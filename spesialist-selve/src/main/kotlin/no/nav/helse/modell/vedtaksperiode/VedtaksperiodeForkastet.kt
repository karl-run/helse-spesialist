package no.nav.helse.modell.vedtaksperiode

import java.util.UUID
import no.nav.helse.mediator.Kommandofabrikk
import no.nav.helse.mediator.meldinger.Vedtaksperiodemelding
import no.nav.helse.mediator.oppgave.OppgaveMediator
import no.nav.helse.modell.CommandContextDao
import no.nav.helse.modell.SnapshotDao
import no.nav.helse.modell.kommando.AvbrytCommand
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.OppdaterSnapshotCommand
import no.nav.helse.modell.person.Person
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spesialist.api.snapshot.SnapshotClient

internal class VedtaksperiodeForkastet private constructor(
    override val id: UUID,
    private val vedtaksperiodeId: UUID,
    private val fødselsnummer: String,
    private val json: String
) : Vedtaksperiodemelding {
    internal constructor(packet: JsonMessage): this(
        UUID.fromString(packet["@id"].asText()),
        UUID.fromString(packet["vedtaksperiodeId"].asText()),
        packet["fødselsnummer"].asText(),
        json = packet.toJson()
    )

    override fun fødselsnummer() = fødselsnummer
    override fun vedtaksperiodeId() = vedtaksperiodeId
    override fun toJson() = json

    override fun behandle(person: Person, kommandofabrikk: Kommandofabrikk) {
        person.vedtaksperiodeForkastet(vedtaksperiodeId)
        kommandofabrikk.iverksettVedtaksperiodeForkastet(this)
    }
}

internal class VedtaksperiodeForkastetCommand(
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val id: UUID,
    personDao: PersonDao,
    commandContextDao: CommandContextDao,
    snapshotDao: SnapshotDao,
    snapshotClient: SnapshotClient,
    oppgaveMediator: OppgaveMediator
): MacroCommand() {
    override val commands: List<Command> = listOf(
        AvbrytCommand(vedtaksperiodeId, commandContextDao, oppgaveMediator),
        OppdaterSnapshotCommand(
            snapshotClient = snapshotClient,
            snapshotDao = snapshotDao,
            vedtaksperiodeId = vedtaksperiodeId,
            fødselsnummer = fødselsnummer,
            personDao = personDao,
        ),
    )
}
