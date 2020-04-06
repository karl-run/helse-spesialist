package no.nav.helse.modell

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.modell.dao.*
import no.nav.helse.modell.løsning.ArbeidsgiverLøsning
import no.nav.helse.modell.løsning.HentEnhetLøsning
import no.nav.helse.modell.løsning.HentPersoninfoLøsning
import no.nav.helse.modell.oppgave.*
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KClass

internal class SpleisBehov(
    internal val id: UUID,
    internal val fødselsnummer: String,
    internal val periodeFom: LocalDate,
    internal val periodeTom: LocalDate,
    internal val vedtaksperiodeId: UUID,
    internal val aktørId: String,
    internal val orgnummer: String,
    nåværendeOppgavenavn: String = OpprettPersonCommand::class.simpleName!!,
    personDao: PersonDao,
    arbeidsgiverDao: ArbeidsgiverDao,
    vedtakDao: VedtakDao,
    snapshotDao: SnapshotDao,
    speilSnapshotRestDao: SpeilSnapshotRestDao,
    oppgaveDao: OppgaveDao
) {
    private val log = LoggerFactory.getLogger(SpleisBehov::class.java)
    private var nåværendeOppgave: KClass<out Command>
    private val oppgaver: List<Command> = listOf(
        OpprettPersonCommand(this, personDao),
        OppdaterPersonCommand(this, personDao),
        OpprettArbeidsgiverCommand(this, arbeidsgiverDao),
        OppdatertArbeidsgiverCommand(this, arbeidsgiverDao),
        OpprettVedtakCommand(this, personDao, arbeidsgiverDao, vedtakDao, snapshotDao, speilSnapshotRestDao),
        OpprettOppgaveCommand(this, oppgaveDao)
    )

    init {
        nåværendeOppgave = oppgaver.first { it::class.simpleName == nåværendeOppgavenavn }::class
    }

    private val behovstyper: MutableList<Behovtype> = mutableListOf()

    internal fun execute() {
        behovstyper.clear()
        oppgaver.asSequence().dropWhile { it::class != nåværendeOppgave }
            .onEach {
                nåværendeOppgave = it::class
                it.execute()
            }
            .takeWhile { behov() == null }
            .forEach {
                log.info("Oppgave ${it::class.simpleName} utført. Nåværende oppgave er ${nåværendeOppgave::class.simpleName}")
            }
    }

    internal fun håndter(behovtype: Behovtype) {
        behovstyper.add(behovtype)
    }

    internal fun fortsett(løsning: HentEnhetLøsning) {
        current().fortsett(løsning)
    }

    internal fun fortsett(løsning: HentPersoninfoLøsning) {
        current().fortsett(løsning)
    }

    fun fortsett(løsning: ArbeidsgiverLøsning) {
        current().fortsett(løsning)
    }

    private fun current() = oppgaver.first { it::class == nåværendeOppgave }


    fun behov() = behovstyper.takeIf { it.isNotEmpty() }?.let { typer ->
        Behov(
            typer = typer,
            fødselsnummer = fødselsnummer,
            orgnummer = orgnummer,
            spleisBehovId = id
        )
    }

    fun toJson() =
        jacksonObjectMapper().writeValueAsString(
            SpleisBehovDTO(
                id,
                fødselsnummer,
                periodeFom,
                periodeTom,
                vedtaksperiodeId,
                aktørId,
                orgnummer,
                nåværendeOppgave.simpleName!!
            )
        )
}

data class SpleisBehovDTO(
    val id: UUID,
    val fødselsnummer: String,
    val periodeFom: LocalDate,
    val periodeTom: LocalDate,
    val vedtaksperiodeId: UUID,
    val aktørId: String,
    val orgnummer: String,
    val oppgavenavn: String
)
