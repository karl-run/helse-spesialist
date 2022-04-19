package no.nav.helse.mediator.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.cio.CIO
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.net.ServerSocket
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.helse.installErrorHandling
import no.nav.helse.objectMapper
import no.nav.helse.spesialist.api.AzureAdAppConfig
import no.nav.helse.spesialist.api.AzureConfig
import no.nav.helse.spesialist.api.azureAdAppAuthentication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationServer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractApiTest {

    private lateinit var server: TestServerRuntime
    protected lateinit var client: HttpClient

    protected fun setupServer(λ: Route.() -> Unit) {
        server = TestServer(λ = λ).start()
        client = server.restClient()
    }

    @AfterAll
    protected fun tearDown() {
        server.close()
    }

    companion object {
        private val requiredGroup: UUID = UUID.randomUUID()
        internal const val clientId = "client_id"
        private const val epostadresse = "sara.saksbehandler@nav.no"
        internal const val issuer = "https://jwt-provider-domain"
        internal val jwtStub = JwtStub()

        fun HttpRequestBuilder.authentication(oid: UUID, group: String? = null) {
            header(
                "Authorization",
                "Bearer ${
                    jwtStub.getToken(
                        listOfNotNull(requiredGroup.toString(), group),
                        oid.toString(),
                        epostadresse,
                        clientId,
                        issuer
                    )
                }"
            )
        }

        fun HttpRequestBuilder.authentication(
            oid: UUID,
            epost: String = epostadresse,
            navn: String,
            ident: String,
            group: String? = null,
        ) {
            header(
                "Authorization",
                "Bearer ${
                    jwtStub.getToken(
                        groups = listOfNotNull(requiredGroup.toString(), group),
                        oid = oid.toString(),
                        epostadresse = epost,
                        clientId = clientId,
                        issuer = issuer,
                        navn = navn,
                        navIdent = ident
                    )
                }"
            )
        }
    }

    class TestServer(
        private val httpPort: Int = ServerSocket(0).use { it.localPort },
        private val λ: Route.() -> Unit,
    ) {
        fun start(): TestServerRuntime {
            return TestServerRuntime(λ, httpPort)
        }

        fun <T> withAuthenticatedServer(λ: suspend (HttpClient) -> T): T {
            return runBlocking {
                start().use { λ(it.restClient()) }
            }
        }
    }

    class TestServerRuntime(
        build: Route.() -> Unit,
        private val httpPort: Int,
    ) : AutoCloseable {
        private val server = createEmbeddedServer(build, httpPort)

        companion object {
            private fun createEmbeddedServer(build: Route.() -> Unit, httpPort: Int) =
                embeddedServer(CIO, applicationEngineEnvironment {
                    connector { port = httpPort }
                    module { module(build) }
                })
        }

        init {
            server.start(wait = false)
        }

        override fun close() {
            server.stop(0, 0)
        }


        fun restClient(): HttpClient {
            return HttpClient {
                defaultRequest {
                    host = "localhost"
                    port = httpPort
                }
                expectSuccess = false
                install(ContentNegotiation) {
                    register(
                        ContentType.Application.Json,
                        JacksonConverter(objectMapper)
                    )
                }
            }
        }
    }
}

internal fun Application.module(build: Route.() -> Unit) {
    install(WebSockets)
    installErrorHandling()
    install(ContentNegotiationServer) {
        register(
            ContentType.Application.Json,
            JacksonConverter(objectMapper)
        )
    }
    val azureAdAppConfig = AzureAdAppConfig(
        azureConfig = AzureConfig(
            clientId = AbstractApiTest.clientId,
            issuer = AbstractApiTest.issuer,
            jwkProvider = AbstractApiTest.jwtStub.getJwkProviderMock(),
            tokenEndpoint = "",
        ),
    )

    azureAdAppAuthentication(azureAdAppConfig)
    routing { authenticate("oidc", build = build) }
}
