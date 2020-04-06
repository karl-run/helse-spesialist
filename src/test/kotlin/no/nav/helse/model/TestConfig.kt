package no.nav.helse.model

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.headersOf
import no.nav.helse.AccessTokenClient
import org.flywaydb.core.Flyway
import javax.sql.DataSource

internal fun setupDataSource(): DataSource {
    val embeddedPostgres = EmbeddedPostgres.builder().start()

    val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    val dataSource = HikariDataSource(hikariConfig)

    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()

    return dataSource
}

internal fun httpClientForSpleis() = HttpClient(MockEngine) {
    install(JsonFeature) {
        this.serializer = JacksonSerializer()
    }
    engine {
        addHandler {
            respond("{}")
        }
    }
}

internal fun accessTokenClient() = AccessTokenClient(
    "http://localhost.no",
    "",
    "",
    HttpClient(MockEngine) {
        install(JsonFeature) {
            this.serializer = JacksonSerializer { registerModule(JavaTimeModule()) }
        }
        engine {
            addHandler {
                respond(content = """{"access_token": "token", "expires_on": "0"}""", headers = headersOf("Content-Type" to listOf("application/json")))
            }
        }
    }
)
