package no.nav.helse

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.callIdMdc
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.request.path
import io.ktor.request.uri
import kotlinx.coroutines.runBlocking
import no.nav.helse.api.OppgaveMediator
import no.nav.helse.api.adminApi
import no.nav.helse.api.oppgaveApi
import no.nav.helse.api.vedtaksperiodeApi
import no.nav.helse.mediator.kafka.SpleisbehovMediator
import no.nav.helse.mediator.kafka.meldinger.*
import no.nav.helse.modell.arbeidsgiver.ArbeidsgiverDao
import no.nav.helse.modell.command.SpleisbehovDao
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.risiko.RisikoDao
import no.nav.helse.modell.vedtak.snapshot.SnapshotDao
import no.nav.helse.modell.vedtak.snapshot.SpeilSnapshotRestDao
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.vedtaksperiode.VedtaksperiodeDao
import no.nav.helse.vedtaksperiode.VedtaksperiodeMediator
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.ProxySelector
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

const val azureMountPath: String = "/var/run/secrets/nais.io/azure"
private val auditLog = LoggerFactory.getLogger("auditLogger")

internal class ApplicationBuilder(env: Map<String, String>) : RapidsConnection.StatusListener {
    private val dataSourceBuilder = DataSourceBuilder(System.getenv())
    private val dataSource = dataSourceBuilder.getDataSource(DataSourceBuilder.Role.User)

    private val personDao = PersonDao(dataSource)
    private val arbeidsgiverDao = ArbeidsgiverDao(dataSource)
    private val spleisbehovDao = SpleisbehovDao(dataSource)
    private val snapshotDao = SnapshotDao(dataSource)
    private val vedtaksperiodeDao = VedtaksperiodeDao(dataSource)
    private val risikoDao = RisikoDao(dataSource)

    private val azureAdClient = HttpClient(Apache) {
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerModule(JavaTimeModule())
            }
        }
    }
    private val spleisClient = HttpClient {
        install(JsonFeature) { serializer = JacksonSerializer() }
    }
    private val oidcDiscovery =
        runBlocking { AzureAadClient(azureAdClient).oidcDiscovery(System.getenv("AZURE_CONFIG_URL")) }
    private val accessTokenClient = AccessTokenClient(
        aadAccessTokenUrl = oidcDiscovery.token_endpoint,
        clientId = readClientId(),
        clientSecret = readClientSecret(),
        httpClient = azureAdClient
    )
    private val speilSnapshotRestDao = SpeilSnapshotRestDao(
        httpClient = spleisClient,
        accessTokenClient = accessTokenClient,
        spleisClientId = env.getValue("SPLEIS_CLIENT_ID")
    )

    private val azureConfig = AzureAdAppConfig(
        clientId = readClientId(),
        requiredGroup = env.getValue("AZURE_REQUIRED_GROUP")
    )
    private val httpTraceLog = LoggerFactory.getLogger("tjenestekall")
    private val spleisbehovMediator = SpleisbehovMediator(
        dataSource = dataSource,
        spleisbehovDao = spleisbehovDao,
        personDao = personDao,
        arbeidsgiverDao = arbeidsgiverDao,
        snapshotDao = snapshotDao,
        speilSnapshotRestDao = speilSnapshotRestDao,
        risikoDao = risikoDao,
        spesialistOID = UUID.fromString(env.getValue("SPESIALIST_OID"))
    )
    private val oppgaveMediator = OppgaveMediator(dataSource)
    private val vedtaksperiodeMediator = VedtaksperiodeMediator(
        vedtaksperiodeDao = vedtaksperiodeDao,
        arbeidsgiverDao = arbeidsgiverDao,
        snapshotDao = snapshotDao,
        personDao = personDao,
        dataSource = dataSource,
        risikoDao = risikoDao
    )
    private val rapidsConnection =
        RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env)).withKtorModule {
            install(CallId) {
                generate {
                    UUID.randomUUID().toString()
                }
            }
            install(CallLogging) {
                logger = httpTraceLog
                level = Level.INFO
                callIdMdc("callId")
                filter { call -> call.request.path().startsWith("/api/") }
            }
            intercept(ApplicationCallPipeline.Call) {
                call.principal<JWTPrincipal>()?.let { principal ->
                    auditLog.info(
                        "Bruker=\"${principal.payload.getClaim("NAVident")
                            .asString()}\" gjør kall mot url=\"${call.request.uri}\""
                    )
                }
            }
            install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
            requestResponseTracing(httpTraceLog)
            azureAdAppAuthentication(
                oidcDiscovery = oidcDiscovery,
                config = azureConfig
            )
            basicAuthentication(env.getValue("ADMIN_SECRET"))
            oppgaveApi(oppgaveMediator)
            vedtaksperiodeApi(
                spleisbehovMediator = spleisbehovMediator,
                vedtaksperiodeMediator = vedtaksperiodeMediator,
                dataSource = dataSource
            )
            adminApi(spleisbehovMediator)
        }.build()

    init {
        spleisbehovMediator.init(rapidsConnection)
        rapidsConnection.register(this)

        ArbeidsgiverMessage.Factory(rapidsConnection, spleisbehovMediator)
        GodkjenningMessage.Factory(rapidsConnection, spleisbehovMediator)
        PersoninfoLøsningMessage.Factory(rapidsConnection, spleisbehovMediator)
        PåminnelseMessage.Factory(rapidsConnection, spleisbehovMediator)
        TilInfotrygdMessage.Factory(rapidsConnection, spleisbehovMediator)
        VedtaksperiodeEndretMessage.Factory(rapidsConnection, spleisbehovMediator)
        VedtaksperiodeForkastetMessage.Factory(rapidsConnection, spleisbehovMediator)
        TilbakerullingMessage.Factory(rapidsConnection, spleisbehovMediator)
        RisikovurderingMessage.Factory(rapidsConnection, spleisbehovMediator)
    }

    fun start() = rapidsConnection.start()
    fun stop() = rapidsConnection.stop()

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        spleisbehovMediator.shutdown()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        dataSourceBuilder.migrate()
    }

    private fun readClientId(): String {
        return Files.readString(Paths.get(azureMountPath, "client_id"))
    }

    private fun readClientSecret(): String {
        return Files.readString(Paths.get(azureMountPath, "client_secret"))
    }
}
