package no.nav.helse.modell.oppgave

import no.nav.helse.Oppgavestatus
import no.nav.helse.modell.Behovtype
import no.nav.helse.modell.Spleisbehov
import no.nav.helse.modell.dao.PersonDao
import no.nav.helse.modell.løsning.HentEnhetLøsning
import no.nav.helse.modell.løsning.HentPersoninfoLøsning
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

internal class OpprettPersonCommand(
    private val spleisbehov: Spleisbehov,
    private val personDao: PersonDao,
    private val fødselsnummer: String,
    private val aktørId: String,
    behovId: UUID,
    parent: Command,
    ferdigstilt: LocalDateTime? = null
) : Command(
    behovId = behovId,
    initiellStatus = Oppgavestatus.AvventerSystem,
    parent = parent,
    timeout = Duration.ofHours(1)
) {
    private var navnId: Int? = null
    private var enhetId: Int? = null

    override fun execute() {
        if (personDao.findPersonByFødselsnummer(fødselsnummer.toLong()) != null) {
            ferdigstillSystem()
        } else if (navnId != null && enhetId != null) {
            personDao.insertPerson(fødselsnummer.toLong(), aktørId.toLong(), navnId!!, enhetId!!)
            ferdigstillSystem()
        } else {
            spleisbehov.håndter(Behovtype.HentPersoninfo)
            spleisbehov.håndter(Behovtype.HentEnhet)
        }
    }

    override fun fortsett(hentEnhetLøsning: HentEnhetLøsning) {
        enhetId = hentEnhetLøsning.enhetNr.toInt()
    }

    override fun fortsett(hentPersoninfoLøsning: HentPersoninfoLøsning) {
        navnId = personDao.insertNavn(hentPersoninfoLøsning.fornavn, hentPersoninfoLøsning.mellomnavn, hentPersoninfoLøsning.etternavn)
    }

}
