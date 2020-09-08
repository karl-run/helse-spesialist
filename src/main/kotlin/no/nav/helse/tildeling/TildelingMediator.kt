package no.nav.helse.tildeling

import kotliquery.sessionOf
import no.nav.helse.modell.feilhåndtering.ModellFeil
import no.nav.helse.modell.feilhåndtering.OppgaveErAlleredeTildelt
import java.util.*
import javax.sql.DataSource

class TildelingMediator(private val dataSource: DataSource) {
    fun hentSaksbehandlerFor(oppgavereferanse: UUID): String? = sessionOf(dataSource).use { session ->
        session.hentSaksbehandlerFor(oppgavereferanse)
    }

    fun tildelOppgaveTilSaksbehandler(oppgavereferanse: UUID, saksbehandlerreferanse: UUID) {
        sessionOf(dataSource).use { session ->
            val hentSaksbehandlerFor = session.hentSaksbehandlerFor(oppgavereferanse)
            if (hentSaksbehandlerFor != null) {
                throw ModellFeil(OppgaveErAlleredeTildelt)
            }
            session.tildelOppgave(oppgavereferanse, saksbehandlerreferanse)
        }
    }

    fun fjernTildeling(oppgavereferanse: UUID) {
        sessionOf(dataSource).use { session ->
            session.slettOppgavetildeling(oppgavereferanse)
        }
    }
}
