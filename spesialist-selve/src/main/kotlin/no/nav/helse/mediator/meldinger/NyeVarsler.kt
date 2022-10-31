package no.nav.helse.mediator.meldinger

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.mediator.meldinger.NyeVarsler.Varsel.Companion.lagre
import no.nav.helse.mediator.meldinger.NyeVarsler.Varsel.Companion.varsler
import no.nav.helse.modell.kommando.CommandContext
import no.nav.helse.modell.varsel.VarselDao
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class NyeVarsler(
    override val id: UUID,
    private val fødselsnummer: String,
    private val varsler: List<Varsel>,
    private val json: String,
    private val varselDao: VarselDao
) : Hendelse {

    private companion object {
        private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }

    override fun fødselsnummer(): String = fødselsnummer
    override fun toJson(): String = json
    override fun execute(context: CommandContext): Boolean {
        varsler.lagre(varselDao)
        sikkerlogg.info("Lagrer ${varsler.size} varsler for {}", keyValue("fødselsnummer", fødselsnummer))
        return true
    }

    internal class Varsel(
        private val id: UUID,
        private val kode: String,
        private val tidsstempel: LocalDateTime,
        private val vedtaksperiodeId: UUID,
    ) {

        internal companion object {
            internal fun List<Varsel>.lagre(varselDao: VarselDao) {
                varselDao.transaction { tx ->
                    forEach { varselDao.lagre(it.id, it.kode, it.tidsstempel, it.vedtaksperiodeId, tx) }
                }
            }

            internal fun JsonNode.varsler(): List<Varsel> {
                return this
                    .filter { it["nivå"].asText() == "VARSEL" && it["varselkode"]?.asText() != null }
                    .filter { it["kontekster"].any { kontekst -> kontekst["konteksttype"].asText() == "Vedtaksperiode" } }
                    .map { jsonNode ->
                        val vedtaksperiodeId =
                            UUID.fromString(
                                jsonNode["kontekster"]
                                    .find { it["konteksttype"].asText() == "Vedtaksperiode" }!!["kontekstmap"]
                                    .get("vedtaksperiodeId").asText()
                            )
                        Varsel(
                            UUID.fromString(jsonNode["id"].asText()),
                            jsonNode["varselkode"].asText(),
                            jsonNode["tidsstempel"].asLocalDateTime(),
                            vedtaksperiodeId
                        )
                    }
            }
        }
    }

    internal class NyeVarslerRiver(
        rapidsConnection: RapidsConnection,
        private val mediator: HendelseMediator
    ) : River.PacketListener {

        init {
            River(rapidsConnection).apply {
                validate {
                    it.demandValue("@event_name", "aktivitetslogg_ny_aktivitet")
                    it.requireKey("@id", "fødselsnummer")
                    it.require("@opprettet") { message -> message.asLocalDateTime() }
                    it.requireArray("aktiviteter") {
                        requireKey("melding", "nivå", "id")
                        interestedIn("varselkode")
                        require("tidsstempel", JsonNode::asLocalDateTime)
                        requireArray("kontekster") {
                            requireKey("konteksttype", "kontekstmap")
                        }
                    }
                }
            }.register(this)
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext) {
            val hendelseId = UUID.fromString(packet["@id"].asText())
            val fødselsnummer = packet["fødselsnummer"].asText()
            val varsler = packet["aktiviteter"].varsler()

            if (varsler.isEmpty()) return

            sikkerlogg.info(
                "Mottok varsler for {} med {}, {}",
                keyValue("fødselsnummer", fødselsnummer),
                keyValue("hendelseId", hendelseId),
                keyValue("hendelse", packet.toJson())
            )

            mediator.nyeVarsler(hendelseId, fødselsnummer, varsler, packet.toJson(), context)
        }
    }
}