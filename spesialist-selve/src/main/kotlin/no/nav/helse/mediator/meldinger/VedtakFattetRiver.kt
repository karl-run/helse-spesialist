package no.nav.helse.mediator.meldinger

import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.modell.vedtaksperiode.vedtak.VedtakFattet
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val mediator: HendelseMediator
) : River.PacketListener {

    private companion object {
        private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtak_fattet")
                it.requireKey("@id", "fødselsnummer", "vedtaksperiodeId")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error("Forstod ikke vedtak_fattet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet["@id"].asText() == "4e368bd3-035d-4545-acaf-ce9a8a845b51" || packet["@id"].asText() == "20559222-145d-4516-8f67-433a0dd531a1") return
        sikkerlogg.info("Mottok melding vedtak_fattet, {}", kv("hendelseId", UUID.fromString(packet["@id"].asText())))

        mediator.mottaMelding(VedtakFattet(packet), context)
    }
}
