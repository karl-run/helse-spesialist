package no.nav.helse.modell.vedtaksperiode

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.mediator.Kommandofabrikk
import no.nav.helse.mediator.meldinger.Vedtaksperiodemelding
import no.nav.helse.mediator.oppgave.OppgaveMediator
import no.nav.helse.modell.CommandContextDao
import no.nav.helse.modell.kommando.AvbrytCommand
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.VedtaksperiodeReberegnetPeriodehistorikk
import no.nav.helse.modell.person.Person
import no.nav.helse.modell.utbetaling.UtbetalingDao
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkDao
import java.util.UUID

internal class VedtaksperiodeReberegnet private constructor(
    override val id: UUID,
    private val fødselsnummer: String,
    private val vedtaksperiodeId: UUID,
    private val json: String,
) : Vedtaksperiodemelding {
    internal constructor(packet: JsonMessage) : this(
        id = UUID.fromString(packet["@id"].asText()),
        fødselsnummer = packet["fødselsnummer"].asText(),
        vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
        json = packet.toJson(),
    )
    internal constructor(jsonNode: JsonNode) : this(
        id = UUID.fromString(jsonNode["@id"].asText()),
        fødselsnummer = jsonNode["fødselsnummer"].asText(),
        vedtaksperiodeId = UUID.fromString(jsonNode["vedtaksperiodeId"].asText()),
        json = jsonNode.toString(),
    )

    override fun fødselsnummer() = fødselsnummer

    override fun toJson(): String = json

    override fun vedtaksperiodeId(): UUID = vedtaksperiodeId

    override fun behandle(
        person: Person,
        kommandofabrikk: Kommandofabrikk,
    ) {
        kommandofabrikk.iverksettVedtaksperiodeReberegnet(this)
    }
}

internal class VedtaksperiodeReberegnetCommand(
    vedtaksperiodeId: UUID,
    utbetalingDao: UtbetalingDao,
    periodehistorikkDao: PeriodehistorikkDao,
    commandContextDao: CommandContextDao,
    oppgaveMediator: OppgaveMediator,
) : MacroCommand() {
    override val commands: List<Command> =
        listOf(
            VedtaksperiodeReberegnetPeriodehistorikk(
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingDao = utbetalingDao,
                periodehistorikkDao = periodehistorikkDao,
            ),
            AvbrytCommand(
                vedtaksperiodeId = vedtaksperiodeId,
                commandContextDao = commandContextDao,
                oppgaveMediator = oppgaveMediator,
            ),
        )
}
