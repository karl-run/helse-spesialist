package no.nav.helse.mediator.oppgave

import no.nav.helse.db.OppgaveFraDatabase
import no.nav.helse.db.TildelingDao
import no.nav.helse.db.TotrinnsvurderingFraDatabase
import no.nav.helse.mediator.oppgave.OppgaveMapper.tilDatabaseversjon
import no.nav.helse.mediator.saksbehandler.SaksbehandlerMapper.tilDatabaseversjon
import no.nav.helse.modell.oppgave.Egenskap
import no.nav.helse.modell.oppgave.Oppgave
import no.nav.helse.modell.oppgave.OppgaveVisitor
import no.nav.helse.modell.saksbehandler.Saksbehandler
import no.nav.helse.modell.totrinnsvurdering.Totrinnsvurdering
import java.time.LocalDateTime
import java.util.UUID

class Oppgavelagrer(private val tildelingDao: TildelingDao) : OppgaveVisitor {
    private lateinit var oppgaveForLagring: OppgaveFraDatabase
    private var totrinnsvurderingForLagring: TotrinnsvurderingFraDatabase? = null

    internal fun lagre(
        oppgaveMediator: OppgaveMediator,
        contextId: UUID,
    ) {
        val oppgave = oppgaveForLagring
        oppgaveMediator.opprett(
            id = oppgave.id,
            contextId = contextId,
            vedtaksperiodeId = oppgave.vedtaksperiodeId,
            utbetalingId = oppgave.utbetalingId,
            egenskaper = oppgave.egenskaper,
            hendelseId = oppgave.hendelseId,
            kanAvvises = oppgave.kanAvvises,
        )
        if (oppgave.tildelt != null) {
            tildelingDao.tildel(oppgave.id, oppgave.tildelt.oid)
        } else {
            tildelingDao.avmeld(oppgave.id)
        }

        val totrinnsvurdering = totrinnsvurderingForLagring
        if (totrinnsvurdering != null) oppgaveMediator.lagreTotrinnsvurdering(totrinnsvurdering)
    }

    internal fun oppdater(oppgaveMediator: OppgaveMediator) {
        val oppgave = oppgaveForLagring
        oppgaveMediator.oppdater(
            oppgaveId = oppgave.id,
            status = oppgave.status,
            ferdigstiltAvIdent = oppgave.ferdigstiltAvIdent,
            ferdigstiltAvOid = oppgave.ferdigstiltAvOid,
            egenskaper = oppgave.egenskaper,
        )
        if (oppgave.tildelt != null) {
            tildelingDao.tildel(oppgave.id, oppgave.tildelt.oid)
        } else {
            tildelingDao.avmeld(oppgave.id)
        }

        val totrinnsvurdering = totrinnsvurderingForLagring
        if (totrinnsvurdering != null) oppgaveMediator.lagreTotrinnsvurdering(totrinnsvurdering)
    }

    override fun visitOppgave(
        id: Long,
        tilstand: Oppgave.Tilstand,
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        hendelseId: UUID,
        ferdigstiltAvOid: UUID?,
        ferdigstiltAvIdent: String?,
        egenskaper: List<Egenskap>,
        tildelt: Saksbehandler?,
        kanAvvises: Boolean,
        totrinnsvurdering: Totrinnsvurdering?,
    ) {
        oppgaveForLagring =
            OppgaveFraDatabase(
                id = id,
                egenskaper = egenskaper.map { it.tilDatabaseversjon() },
                status = status(tilstand),
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingId = utbetalingId,
                hendelseId = hendelseId,
                kanAvvises = kanAvvises,
                ferdigstiltAvIdent = ferdigstiltAvIdent,
                ferdigstiltAvOid = ferdigstiltAvOid,
                tildelt = tildelt?.tilDatabaseversjon(),
            )
    }

    override fun visitTotrinnsvurdering(
        vedtaksperiodeId: UUID,
        erRetur: Boolean,
        saksbehandler: Saksbehandler?,
        beslutter: Saksbehandler?,
        utbetalingId: UUID?,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime?,
    ) {
        totrinnsvurderingForLagring =
            TotrinnsvurderingFraDatabase(
                vedtaksperiodeId = vedtaksperiodeId,
                erRetur = erRetur,
                saksbehandler = saksbehandler?.oid(),
                beslutter = beslutter?.oid(),
                utbetalingId = utbetalingId,
                opprettet = opprettet,
                oppdatert = oppdatert,
            )
    }

    private fun status(tilstand: Oppgave.Tilstand): String {
        return when (tilstand) {
            Oppgave.AvventerSaksbehandler -> "AvventerSaksbehandler"
            Oppgave.AvventerSystem -> "AvventerSystem"
            Oppgave.Ferdigstilt -> "Ferdigstilt"
            Oppgave.Invalidert -> "Invalidert"
        }
    }
}
