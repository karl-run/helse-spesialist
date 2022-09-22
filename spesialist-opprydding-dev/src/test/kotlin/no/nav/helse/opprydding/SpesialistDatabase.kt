package no.nav.helse.opprydding

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object SpesialistDatabase {
    fun spesialistDataSource(url: String?) = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = "spesialist"
        password = "test"
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 10000
        connectionTimeout = 500
        maxLifetime = 30000
        initializationFailTimeout = 1000
    })

}
