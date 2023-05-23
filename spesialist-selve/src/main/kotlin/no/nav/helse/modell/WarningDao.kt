package no.nav.helse.modell

import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.modell.vedtak.Warning
import no.nav.helse.modell.vedtak.WarningKilde
import org.intellij.lang.annotations.Language

internal class WarningDao(private val dataSource: DataSource) {
    internal fun leggTilWarnings(vedtaksperiodeId: UUID, warnings: List<Warning>) {
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return
        Warning.lagre(this, warnings, vedtakRef)
    }

    private fun fjernWarnings(vedtakRef: Long, kilde: WarningKilde) {
        sessionOf(dataSource).use  { session ->
            @Language("PostgreSQL")
            val statement = "DELETE FROM warning WHERE vedtak_ref=? AND kilde=CAST(? as warning_kilde)"
            session.run(queryOf(statement, vedtakRef, kilde.name).asExecute)
        }
    }

    internal fun oppdaterSpleisWarnings(vedtaksperiodeId: UUID, warnings: List<Warning>) {
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return
        fjernWarnings(vedtakRef, WarningKilde.Spleis)
        Warning.lagre(this, warnings, vedtakRef)
    }

    internal fun leggTilWarning(vedtaksperiodeId: UUID, warning: Warning) {
        val vedtakRef = finnVedtakId(vedtaksperiodeId) ?: return
        warning.lagre(this, vedtakRef)
    }

    internal fun leggTilWarning(vedtakRef: Long, melding: String, kilde: WarningKilde, opprettet: LocalDateTime) =
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val statement =
                "INSERT INTO warning (melding, kilde, vedtak_ref, opprettet) VALUES (?, CAST(? as warning_kilde), ?, ?)"
            session.run(queryOf(statement, melding, kilde.name, vedtakRef, opprettet).asUpdate)
        }

    private fun finnVedtakId(vedtaksperiodeId: UUID) = sessionOf(dataSource).use  { session ->
        @Language("PostgreSQL")
        val statement = "SELECT id FROM vedtak WHERE vedtaksperiode_id = ?"
        session.run(queryOf(statement, vedtaksperiodeId).map { it.long("id") }.asSingle)
    }
}
