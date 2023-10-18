package no.nav.helse.spesialist.api.behandlingsstatistikk

import java.time.LocalDate
import javax.sql.DataSource
import kotliquery.Query
import kotliquery.Row
import no.nav.helse.HelseDao
import no.nav.helse.spesialist.api.oppgave.Oppgavetype
import no.nav.helse.spesialist.api.vedtaksperiode.Inntektskilde
import no.nav.helse.spesialist.api.vedtaksperiode.Mottakertype
import no.nav.helse.spesialist.api.vedtaksperiode.Periodetype

class BehandlingsstatistikkDao(dataSource: DataSource) : HelseDao(dataSource) {

    fun getAntallTilgjengeligeBeslutteroppgaver() = asSQL(
        """
            SELECT count(1)
            FROM totrinnsvurdering t
            INNER JOIN vedtak v ON v.vedtaksperiode_id = t.vedtaksperiode_id
            INNER JOIN oppgave o ON v.id = o.vedtak_ref
            WHERE o.status='AvventerSaksbehandler'::oppgavestatus
              AND v.forkastet = false 
              AND o.egenskaper @> ARRAY['BESLUTTER']::VARCHAR[]
        """
    ).single { it.int("count") } ?: 0

    fun getAntallFullførteBeslutteroppgaver(fom: LocalDate) = asSQL(
        """
            SELECT count(1)
            FROM totrinnsvurdering
            WHERE utbetaling_id_ref IS NOT NULL
            AND oppdatert >= :fom
        """, mapOf("fom" to fom)
    ).single { it.int("count") } ?: 0

    fun getAutomatiseringerPerInntektOgPeriodetype(fom: LocalDate): StatistikkPerInntektOgPeriodetype {
        val query = asSQL("""
            SELECT s.type,
                s.inntektskilde,
                CASE WHEN ui.arbeidsgiverbeløp > 0 AND ui.personbeløp > 0 THEN '${Mottakertype.BEGGE}'
                    WHEN ui.personbeløp > 0 THEN '${Mottakertype.SYKMELDT}'
                    ELSE '${Mottakertype.ARBEIDSGIVER}'
                END AS mottakertype,
                count(distinct a.id)
            FROM automatisering a
                     INNER JOIN saksbehandleroppgavetype s on s.vedtak_ref = a.vedtaksperiode_ref
                     INNER JOIN vedtak v ON v.id = a.vedtaksperiode_ref
                     INNER JOIN vedtaksperiode_utbetaling_id vui on vui.vedtaksperiode_id = v.vedtaksperiode_id 
                     INNER JOIN utbetaling_id ui on ui.utbetaling_id = vui.utbetaling_id
            WHERE a.opprettet >= :fom
              AND a.automatisert = true
            GROUP BY s.type, s.inntektskilde, mottakertype;
        """, mapOf("fom" to fom))

        return getStatistikkPerInntektOgPeriodetype(query, inkluderMottakertype = true)
    }

    fun getTilgjengeligeOppgaverPerInntektOgPeriodetype(): StatistikkPerInntektOgPeriodetype {
        val query = asSQL("""
            SELECT s.type, s.inntektskilde, count(distinct o.id)
            FROM oppgave o
                     INNER JOIN saksbehandleroppgavetype s on o.vedtak_ref = s.vedtak_ref
            WHERE o.status = 'AvventerSaksbehandler'
            GROUP BY s.type, s.inntektskilde;
        """)

        return getStatistikkPerInntektOgPeriodetype(query)
    }

    fun getManueltUtførteOppgaverPerInntektOgPeriodetype(fom: LocalDate): StatistikkPerInntektOgPeriodetype {
        val query = asSQL("""
            SELECT s.type, s.inntektskilde, count(distinct o.id)
            FROM oppgave o
                     INNER JOIN saksbehandleroppgavetype s on o.vedtak_ref = s.vedtak_ref
            WHERE o.status = 'Ferdigstilt'
              AND o.oppdatert >= :fom
            GROUP BY s.type, s.inntektskilde;
        """, mapOf("fom" to fom))

        return getStatistikkPerInntektOgPeriodetype(query)
    }

