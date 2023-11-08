package no.nav.helse.mediator

import java.util.UUID
import javax.sql.DataSource
import no.nav.helse.Tilgangsgrupper
import no.nav.helse.db.ReservasjonDao
import no.nav.helse.db.SaksbehandlerDao
import no.nav.helse.mediator.oppgave.OppgaveMediator
import no.nav.helse.mediator.overstyring.Overstyringlagrer
import no.nav.helse.mediator.overstyring.Saksbehandlingsmelder
import no.nav.helse.mediator.saksbehandler.SaksbehandlerLagrer
import no.nav.helse.mediator.saksbehandler.SaksbehandlerMapper.tilApiversjon
import no.nav.helse.modell.ManglerTilgang
import no.nav.helse.modell.Modellfeil
import no.nav.helse.modell.OppgaveAlleredeSendtBeslutter
import no.nav.helse.modell.OppgaveAlleredeSendtIRetur
import no.nav.helse.modell.OppgaveKreverVurderingAvToSaksbehandlere
import no.nav.helse.modell.OppgaveTildeltNoenAndre
import no.nav.helse.modell.overstyring.OverstyringDao
import no.nav.helse.modell.saksbehandler.Saksbehandler
import no.nav.helse.modell.saksbehandler.handlinger.Annullering
import no.nav.helse.modell.saksbehandler.handlinger.Handling
import no.nav.helse.modell.saksbehandler.handlinger.Oppgavehandling
import no.nav.helse.modell.saksbehandler.handlinger.Overstyring
import no.nav.helse.modell.saksbehandler.handlinger.OverstyrtArbeidsforhold
import no.nav.helse.modell.saksbehandler.handlinger.OverstyrtArbeidsgiver
import no.nav.helse.modell.saksbehandler.handlinger.OverstyrtInntektOgRefusjon
import no.nav.helse.modell.saksbehandler.handlinger.OverstyrtTidslinje
import no.nav.helse.modell.saksbehandler.handlinger.OverstyrtTidslinjedag
import no.nav.helse.modell.saksbehandler.handlinger.Refusjonselement
import no.nav.helse.modell.saksbehandler.handlinger.SkjønnsfastsattSykepengegrunnlag
import no.nav.helse.modell.varsel.Varselmelder
import no.nav.helse.modell.vilkårsprøving.Lovhjemmel
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.withMDC
import no.nav.helse.spesialist.api.Saksbehandlerhåndterer
import no.nav.helse.spesialist.api.abonnement.AbonnementDao
import no.nav.helse.spesialist.api.abonnement.OpptegnelseDao
import no.nav.helse.spesialist.api.feilhåndtering.IkkeTilgang
import no.nav.helse.spesialist.api.feilhåndtering.ManglerVurderingAvVarsler
import no.nav.helse.spesialist.api.feilhåndtering.OppgaveIkkeTildelt
import no.nav.helse.spesialist.api.graphql.schema.Opptegnelse
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.AnnulleringHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.AvmeldOppgave
import no.nav.helse.spesialist.api.saksbehandler.handlinger.FjernOppgaveFraPåVent
import no.nav.helse.spesialist.api.saksbehandler.handlinger.HandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.LeggOppgavePåVent
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrArbeidsforholdHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrInntektOgRefusjonHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrTidslinjeHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.SkjønnsfastsettSykepengegrunnlagHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.TildelOppgave
import no.nav.helse.spesialist.api.tildeling.TildelingApiDto
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import no.nav.helse.spesialist.api.varsel.Varsel
import no.nav.helse.spesialist.api.vedtak.GodkjenningDto
import no.nav.helse.spesialist.api.vedtak.Vedtaksperiode.Companion.harAktiveVarsler
import no.nav.helse.spesialist.api.vedtak.Vedtaksperiode.Companion.vurderVarsler
import no.nav.helse.spesialist.api.vedtaksperiode.ApiGenerasjonRepository
import org.slf4j.LoggerFactory

