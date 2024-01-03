package no.nav.helse.spesialist.api.behandlingsstatistikk

import java.time.LocalDate
import no.nav.helse.spesialist.api.oppgave.Oppgavetype
import no.nav.helse.spesialist.api.vedtaksperiode.Inntektskilde
import no.nav.helse.spesialist.api.vedtaksperiode.Mottakertype
import no.nav.helse.spesialist.api.vedtaksperiode.Periodetype

class BehandlingsstatistikkMediator(private val behandlingsstatistikkDao: BehandlingsstatistikkDao) {

    fun getBehandlingsstatistikk(fom: LocalDate = LocalDate.now()): BehandlingsstatistikkResponse {
        val automatisertPerInntektPeriodetypeOgMottaker = behandlingsstatistikkDao.getAutomatiseringerPerInntektOgPeriodetype(fom)
        val manueltUtførteOppgaver = behandlingsstatistikkDao.getManueltUtførteOppgaverPerInntektOgPeriodetype(fom)
        val tilgjengeligeOppgaver = behandlingsstatistikkDao.getTilgjengeligeOppgaverPerInntektOgPeriodetype()

        val enArbeidsgiver = {
            Statistikk(
                automatisk = automatisertPerInntektPeriodetypeOgMottaker.perInntekttype[Inntektskilde.EN_ARBEIDSGIVER] ?: 0,
                manuelt = manueltUtførteOppgaver.perInntekttype[Inntektskilde.EN_ARBEIDSGIVER] ?: 0,
                tilgjengelig = tilgjengeligeOppgaver.perInntekttype[Inntektskilde.EN_ARBEIDSGIVER] ?: 0
            )
        }

        val flereArbeidsgivere = {
            Statistikk(
                automatisk = automatisertPerInntektPeriodetypeOgMottaker.perInntekttype[Inntektskilde.FLERE_ARBEIDSGIVERE] ?: 0,
                manuelt = manueltUtførteOppgaver.perInntekttype[Inntektskilde.FLERE_ARBEIDSGIVERE] ?: 0,
                tilgjengelig = tilgjengeligeOppgaver.perInntekttype[Inntektskilde.FLERE_ARBEIDSGIVERE] ?: 0
            )
        }

        val forstegangsbehandling = {
            Statistikk(
                automatisk = automatisertPerInntektPeriodetypeOgMottaker.perPeriodetype[Periodetype.FØRSTEGANGSBEHANDLING] ?: 0,
                manuelt = manueltUtførteOppgaver.perPeriodetype[Periodetype.FØRSTEGANGSBEHANDLING] ?: 0,
                tilgjengelig = tilgjengeligeOppgaver.perPeriodetype[Periodetype.FØRSTEGANGSBEHANDLING] ?: 0
            )
        }

        val forlengelser = {
            Statistikk(
                automatisk = (automatisertPerInntektPeriodetypeOgMottaker.perPeriodetype[Periodetype.FORLENGELSE] ?: 0)
                        + (automatisertPerInntektPeriodetypeOgMottaker.perPeriodetype[Periodetype.INFOTRYGDFORLENGELSE] ?: 0),
                manuelt = (manueltUtførteOppgaver.perPeriodetype[Periodetype.FORLENGELSE] ?: 0)
                        + (manueltUtførteOppgaver.perPeriodetype[Periodetype.INFOTRYGDFORLENGELSE] ?: 0),
                tilgjengelig = (tilgjengeligeOppgaver.perPeriodetype[Periodetype.FORLENGELSE] ?: 0)
                        + (tilgjengeligeOppgaver.perPeriodetype[Periodetype.INFOTRYGDFORLENGELSE] ?: 0),
            )
        }

        val forlengelseIt = {
            Statistikk(
                automatisk = automatisertPerInntektPeriodetypeOgMottaker.perPeriodetype[Periodetype.OVERGANG_FRA_IT] ?: 0,
                manuelt = manueltUtførteOppgaver.perPeriodetype[Periodetype.OVERGANG_FRA_IT] ?: 0,
                tilgjengelig = tilgjengeligeOppgaver.perPeriodetype[Periodetype.OVERGANG_FRA_IT] ?: 0,
            )
        }

        val manueltUtførteOppgavetyper = behandlingsstatistikkDao.getManueltUtførteOppgaverPerOppgavetype(fom)
        val tilgjengeligeOppgavetyper = behandlingsstatistikkDao.getTilgjengeligeOppgaverPerOppgavetype()

        return BehandlingsstatistikkResponse(
            enArbeidsgiver = enArbeidsgiver(),
            flereArbeidsgivere = flereArbeidsgivere(),
            forstegangsbehandling = forstegangsbehandling(),
            forlengelser = forlengelser(),
            forlengelseIt = forlengelseIt(),
            utbetalingTilArbeidsgiver = Statistikk(
                automatisertPerInntektPeriodetypeOgMottaker.perMottakertype[Mottakertype.ARBEIDSGIVER] ?: 0,
                manueltUtførteOppgavetyper[Oppgavetype.SØKNAD] ?: 0,
                tilgjengeligeOppgavetyper[Oppgavetype.SØKNAD] ?: 0,
            ),
            utbetalingTilSykmeldt = Statistikk(
                automatisertPerInntektPeriodetypeOgMottaker.perMottakertype[Mottakertype.SYKMELDT] ?: 0,
                manueltUtførteOppgavetyper[Oppgavetype.UTBETALING_TIL_SYKMELDT] ?: 0,
                tilgjengeligeOppgavetyper[Oppgavetype.UTBETALING_TIL_SYKMELDT] ?: 0
            ),
            faresignaler = Statistikk(
                0,
                manueltUtførteOppgavetyper[Oppgavetype.RISK_QA] ?: 0,
                tilgjengeligeOppgavetyper[Oppgavetype.RISK_QA] ?: 0
            ),
            fortroligAdresse = Statistikk(
                0,
                manueltUtførteOppgavetyper[Oppgavetype.FORTROLIG_ADRESSE] ?: 0,
                tilgjengeligeOppgavetyper[Oppgavetype.FORTROLIG_ADRESSE] ?: 0
            ),
            stikkprover = Statistikk(
                0,
                manueltUtførteOppgavetyper[Oppgavetype.STIKKPRØVE] ?: 0,
                tilgjengeligeOppgavetyper[Oppgavetype.STIKKPRØVE] ?: 0
            ),
            revurdering = Statistikk(
                0,
                manueltUtførteOppgavetyper[Oppgavetype.REVURDERING] ?: 0,
                tilgjengeligeOppgavetyper[Oppgavetype.REVURDERING] ?: 0
            ),
            delvisRefusjon = Statistikk(
                automatisertPerInntektPeriodetypeOgMottaker.perMottakertype[Mottakertype.BEGGE] ?: 0,
                manueltUtførteOppgavetyper[Oppgavetype.DELVIS_REFUSJON] ?: 0,
                tilgjengeligeOppgavetyper[Oppgavetype.DELVIS_REFUSJON] ?: 0
            ),
            beslutter = Statistikk(
                0,
                behandlingsstatistikkDao.getAntallFullførteBeslutteroppgaver(fom),
                behandlingsstatistikkDao.getAntallTilgjengeligeBeslutteroppgaver()
            ),
            egenAnsatt = Statistikk(
                0,
                behandlingsstatistikkDao.getAntallManueltFullførteEgenAnsattOppgaver(fom),
                behandlingsstatistikkDao.getAntallTilgjengeligeEgenAnsattOppgaver()
            ),
            antallAnnulleringer = behandlingsstatistikkDao.getAntallAnnulleringer(fom),
        )
    }

    fun hentSaksbehandlingsstatistikk() =
        behandlingsstatistikkDao.oppgavestatistikk().let(BehandlingstatistikkForSpeilDto::toSpeilMap)
}