    fun getManueltUtførteOppgaverPerOppgavetype(fom: LocalDate) = asSQL(
        """
            SELECT o.type, count(distinct o.id)
            FROM oppgave o
            WHERE o.status = 'Ferdigstilt'
              AND o.oppdatert >= :fom
            GROUP BY o.type;
        """, mapOf("fom" to fom)
    ).list { mapOf(Oppgavetype.valueOf(it.string("type")) to it.int("count")) }
        .fold(emptyMap(), Map<Oppgavetype, Int>::plus)

    fun getTilgjengeligeOppgaverPerOppgavetype() = asSQL(
        """
            SELECT o.type, count(distinct o.id)
            FROM oppgave o
            WHERE o.status = 'AvventerSaksbehandler'
            GROUP BY o.type;
        """
    ).list { mapOf(Oppgavetype.valueOf(it.string("type")) to it.int("count")) }
        .fold(emptyMap(), Map<Oppgavetype, Int>::plus)

    private fun getStatistikkPerInntektOgPeriodetype(
        query: Query,
        inkluderMottakertype: Boolean = false
    ): StatistikkPerInntektOgPeriodetype {
        val rader = query.list {
            InntektOgPeriodetyperad(
                inntekttype = Inntektskilde.valueOf(it.string("inntektskilde")),
                periodetype = Periodetype.valueOf(it.string("type")),
                mottakertype = if (inkluderMottakertype) Mottakertype.valueOf(it.string("mottakertype")) else null,
                antall = it.int("count")
            )
        }

        val perInntekttype = Inntektskilde.entries.map { inntektskilde ->
            mapOf(inntektskilde to rader.filter { it.inntekttype == inntektskilde }.sumOf { it.antall })
        }.fold(emptyMap(), Map<Inntektskilde, Int>::plus)

        val perPeriodetype = Periodetype.entries.map { periodetype ->
            mapOf(periodetype to rader.filter { it.periodetype == periodetype }.sumOf { it.antall })
        }.fold(emptyMap(), Map<Periodetype, Int>::plus)

        val perMottakertype = if (inkluderMottakertype) {
            Mottakertype.entries.map { mottakertype ->
                mapOf(mottakertype to rader.filter { it.mottakertype == mottakertype }.sumOf { it.antall })
            }.fold(emptyMap(), Map<Mottakertype, Int>::plus)
        } else emptyMap()

        return StatistikkPerInntektOgPeriodetype(
            perInntekttype = perInntekttype,
            perPeriodetype = perPeriodetype,
            perMottakertype = perMottakertype,
        )
    }

    fun getAntallAnnulleringer(fom: LocalDate) = asSQL(
        """
            SELECT count(distinct u.id) as annulleringer
            FROM utbetaling u
            WHERE u.status = 'ANNULLERT'
              AND u.opprettet >= :fom;
        """, mapOf("fom" to fom)
    ).single { it.int("annulleringer") } ?: 0

    fun oppgavestatistikk(fom: LocalDate = LocalDate.now()): BehandlingsstatistikkDto {

        val godkjentManueltPerPeriodetype = godkjentManueltPerPeriodetype(fom)
        val tilGodkjenningPerPeriodetype = tilGodkjenningPerPeriodetype()
        val tildeltPerPeriodetype = tildeltPerPeriodetype()

        val godkjentManueltTotalt = godkjentManueltPerPeriodetype(fom).sumOf { (_, antall) -> antall }
        val annulleringerTotalt = antallAnnulleringer(fom)
        val godkjentAutomatiskTotalt = godkjentAutomatiskTotalt(fom)
        val oppgaverTilGodkjenningTotalt = tilGodkjenningPerPeriodetype.sumOf { (_, antall) -> antall }
        val tildelteOppgaverTotalt = tildeltPerPeriodetype.sumOf { (_, antall) -> antall }

        val behandletTotalt = annulleringerTotalt + godkjentManueltTotalt + godkjentAutomatiskTotalt

        return BehandlingsstatistikkDto(
            oppgaverTilGodkjenning = BehandlingsstatistikkDto.OppgavestatistikkDto(
                totalt = oppgaverTilGodkjenningTotalt,
                perPeriodetype = tilGodkjenningPerPeriodetype,
            ),
            tildelteOppgaver = BehandlingsstatistikkDto.OppgavestatistikkDto(
                totalt = tildelteOppgaverTotalt,
                perPeriodetype = tildeltPerPeriodetype
            ),
            fullførteBehandlinger = BehandlingsstatistikkDto.BehandlingerDto(
                annullert = annulleringerTotalt,
                manuelt = BehandlingsstatistikkDto.OppgavestatistikkDto(
                    totalt = godkjentManueltTotalt,
                    perPeriodetype = godkjentManueltPerPeriodetype
                ),
                automatisk = godkjentAutomatiskTotalt,
                totalt = behandletTotalt
            )
        )
    }

