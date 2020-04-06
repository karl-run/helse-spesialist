package no.nav.helse

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import no.nav.helse.mediator.kafka.SpleisbehovMediator
import no.nav.helse.mediator.kafka.meldinger.GodkjenningMessage
import no.nav.helse.mediator.kafka.meldinger.PersoninfoMessage
import no.nav.helse.modell.dao.*
import no.nav.helse.rapids_rivers.RapidApplication
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector
import java.nio.file.Files
import java.nio.file.Paths

@KtorExperimentalAPI
fun main(): Unit = runBlocking {
    val dataSourceBuilder = DataSourceBuilder(System.getenv())
    dataSourceBuilder.migrate()
    val dataSource = dataSourceBuilder.getDataSource(DataSourceBuilder.Role.User)
    val personDao = PersonDao(dataSource)
    val arbeidsgiverDao = ArbeidsgiverDao(dataSource)
    val vedtakDao = VedtakDao(dataSource)
    val spleisbehovDao = SpleisbehovDao(dataSource)
    val azureAdClient = HttpClient(Apache) {
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
        install(JsonFeature) { serializer = JacksonSerializer() }
    }
    val spleisClient = HttpClient {
        install(JsonFeature) { serializer = JacksonSerializer() }
    }
    val oidcDiscovery = AzureAad(azureAdClient).oidcDiscovery(System.getenv("AZURE_CONFIG_URL"))
    val accessTokenClient = AccessTokenClient(
        aadAccessTokenUrl = oidcDiscovery.token_endpoint,
        clientId = readClientId(),
        clientSecret = readClientSecret(),
        httpClient = azureAdClient
    )
    val snapshotDao = SnapshotDao(dataSource)
    val speilSnapshotRestDao = SpeilSnapshotRestDao(
        httpClient = spleisClient,
        accessTokenClient = accessTokenClient,
        spleisClientId = System.getenv("SPLEIS_CLIENT_ID")
    )
    val oppgaveDao = OppgaveDao(dataSource)

    RapidApplication.create(System.getenv()).apply {
        val spleisbehovMediator = SpleisbehovMediator(
            spleisbehovDao,
            personDao,
            arbeidsgiverDao,
            vedtakDao,
            snapshotDao,
            speilSnapshotRestDao,
            oppgaveDao
        )

        GodkjenningMessage.Factory(
            rapidsConnection = this,
            spleisbehovMediator = spleisbehovMediator
        )
        PersoninfoMessage.Factory(this, spleisbehovMediator)
    }.start()
}


val azureMountPath: String = "/var/run/secrets/nais.io/azure"

private fun readClientId(): String {
    return Files.readString(Paths.get(azureMountPath, "client_id"))
}

private fun readClientSecret(): String {
    return Files.readString(Paths.get(azureMountPath, "client_secret"))
}
