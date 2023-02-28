package no.nav.helse.mediator.api

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import java.net.ServerSocket
import java.util.*
import kotlinx.coroutines.runBlocking
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
        private const val clientId = "client_id"
        private const val epostadresse = "sara.saksbehandler@nav.no"
        private const val issuer = "https://jwt-provider-domain"
        private val jwtStub = JwtStub()
        private val azureConfig = AzureConfig(
            clientId = clientId,
            issuer = issuer,
            jwkProvider = jwtStub.getJwkProviderMock(),
            tokenEndpoint = "",
        )
        internal val azureAdAppConfig = AzureAdAppConfig(
            azureConfig = azureConfig,
        )

        fun HttpRequestBuilder.authentication(oid: UUID, group: String? = null) {
            header(
                "Authorization",
                "Bearer ${
                    jwtStub.getToken(
                        groups = listOfNotNull(requiredGroup.toString(), group),
                        oid = oid.toString(),
                        epostadresse = epostadresse,
                        clientId = clientId,
                        issuer = issuer
                    )
                }"
            )
        }

        fun HttpRequestBuilder.authentication(oid: UUID, epost: String = epostadresse, navn:String, ident:String, group: String? = null) {
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
        private val httpPort: Int
    ) : AutoCloseable {
        private val server = createEmbeddedServer(build, httpPort)

        companion object {
            private fun createEmbeddedServer(build: Route.() -> Unit, httpPort: Int) =
                embeddedServer(CIO, port = httpPort) {
                    install(ContentNegotiationServer) {
                        register(
                            ContentType.Application.Json,
                            JacksonConverter(objectMapper)
                        )
                    }
                    val azureAdAppConfig = AzureAdAppConfig(
                        azureConfig = AzureConfig(
                            clientId = clientId,
                            issuer = issuer,
                            jwkProvider = jwtStub.getJwkProviderMock(),
                            tokenEndpoint = "",
                        ),
                    )

                    azureAdAppAuthentication(azureAdAppConfig)
                    routing {
                        authenticate("oidc", build = build)
                    }
                }
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