    private fun tilGodkjenningPerPeriodetype() = asSQL(
        """ SELECT sot.type AS periodetype, o.type, COUNT(distinct o.id)
            FILTER (WHERE o.type = 'SØKNAD') AS antall,
            COUNT(distinct o.id) as antallAvOppgaveType
            FROM oppgave o
              INNER JOIN saksbehandleroppgavetype sot ON o.vedtak_ref = sot.vedtak_ref
            WHERE o.status = 'AvventerSaksbehandler'
            GROUP BY sot.type, o.type
        """).list { perStatistikktype(it) }

    private fun tildeltPerPeriodetype() = asSQL(
        """ SELECT s.type as periodetype, o.type,
            COUNT(distinct o.id) FILTER (WHERE o.type = 'SØKNAD') AS antall,
            COUNT(distinct o.id) as antallAvOppgaveType
            FROM oppgave o
              INNER JOIN vedtak v on o.vedtak_ref = v.id
              INNER JOIN saksbehandleroppgavetype s on v.id = s.vedtak_ref
              INNER JOIN tildeling t on o.id = t.oppgave_id_ref
            WHERE o.status = 'AvventerSaksbehandler'
            GROUP BY s.type, o.type
        """).list { perStatistikktype(it) }

    private fun godkjentManueltPerPeriodetype(fom: LocalDate) = asSQL(
        """ SELECT sot.type AS periodetype, o.type,
            COUNT(distinct o.id) FILTER (WHERE o.type = 'SØKNAD') AS antall,
            COUNT(distinct o.id) as antallAvOppgaveType
            FROM oppgave o
              INNER JOIN saksbehandleroppgavetype sot ON o.vedtak_ref = sot.vedtak_ref
            WHERE o.status = 'Ferdigstilt' AND o.oppdatert >= :fom
            GROUP BY sot.type, o.type
        """, mapOf("fom" to fom)
    ).list { perStatistikktype(it) }

    private fun godkjentAutomatiskTotalt(fom: LocalDate) = requireNotNull(asSQL(
        """ SELECT COUNT(1) as antall
            FROM automatisering a
                INNER JOIN vedtak v on a.vedtaksperiode_ref = v.id
            WHERE a.automatisert = true 
            AND a.stikkprøve = false 
            AND a.opprettet >= :fom
            AND (a.inaktiv_fra IS NULL OR a.inaktiv_fra > now()) 
        """, mapOf("fom" to fom)
    ).single { it.int("antall") })

    private fun antallAnnulleringer(fom: LocalDate) = requireNotNull(asSQL(
        """
            SELECT COUNT(1) as antall
            FROM annullert_av_saksbehandler
            WHERE annullert_tidspunkt >= :fom
        """, mapOf("fom" to fom)
    ).single { it.int("antall") })

    private fun perStatistikktype(row: Row): Pair<BehandlingsstatistikkType, Int> {
        val oppgavetype: Oppgavetype = Oppgavetype.valueOf(row.string("type"))

        return if (oppgavetype == Oppgavetype.SØKNAD) {
            BehandlingsstatistikkType.valueOf(row.string("periodetype")) to row.int("antall")
        } else {
            BehandlingsstatistikkType.valueOf(row.string("type")) to row.int("antallAvOppgaveType")
        }
    }
}

enum class BehandlingsstatistikkType {
    FØRSTEGANGSBEHANDLING,
    FORLENGELSE,
    INFOTRYGDFORLENGELSE,
    OVERGANG_FRA_IT,
    STIKKPRØVE,
    RISK_QA,
    REVURDERING,
    FORTROLIG_ADRESSE,
    UTBETALING_TIL_SYKMELDT,
    DELVIS_REFUSJON
}
