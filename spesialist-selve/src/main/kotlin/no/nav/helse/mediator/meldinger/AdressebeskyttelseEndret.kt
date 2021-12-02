package no.nav.helse.mediator.meldinger

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.OppdaterPersoninfoCommand
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory
import java.util.*

internal class AdressebeskyttelseEndret(
    override val id: UUID,
    private val fødselsnummer: String,
    private val json: String,
    personDao: PersonDao
) : Hendelse, MacroCommand() {
    override val commands: List<Command> = listOf(OppdaterPersoninfoCommand(fødselsnummer, personDao, force = true))

    override fun fødselsnummer(): String = fødselsnummer

    override fun toJson(): String = json

    internal class AdressebeskyttelseEndretRiver(
        rapidsConnection: RapidsConnection,
        private val mediator: HendelseMediator
    ) : River.PacketListener {
        private val logg = LoggerFactory.getLogger("adressebeskyttelse_endret_river")
        init {
            River(rapidsConnection).apply {
                validate {
                    it.demandValue("@event_name", "adressebeskyttelse_endret")
                    it.requireKey("@id", "fødselsnummer")
                }
            }.register(this)
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext) {
            val hendelseId = UUID.fromString(packet["@id"].asText())
            // Unngå å logge mer informasjon enn id her, vi ønsker ikke å lekke informasjon om adressebeskyttelse
            logg.info(
                "Mottok adressebeskyttelse_endret med {}",
                StructuredArguments.keyValue("hendelseId", hendelseId)
            )
            mediator.adressebeskyttelseEndret(
                packet,
                hendelseId,
                packet["fødselsnummer"].asText(),
                context
            )
        }
    }
}
