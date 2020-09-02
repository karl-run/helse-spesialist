package no.nav.helse.tildeling

import kotliquery.Session
import kotliquery.queryOf
import org.intellij.lang.annotations.Language
import java.util.*

fun Session.tildelOppgave(oppgaveReferanse: UUID, saksbehandlerOid: UUID) {
    @Language("PostgreSQL")
    val query = "INSERT INTO tildeling(oppgave_ref, saksbehandler_ref) VALUES(:oppgave_ref, :saksbehandler_ref);"
    run(queryOf(query, mapOf(
        "oppgave_ref" to oppgaveReferanse,
        "saksbehandler_ref" to saksbehandlerOid
    )).asUpdate)
}

fun Session.hentSaksbehandlerFor(oppgaveReferanse: UUID): UUID? {
    @Language("PostgreSQL")
    val query = "SELECT * FROM tildeling WHERE oppgave_ref=:oppgave_ref;"
    return run(queryOf(query, mapOf("oppgave_ref" to oppgaveReferanse)).map { row ->
        UUID.fromString(row.string("saksbehandler_ref"))
    }.asSingle)
}
