package no.nav.helse.modell.leggpåvent

import no.nav.helse.spesialist.api.feilhåndtering.OppgaveIkkeTildelt
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.spesialist.api.tildeling.TildelingDao

internal class LeggPåVentService(
    private val tildelingDao: TildelingDao,
    private val hendelseMediator: HendelseMediator
) {

    internal fun leggOppgavePåVent(oppgaveId: Long) {
        tildelingDao.tildelingForOppgave(oppgaveId) ?: throw OppgaveIkkeTildelt(oppgaveId)
        tildelingDao.leggOppgavePåVent(oppgaveId)
        hendelseMediator.sendMeldingOppgaveOppdatert(oppgaveId, påVent = true)
    }

    internal fun fjernPåVent(oppgaveId: Long) {
        tildelingDao.fjernPåVent(oppgaveId)
        hendelseMediator.sendMeldingOppgaveOppdatert(oppgaveId, påVent = false)
    }
}
