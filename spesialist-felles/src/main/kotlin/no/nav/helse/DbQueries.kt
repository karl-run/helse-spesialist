package no.nav.helse

import io.ktor.utils.io.core.use
import javax.sql.DataSource
import kotliquery.Query
import kotliquery.Row
import kotliquery.action.QueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class DbQueries(private val dataSource: DataSource) {
    fun <T> Query.single(mapper: (Row) -> T?) = map(mapper).asSingle.runInSession()
    fun <T> Query.list(mapper: (Row) -> T?) = map(mapper).asList.runInSession()

    fun Query.update() = asUpdate.runInSession()
    fun Query.updateAndReturnGeneratedKey() = asUpdateAndReturnGeneratedKey.runInSession(returnGeneratedKey = true)

    private fun <T> QueryAction<T>.runInSession(returnGeneratedKey: Boolean = false) =
        sessionOf(dataSource, strict = true, returnGeneratedKey = returnGeneratedKey).use(::runWithSession)
}

fun query(@Language("postgresql") query: String, vararg params: Pair<String, Any>) = queryOf(query, params.toMap())
