package no.nav.helse.modell.vedtaksperiode

import com.fasterxml.jackson.databind.JsonNode
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
import no.nav.helse.spesialist.api.snapshot.ISnapshotClient
import java.util.UUID

internal class VedtaksperiodeForkastet private constructor(
    override val id: UUID,
    private val vedtaksperiodeId: UUID,
    private val fødselsnummer: String,
    private val json: String,
) : Vedtaksperiodemelding {
    internal constructor(packet: JsonMessage) : this(
        UUID.fromString(packet["@id"].asText()),
        UUID.fromString(packet["vedtaksperiodeId"].asText()),
        packet["fødselsnummer"].asText(),
        json = packet.toJson(),
    )
    internal constructor(jsonNode: JsonNode) : this(
        UUID.fromString(jsonNode["@id"].asText()),
        UUID.fromString(jsonNode["vedtaksperiodeId"].asText()),
        jsonNode["fødselsnummer"].asText(),
        json = jsonNode.toString(),
    )

    override fun fødselsnummer() = fødselsnummer

    override fun vedtaksperiodeId() = vedtaksperiodeId

    override fun behandle(
        person: Person,
        kommandofabrikk: Kommandofabrikk,
    ) {
        person.vedtaksperiodeForkastet(this)
        kommandofabrikk.iverksettVedtaksperiodeForkastet(this)
    }

    override fun toJson() = json
}

internal class VedtaksperiodeForkastetCommand(
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val id: UUID,
    personDao: PersonDao,
    commandContextDao: CommandContextDao,
    snapshotDao: SnapshotDao,
    snapshotClient: ISnapshotClient,
    oppgaveMediator: OppgaveMediator,
) : MacroCommand() {
    override val commands: List<Command> =
        listOf(
            AvbrytCommand(vedtaksperiodeId, commandContextDao, oppgaveMediator),
            OppdaterSnapshotCommand(
                snapshotClient = snapshotClient,
                snapshotDao = snapshotDao,
                fødselsnummer = fødselsnummer,
                personDao = personDao,
            ),
        )
}
