package no.nav.helse.modell.oppgave

import no.nav.helse.Oppgavestatus
import no.nav.helse.modell.Spleisbehov
import no.nav.helse.modell.dao.OppgaveDao
import no.nav.helse.modell.løsning.ArbeidsgiverLøsning
import no.nav.helse.modell.løsning.HentEnhetLøsning
import no.nav.helse.modell.løsning.HentPersoninfoLøsning
import no.nav.helse.modell.løsning.SaksbehandlerLøsning
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

abstract class Command(
    protected val behovId: UUID,
    initiellStatus: Oppgavestatus,
    private val parent: Command?,
    internal val timeout: Duration
) {
    private var status = initiellStatus
    private var ferdigstiltAv: String? = null
    protected val log: Logger = LoggerFactory.getLogger("command")
    internal open val oppgaver: Set<Command> = setOf()
    internal val oppgavetype: String = requireNotNull(this::class.simpleName)
    internal abstract fun execute()
    internal open fun fortsett(hentEnhetLøsning: HentEnhetLøsning) {
        oppgaver.forEach { it.fortsett(hentEnhetLøsning) }
    }

    internal open fun fortsett(hentPersoninfoLøsning: HentPersoninfoLøsning) {
        oppgaver.forEach { it.fortsett(hentPersoninfoLøsning) }
    }

    internal open fun fortsett(løsning: ArbeidsgiverLøsning) {
        oppgaver.forEach { it.fortsett(løsning) }
    }

    internal open fun fortsett(løsning: SaksbehandlerLøsning) {
        oppgaver.forEach { it.fortsett(løsning) }
    }

    protected fun ferdigstill(ident: String) {
        ferdigstiltAv = ident
        status = Oppgavestatus.Ferdigstilt
    }

    internal fun ferdigstillSystem() = ferdigstill("System")

    internal fun trengerExecute() = this.ferdigstiltAv == null

    private fun findRootCommand(): Command {
        var current = this
        while (current.parent != null) {
            current = current.parent!!
        }
        return current
    }

    protected fun oppdaterVedtakRef(vedtakRef: Int) {
        (findRootCommand() as Spleisbehov).vedtaksperiodeReferanse = vedtakRef
    }

    fun persisterEndring(oppgaveDao: OppgaveDao) {
        oppgaveDao.updateOppgave(behovId, oppgavetype, status, ferdigstiltAv)
    }

    internal fun persister(oppgaveDao: OppgaveDao, vedtakRef: Int?) {
        val oppgave = oppgaveDao.findOppgave(behovId, oppgavetype)
        if (oppgave != null) {
            log.warn("Prøvde å persistere en oppgave som allerede ligger i databasen")
            return
        }
        oppgaveDao.insertOppgave(behovId, oppgavetype, status, vedtakRef)
    }
}

internal fun List<Command>.execute() = this
    .filter { it.trengerExecute() }
    .forEach { it.execute() }
