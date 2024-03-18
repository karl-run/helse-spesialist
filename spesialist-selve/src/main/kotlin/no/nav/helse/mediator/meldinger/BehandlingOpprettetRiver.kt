package no.nav.helse.mediator.meldinger

import no.nav.helse.mediator.MeldingMediator
import no.nav.helse.modell.vedtaksperiode.BehandlingOpprettet
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class BehandlingOpprettetRiver(
    rapidsConnection: RapidsConnection,
    private val mediator: MeldingMediator,
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "behandling_opprettet")
                it.rejectValues("organisasjonsnummer", listOf("ARBEIDSLEDIG", "SELVSTENDIG", "FRILANS"))
                it.requireKey(
                    "@id",
                    "vedtaksperiodeId",
                    "behandlingId",
                    "fødselsnummer",
                    "organisasjonsnummer",
                )
                it.requireKey("fom", "tom")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        mediator.mottaMelding(BehandlingOpprettet(packet), context)
    }
}