package no.nav.helse.modell.saksbehandler.handlinger

import java.util.UUID
import no.nav.helse.mediator.OverstyringMediator
import no.nav.helse.mediator.meldinger.Kommandohendelse
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.PubliserOverstyringCommand
import no.nav.helse.modell.overstyring.OverstyringDao

internal class OverstyringArbeidsforhold(
    override val id: UUID,
    private val fødselsnummer: String,
    private val json: String,
    overstyringDao: OverstyringDao,
    overstyringMediator: OverstyringMediator,
) : Kommandohendelse, MacroCommand() {
    override val commands: List<Command> = listOf(
        PubliserOverstyringCommand(
            eventName = "overstyr_arbeidsforhold",
            hendelseId = id,
            json = json,
            overstyringMediator = overstyringMediator,
            overstyringDao = overstyringDao,
        )
    )
    override fun fødselsnummer(): String = fødselsnummer
    override fun toJson(): String = json

}