internal class SaksbehandlerMediator(
    dataSource: DataSource,
    private val versjonAvKode: String,
    private val rapidsConnection: RapidsConnection,
    private val oppgaveMediator: OppgaveMediator,
    private val tilgangsgrupper: Tilgangsgrupper,
): Saksbehandlerhåndterer {
    private val saksbehandlerDao = SaksbehandlerDao(dataSource)
    private val generasjonRepository = ApiGenerasjonRepository(dataSource)
    private val varselRepository = ApiVarselRepository(dataSource)
    private val oppgaveApiDao = OppgaveApiDao(dataSource)
    private val opptegnelseDao = OpptegnelseDao(dataSource)
    private val abonnementDao = AbonnementDao(dataSource)
    private val reservasjonDao = ReservasjonDao(dataSource)
    private val overstyringDao = OverstyringDao(dataSource)
    private val varselmelder = Varselmelder(rapidsConnection)

    override fun <T : HandlingFraApi> håndter(handlingFraApi: T, saksbehandlerFraApi: SaksbehandlerFraApi) {
        val saksbehandler = saksbehandlerFraApi.tilSaksbehandler()
        val modellhandling = handlingFraApi.tilModellversjon()
        SaksbehandlerLagrer(saksbehandlerDao).lagre(saksbehandler)
        tell(modellhandling)
        saksbehandler.register(Saksbehandlingsmelder(rapidsConnection))
        saksbehandler.register(Subsumsjonsmelder(versjonAvKode, rapidsConnection))
        val handlingId = UUID.randomUUID()

        withMDC(
            mapOf(
                "saksbehandlerOid" to saksbehandler.oid().toString(),
                "handlingId" to handlingId.toString()
            )
        ) {
            sikkerlogg.info("Utfører handling ${modellhandling.loggnavn()} på vegne av saksbehandler $saksbehandler")
            when (modellhandling) {
                is Overstyring -> håndter(modellhandling, saksbehandler)
                is Oppgavehandling -> håndter(modellhandling, saksbehandler)
                else -> modellhandling.utførAv(saksbehandler)
            }
            sikkerlogg.info("Handling ${modellhandling.loggnavn()} utført")
        }
    }

    private fun håndter(handling: Oppgavehandling, saksbehandler: Saksbehandler) {
        try {
            oppgaveMediator.håndter(handling, saksbehandler)
        } catch (e: Modellfeil) {
            throw e.tilApiversjon()
        }
    }

    private fun håndter(handling: Overstyring, saksbehandler: Saksbehandler) {
        val fødselsnummer = handling.gjelderFødselsnummer()
        val antall = oppgaveApiDao.invaliderOppgaveFor(fødselsnummer)
        sikkerlogg.info("Invaliderer $antall {} for $fødselsnummer", if (antall == 1) "oppgave" else "oppgaver")
        reservasjonDao.reserverPerson(saksbehandler.oid(), fødselsnummer, false)
        sikkerlogg.info("Reserverer person $fødselsnummer til saksbehandler $saksbehandler")
        Overstyringlagrer(overstyringDao).apply {
            this.lagre(handling, saksbehandler.oid())
        }
        handling.utførAv(saksbehandler)
    }

    override fun opprettAbonnement(saksbehandlerFraApi: SaksbehandlerFraApi, personidentifikator: String) {
        val saksbehandler = saksbehandlerFraApi.tilSaksbehandler()
        SaksbehandlerLagrer(saksbehandlerDao).lagre(saksbehandler)
        abonnementDao.opprettAbonnement(saksbehandler.oid(), personidentifikator.toLong())
    }

    override fun hentAbonnerteOpptegnelser(
        saksbehandlerFraApi: SaksbehandlerFraApi,
        sisteSekvensId: Int,
    ): List<Opptegnelse> {
        val saksbehandler = saksbehandlerFraApi.tilSaksbehandler()
        SaksbehandlerLagrer(saksbehandlerDao).lagre(saksbehandler)
        abonnementDao.registrerSistekvensnummer(saksbehandler.oid(), sisteSekvensId)
        return opptegnelseDao.finnOpptegnelser(saksbehandler.oid())
    }

    override fun hentAbonnerteOpptegnelser(saksbehandlerFraApi: SaksbehandlerFraApi): List<Opptegnelse> {
        val saksbehandler = saksbehandlerFraApi.tilSaksbehandler()
        SaksbehandlerLagrer(saksbehandlerDao).lagre(saksbehandler)
        return opptegnelseDao.finnOpptegnelser(saksbehandler.oid())
    }

    override fun håndter(godkjenning: GodkjenningDto, behandlingId: UUID, saksbehandlerFraApi: SaksbehandlerFraApi) {
        val saksbehandler = saksbehandlerFraApi.tilSaksbehandler()
        val perioderTilBehandling = generasjonRepository.perioderTilBehandling(godkjenning.oppgavereferanse)
        if (godkjenning.godkjent) {
            if (perioderTilBehandling.harAktiveVarsler())
                throw ManglerVurderingAvVarsler(godkjenning.oppgavereferanse)
        }

        oppgaveApiDao.lagreBehandlingsreferanse(godkjenning.oppgavereferanse, behandlingId)

        if (godkjenning.godkjent) {
            val fødselsnummer = oppgaveApiDao.finnFødselsnummer(godkjenning.oppgavereferanse)
            perioderTilBehandling.vurderVarsler(
                true,
                fødselsnummer,
                behandlingId,
                saksbehandler.ident(),
                this::vurderVarsel
            )
        }
    }

    override fun håndterTotrinnsvurdering(oppgavereferanse: Long) {
        val perioderTilBehandling = generasjonRepository.perioderTilBehandling(oppgavereferanse)
        if (perioderTilBehandling.harAktiveVarsler())
            throw ManglerVurderingAvVarsler(oppgavereferanse)
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
        saksbehandlerIdent: String,
    ) {
        varselRepository.vurderVarselFor(varselId, gjeldendeStatus, saksbehandlerIdent)
        varselmelder.meldVarselEndret(
            fødselsnummer,
            behandlingId,
            vedtaksperiodeId,
            varselId,
            varseltittel,
            varselkode,
            forrigeStatus,
            gjeldendeStatus
        )
    }

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        internal fun Modellfeil.tilApiversjon(): no.nav.helse.spesialist.api.feilhåndtering.Modellfeil {
            return when (this) {
                is no.nav.helse.modell.OppgaveIkkeTildelt -> OppgaveIkkeTildelt(oppgaveId)
                is OppgaveTildeltNoenAndre -> {
                    val (oid, navn, epost) = this.saksbehandler.tilApiversjon()
                    no.nav.helse.spesialist.api.feilhåndtering.OppgaveTildeltNoenAndre(TildelingApiDto(navn, epost, oid, påVent))
                }
                is OppgaveAlleredeSendtBeslutter -> no.nav.helse.spesialist.api.feilhåndtering.OppgaveAlleredeSendtBeslutter(oppgaveId)
                is OppgaveAlleredeSendtIRetur -> no.nav.helse.spesialist.api.feilhåndtering.OppgaveAlleredeSendtIRetur(oppgaveId)
                is OppgaveKreverVurderingAvToSaksbehandlere -> no.nav.helse.spesialist.api.feilhåndtering.OppgaveKreverVurderingAvToSaksbehandlere(oppgaveId)
                is ManglerTilgang -> IkkeTilgang(oid, oppgaveId)
            }
        }
    }

    private fun SaksbehandlerFraApi.tilSaksbehandler() =
        Saksbehandler(epost, oid, navn, ident, TilgangskontrollørForApi(this.grupper, tilgangsgrupper))

    private fun HandlingFraApi.tilModellversjon(): Handling {
        return when (this) {
            is OverstyrArbeidsforholdHandlingFraApi -> this.tilModellversjon()
            is OverstyrInntektOgRefusjonHandlingFraApi -> this.tilModellversjon()
            is OverstyrTidslinjeHandlingFraApi -> this.tilModellversjon()
            is SkjønnsfastsettSykepengegrunnlagHandlingFraApi -> this.tilModellversjon()
            is AnnulleringHandlingFraApi -> this.tilModellversjon()
            is TildelOppgave -> this.tilModellversjon()
            is AvmeldOppgave -> this.tilModellversjon()
            is FjernOppgaveFraPåVent -> this.tilModellversjon()
            is LeggOppgavePåVent -> this.tilModellversjon()
        }
    }

    private fun OverstyrArbeidsforholdHandlingFraApi.tilModellversjon(): OverstyrtArbeidsforhold {
        return OverstyrtArbeidsforhold(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            skjæringstidspunkt = skjæringstidspunkt,
            overstyrteArbeidsforhold = overstyrteArbeidsforhold.map {
                OverstyrtArbeidsforhold.Arbeidsforhold(it.orgnummer, it.deaktivert, it.begrunnelse, it.forklaring)
            }
        )
    }

    private fun OverstyrInntektOgRefusjonHandlingFraApi.tilModellversjon(): OverstyrtInntektOgRefusjon {
        return OverstyrtInntektOgRefusjon(
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgivere = arbeidsgivere.map { overstyrArbeidsgiver ->
                OverstyrtArbeidsgiver(
                    overstyrArbeidsgiver.organisasjonsnummer,
                    overstyrArbeidsgiver.månedligInntekt,
                    overstyrArbeidsgiver.fraMånedligInntekt,
                    overstyrArbeidsgiver.refusjonsopplysninger?.map {
                        Refusjonselement(it.fom, it.tom, it.beløp)
                    },
                    overstyrArbeidsgiver.fraRefusjonsopplysninger?.map {
                        Refusjonselement(it.fom, it.tom, it.beløp)
                    },
                    begrunnelse = overstyrArbeidsgiver.begrunnelse,
                    forklaring = overstyrArbeidsgiver.forklaring,
                    lovhjemmel = overstyrArbeidsgiver.lovhjemmel?.let {
                        Lovhjemmel(it.paragraf, it.ledd, it.bokstav, it.lovverk, it.lovverksversjon)
                    }
                )
            },
        )
    }

    private fun OverstyrTidslinjeHandlingFraApi.tilModellversjon(): OverstyrtTidslinje {
        return OverstyrtTidslinje(
            vedtaksperiodeId = vedtaksperiodeId,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            dager = dager.map {
                OverstyrtTidslinjedag(
                    dato = it.dato,
                    type = it.type,
                    fraType = it.fraType,
                    grad = it.grad,
                    fraGrad = it.fraGrad,
                    lovhjemmel = it.lovhjemmel?.let { lovhjemmel ->
                        Lovhjemmel(
                            paragraf = lovhjemmel.paragraf,
                            ledd = lovhjemmel.ledd,
                            bokstav = lovhjemmel.bokstav,
                            lovverk = lovhjemmel.lovverk,
                            lovverksversjon = lovhjemmel.lovverksversjon,
                        )
                    })
            },
            begrunnelse = begrunnelse
        )
    }

    private fun SkjønnsfastsettSykepengegrunnlagHandlingFraApi.tilModellversjon(): SkjønnsfastsattSykepengegrunnlag {
        return SkjønnsfastsattSykepengegrunnlag(
            aktørId,
            fødselsnummer,
            skjæringstidspunkt,
            arbeidsgivere = arbeidsgivere.map { arbeidsgiverDto ->
                SkjønnsfastsattSykepengegrunnlag.SkjønnsfastsattArbeidsgiver(
                    arbeidsgiverDto.organisasjonsnummer,
                    arbeidsgiverDto.årlig,
                    arbeidsgiverDto.fraÅrlig,
                    arbeidsgiverDto.årsak,
                    type = when (arbeidsgiverDto.type) {
                        SkjønnsfastsettSykepengegrunnlagHandlingFraApi.SkjønnsfastsattArbeidsgiverFraApi.SkjønnsfastsettingstypeDto.OMREGNET_ÅRSINNTEKT -> SkjønnsfastsattSykepengegrunnlag.SkjønnsfastsattArbeidsgiver.Skjønnsfastsettingstype.OMREGNET_ÅRSINNTEKT
                        SkjønnsfastsettSykepengegrunnlagHandlingFraApi.SkjønnsfastsattArbeidsgiverFraApi.SkjønnsfastsettingstypeDto.RAPPORTERT_ÅRSINNTEKT -> SkjønnsfastsattSykepengegrunnlag.SkjønnsfastsattArbeidsgiver.Skjønnsfastsettingstype.RAPPORTERT_ÅRSINNTEKT
                        SkjønnsfastsettSykepengegrunnlagHandlingFraApi.SkjønnsfastsattArbeidsgiverFraApi.SkjønnsfastsettingstypeDto.ANNET -> SkjønnsfastsattSykepengegrunnlag.SkjønnsfastsattArbeidsgiver.Skjønnsfastsettingstype.ANNET
                    },
                    begrunnelseMal = arbeidsgiverDto.begrunnelseMal,
                    begrunnelseFritekst = arbeidsgiverDto.begrunnelseFritekst,
                    begrunnelseKonklusjon = arbeidsgiverDto.begrunnelseKonklusjon,
                    lovhjemmel = arbeidsgiverDto.lovhjemmel?.let {
                        Lovhjemmel(
                            paragraf = it.paragraf,
                            ledd = it.ledd,
                            bokstav = it.bokstav,
                            lovverk = it.lovverk,
                            lovverksversjon = it.lovverksversjon
                        )
                    },
                    initierendeVedtaksperiodeId = arbeidsgiverDto.initierendeVedtaksperiodeId
                )
            }
        )
    }

    private fun AnnulleringHandlingFraApi.tilModellversjon(): Annullering {
        return Annullering(
            aktørId = this.aktørId,
            fødselsnummer = this.fødselsnummer,
            organisasjonsnummer = this.organisasjonsnummer,
            fagsystemId = this.fagsystemId,
            begrunnelser = this.begrunnelser,
            kommentar = this.kommentar
        )
    }

    private fun TildelOppgave.tilModellversjon(): no.nav.helse.modell.saksbehandler.handlinger.TildelOppgave {
        return no.nav.helse.modell.saksbehandler.handlinger.TildelOppgave(this.oppgaveId)
    }

    private fun AvmeldOppgave.tilModellversjon(): no.nav.helse.modell.saksbehandler.handlinger.AvmeldOppgave {
        return no.nav.helse.modell.saksbehandler.handlinger.AvmeldOppgave(this.oppgaveId)
    }

    private fun LeggOppgavePåVent.tilModellversjon(): no.nav.helse.modell.saksbehandler.handlinger.LeggOppgavePåVent {
        return no.nav.helse.modell.saksbehandler.handlinger.LeggOppgavePåVent(this.oppgaveId)
    }

    private fun FjernOppgaveFraPåVent.tilModellversjon(): no.nav.helse.modell.saksbehandler.handlinger.FjernOppgaveFraPåVent {
        return no.nav.helse.modell.saksbehandler.handlinger.FjernOppgaveFraPåVent(this.oppgaveId)
    }
}
