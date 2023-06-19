package no.nav.helse.spesialist.api

import java.util.UUID
import javax.sql.DataSource
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spesialist.api.feilhåndtering.ManglerVurderingAvVarsler
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.overstyring.OverstyrArbeidsforholdDto
import no.nav.helse.spesialist.api.overstyring.OverstyrInntektOgRefusjonDto
import no.nav.helse.spesialist.api.overstyring.OverstyrTidslinjeDto
import no.nav.helse.spesialist.api.overstyring.SkjønnsmessigfastsattDto
import no.nav.helse.spesialist.api.saksbehandler.Saksbehandler
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerDao
import no.nav.helse.spesialist.api.utbetaling.AnnulleringDto
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import no.nav.helse.spesialist.api.varsel.Varsel
import no.nav.helse.spesialist.api.vedtak.GodkjenningDto
import no.nav.helse.spesialist.api.vedtak.Vedtaksperiode.Companion.harAktiveVarsler
import no.nav.helse.spesialist.api.vedtak.Vedtaksperiode.Companion.vurderVarsler
import no.nav.helse.spesialist.api.vedtaksperiode.ApiGenerasjonRepository
import org.slf4j.LoggerFactory

class SaksbehandlerMediator(
    dataSource: DataSource,
    private val rapidsConnection: RapidsConnection
) {
    private val saksbehandlerDao = SaksbehandlerDao(dataSource)
    private val generasjonRepository = ApiGenerasjonRepository(dataSource)
    private val varselRepository = ApiVarselRepository(dataSource)
    private val oppgaveApiDao = OppgaveApiDao(dataSource)

    internal fun håndter(annullering: AnnulleringDto, saksbehandler: Saksbehandler) {
        tellAnnullering()
        saksbehandler.persister(saksbehandlerDao)
        val message = annullering.somJsonMessage(saksbehandler).also {
            sikkerlogg.info(
                "Publiserer annullering fra api: {}, {}, {}\n${it.toJson()}",
                kv("fødselsnummer", annullering.fødselsnummer),
                kv("aktørId", annullering.aktørId),
                kv("organisasjonsnummer", annullering.organisasjonsnummer)
            )
        }
        rapidsConnection.publish(annullering.fødselsnummer, message.toJson())
    }

    internal fun håndter(overstyring: OverstyrTidslinjeDto, saksbehandler: Saksbehandler) {
        tellOverstyrTidslinje()
        val message = overstyring.somJsonMessage(saksbehandler.toDto()).also {
            sikkerlogg.info(
                "Publiserer overstyring av tidslinje fra api: {}, {}\n${it.toJson()}",
                kv("fødselsnummer", overstyring.fødselsnummer),
                kv("aktørId", overstyring.aktørId),
                kv("organisasjonsnummer", overstyring.organisasjonsnummer)
            )
        }
        rapidsConnection.publish(overstyring.fødselsnummer, message.toJson())
    }

    internal fun håndter(overstyring: OverstyrInntektOgRefusjonDto, saksbehandler: Saksbehandler) {
        tellOverstyrInntektOgRefusjon()
        val message = overstyring.somJsonMessage(saksbehandler.toDto()).also {
            sikkerlogg.info(
                "Publiserer overstyring av inntekt og refusjon fra api: {}, {}\n${it.toJson()}",
                kv("fødselsnummer", overstyring.fødselsnummer),
                kv("aktørId", overstyring.aktørId),
            )
        }
        rapidsConnection.publish(overstyring.fødselsnummer, message.toJson())
    }
    internal fun håndter(skjønnsmessigFastsattInntekt: SkjønnsmessigfastsattDto, saksbehandler: Saksbehandler) {
        tellSkjønnsmessigFastsettingInntekt()
        val message = skjønnsmessigFastsattInntekt.somJsonMessage(saksbehandler.toDto()).also {
            sikkerlogg.info(
                "Publiserer skjønnsmessig fastsetting av inntekt fra api: {}, {}\n${it.toJson()}",
                kv("fødselsnummer", skjønnsmessigFastsattInntekt.fødselsnummer),
                kv("aktørId", skjønnsmessigFastsattInntekt.aktørId),
            )
        }
        rapidsConnection.publish(skjønnsmessigFastsattInntekt.fødselsnummer, message.toJson())
    }

    internal fun håndter(overstyring: OverstyrArbeidsforholdDto, saksbehandler: Saksbehandler) {
        tellOverstyrArbeidsforhold()
        val message = overstyring.somJsonMessage(saksbehandler.toDto()).also {
            sikkerlogg.info(
                "Publiserer overstyring av arbeidsforhold fra api: {}, {}\n${it.toJson()}",
                kv("fødselsnummer", overstyring.fødselsnummer),
                kv("aktørId", overstyring.aktørId),
            )
        }
        rapidsConnection.publish(overstyring.fødselsnummer, message.toJson())
    }

    fun håndter(godkjenning: GodkjenningDto, behandlingId: UUID, saksbehandler: Saksbehandler) {
        val perioderTilBehandling = generasjonRepository.perioderTilBehandling(godkjenning.oppgavereferanse)
        if (godkjenning.godkjent) {
            if (perioderTilBehandling.harAktiveVarsler())
                throw ManglerVurderingAvVarsler(godkjenning.oppgavereferanse)
        }

        oppgaveApiDao.lagreBehandlingsreferanse(godkjenning.oppgavereferanse, behandlingId)

        val fødselsnummer = oppgaveApiDao.finnFødselsnummer(godkjenning.oppgavereferanse)

        perioderTilBehandling.vurderVarsler(godkjenning.godkjent, fødselsnummer, behandlingId, saksbehandler.ident(), this::vurderVarsel)
    }

    private fun vurderVarsel(
        fødselsnummer: String,
        behandlingId: UUID,
        vedtaksperiodeId: UUID,
        varselId: UUID,
        varseltittel: String,
        varselkode: String,
        forrigeStatus: Varsel.Varselstatus,
        gjeldendeStatus: Varsel.Varselstatus,
        saksbehandlerIdent: String
    ) {
        varselRepository.vurderVarselFor(varselId, gjeldendeStatus, saksbehandlerIdent)
        val message = JsonMessage.newMessage(
            "varsel_endret", mapOf(
                "fødselsnummer" to fødselsnummer,
                "vedtaksperiode_id" to vedtaksperiodeId,
                "behandling_id" to behandlingId,
                "varsel_id" to varselId,
                "varseltittel" to varseltittel,
                "varselkode" to varselkode,
                "forrige_status" to forrigeStatus.name,
                "gjeldende_status" to gjeldendeStatus.name
            )
        )
        sikkerlogg.info(
            "Publiserer varsel_endret for varsel med {}, {}, {}",
            kv("varselId", varselId),
            kv("varselkode", varselkode),
            kv("status", gjeldendeStatus)
        )
        rapidsConnection.publish(fødselsnummer, message.toJson())
    }

    fun håndterTotrinnsvurdering(oppgavereferanse: Long) {
        val perioderTilBehandling = generasjonRepository.perioderTilBehandling(oppgavereferanse)
        if (perioderTilBehandling.harAktiveVarsler())
            throw ManglerVurderingAvVarsler(oppgavereferanse)
    }

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
}