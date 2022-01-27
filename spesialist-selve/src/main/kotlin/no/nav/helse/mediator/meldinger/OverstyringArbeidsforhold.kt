package no.nav.helse.mediator.meldinger

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.modell.kommando.*
import no.nav.helse.modell.kommando.Command
import no.nav.helse.modell.kommando.InvaliderSaksbehandlerOppgaveCommand
import no.nav.helse.modell.kommando.MacroCommand
import no.nav.helse.modell.kommando.OpprettSaksbehandlerCommand
import no.nav.helse.modell.kommando.PersisterOverstyringInntektCommand
import no.nav.helse.modell.kommando.ReserverPersonCommand
import no.nav.helse.modell.overstyring.OverstyringDao
import no.nav.helse.rapids_rivers.*
import no.nav.helse.reservasjon.ReservasjonDao
import no.nav.helse.saksbehandler.SaksbehandlerDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

internal class OverstyringArbeidsforhold(
    override val id: UUID,
    private val fødselsnummer: String,
    oid: UUID,
    navn: String,
    epost: String,
    ident: String,
    orgnummer: String,
    erAktivt: Boolean,
    begrunnelse: String,
    forklaring: String,
    skjæringstidspunkt: LocalDate,
    private val json: String,
    reservasjonDao: ReservasjonDao,
    saksbehandlerDao: SaksbehandlerDao,
    overstyringDao: OverstyringDao
) : Hendelse, MacroCommand() {
    override val commands: List<Command> = listOf(
        OpprettSaksbehandlerCommand(
            oid = oid,
            navn = navn,
            epost = epost,
            ident = ident,
            saksbehandlerDao = saksbehandlerDao
        ),
        ReserverPersonCommand(oid, fødselsnummer, reservasjonDao),
        PersisterOverstyringArbeidsforholdCommand(
            oid = oid,
            eventId = id,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = orgnummer,
            erAktivt = erAktivt,
            begrunnelse = begrunnelse,
            forklaring = forklaring,
            skjæringstidspunkt = skjæringstidspunkt,
            overstyringDao = overstyringDao
        ),
        InvaliderSaksbehandlerOppgaveCommand(fødselsnummer, orgnummer, saksbehandlerDao)
    )
    override fun fødselsnummer(): String = fødselsnummer
    override fun toJson(): String = json

    internal class OverstyringArbeidsforholdRiver(
        rapidsConnection: RapidsConnection,
        private val mediator: HendelseMediator
    ): River.PacketListener {
        private val logg = LoggerFactory.getLogger(this::class.java)
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

        init {
            River(rapidsConnection).apply {
                validate {
                    it.demandValue("@event_name", "overstyr_arbeidsforhold")
                    it.requireKey("aktørId")
                    it.requireKey("fødselsnummer")
                    it.requireKey("organisasjonsnummer")
                    it.requireKey("erAktivt")
                    it.requireKey("begrunnelse")
                    it.requireKey("forklaring")
                    it.requireKey("skjæringstidspunkt")
                    it.requireKey("saksbehandlerIdent")
                    it.requireKey("saksbehandlerOid")
                    it.requireKey("saksbehandlerNavn")
                    it.requireKey("saksbehandlerEpost")
                    it.requireKey("@id")
                }
            }.register(this)
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext) {
            val hendelseId = UUID.fromString(packet["@id"].asText())
            logg.info(
                "Mottok overstyring av arbeidsforhold med {}",
                StructuredArguments.keyValue("eventId", hendelseId)
            )
            sikkerLogg.info(
                "Mottok overstyring av arbeidsforhold med {}, {}",
                StructuredArguments.keyValue("hendelseId", hendelseId),
                StructuredArguments.keyValue("hendelse", packet.toJson())
            )

            mediator.overstyringArbeidsforhold(
                id = UUID.fromString(packet["@id"].asText()),
                fødselsnummer = packet["fødselsnummer"].asText(),
                oid = UUID.fromString(packet["saksbehandlerOid"].asText()),
                navn = packet["saksbehandlerNavn"].asText(),
                ident = packet["saksbehandlerIdent"].asText(),
                epost = packet["saksbehandlerEpost"].asText(),
                orgnummer = packet["organisasjonsnummer"].asText(),
                erAktivt = packet["erAktivt"].asBoolean(),
                begrunnelse = packet["begrunnelse"].asText(),
                forklaring = packet["forklaring"].asText(),
                skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
                json = packet.toJson(),
                context = context
            )
        }
    }
}
