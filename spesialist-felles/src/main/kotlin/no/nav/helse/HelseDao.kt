package no.nav.helse

import javax.sql.DataSource
import kotliquery.Query
import kotliquery.Row
import kotliquery.queryOf
import org.intellij.lang.annotations.Language

abstract class HelseDao(dataSource: DataSource) {
    private val dbQueries = DbQueries(dataSource)

    fun asSQL(@Language("SQL") sql: String, argMap: Map<String, Any?> = emptyMap()) = queryOf(sql, argMap)
    fun asSQL(@Language("SQL") sql: String, vararg params: Any?) = queryOf(sql, *params)

    fun <T> Query.single(mapping: (Row) -> T?) = dbQueries.run { single(mapping) }

    fun <T> Query.list(mapping: (Row) -> T?) = dbQueries.run { list(mapping) }

    fun Query.update() = dbQueries.run { update() }
    fun Query.updateAndReturnGeneratedKey() = dbQueries.run { updateAndReturnGeneratedKey() }
}
