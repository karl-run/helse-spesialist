package no.nav.helse.spesialist.api.oppgave

import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.HelseDao
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import no.nav.helse.spesialist.api.person.Adressebeskyttelse
import no.nav.helse.spesialist.api.person.Kjønn
import no.nav.helse.spesialist.api.person.PersoninfoApiDto
import no.nav.helse.spesialist.api.tildeling.TildelingApiDto
import no.nav.helse.spesialist.api.vedtaksperiode.EnhetDto
import no.nav.helse.spesialist.api.vedtaksperiode.Inntektskilde
import no.nav.helse.spesialist.api.vedtaksperiode.Periodetype
import org.intellij.lang.annotations.Language

class OppgaveApiDao(private val dataSource: DataSource) : HelseDao(dataSource) {

    fun finnPeriodeoppgave(vedtaksperiodeId: UUID): OppgaveForPeriodevisningDto? {
        @Language("PostgreSQL")
        val query = """
            SELECT id, er_beslutter_oppgave, er_retur_oppgave, totrinnsvurdering, tidligere_saksbehandler_oid
            FROM oppgave
            WHERE vedtak_ref = (SELECT id FROM vedtak WHERE vedtaksperiode_id = :vedtaksperiodeId)
                AND status = 'AvventerSaksbehandler'::oppgavestatus
        """.trimIndent()
        return query.single(mapOf("vedtaksperiodeId" to vedtaksperiodeId)) {
            OppgaveForPeriodevisningDto(
                id = it.string("id"),
                erBeslutter = it.boolean("er_beslutter_oppgave"),
                erRetur = it.boolean("er_retur_oppgave"),
                trengerTotrinnsvurdering = it.boolean("totrinnsvurdering"),
                tidligereSaksbehandler = it.stringOrNull("tidligere_saksbehandler_oid"),
            )
        }
    }

    fun finnOppgaver(saksbehandlerTilganger: SaksbehandlerTilganger) =
        sessionOf(dataSource).use { session ->
            val eventuellEkskluderingAvRiskQA =
                if (saksbehandlerTilganger.harTilgangTilRiskOppgaver()) "" else "AND o.type != 'RISK_QA'"
            val gyldigeAdressebeskyttelser =
                if (saksbehandlerTilganger.harTilgangTilKode7()) "AND pi.adressebeskyttelse IN ('Ugradert', 'Fortrolig')"
                else "AND pi.adressebeskyttelse = 'Ugradert'"
            val eventuellEkskluderingAvBeslutterOppgaver =
                if (saksbehandlerTilganger.harTilgangTilBeslutterOppgaver()) "" else "AND o.er_beslutter_oppgave = false"
            // bruk av const direkte i @Language-annotert sql fører til snodige fantom-compile-feil i IntelliJ
            val beslutterOppgaveHackyWorkaround = BESLUTTEROPPGAVE_PREFIX

            @Language("PostgreSQL")
            val query = """
            SELECT o.id as oppgave_id, o.type AS oppgavetype, o.opprettet, o.er_beslutter_oppgave, o.er_retur_oppgave, o.totrinnsvurdering, o.tidligere_saksbehandler_oid , s.epost, s.navn as saksbehandler_navn, s.oid, v.vedtaksperiode_id, v.fom, v.tom, pi.fornavn, pi.mellomnavn, pi.etternavn, pi.fodselsdato,
                   pi.kjonn, pi.adressebeskyttelse, p.aktor_id, p.fodselsnummer, sot.type as saksbehandleroppgavetype, sot.inntektskilde, e.id AS enhet_id, e.navn AS enhet_navn, t.på_vent,
                   (SELECT COUNT(DISTINCT melding) from warning w where w.melding not like '$beslutterOppgaveHackyWorkaround%' and w.vedtak_ref = o.vedtak_ref and (w.inaktiv_fra is null or w.inaktiv_fra > now())) AS antall_varsler
            FROM oppgave o
                INNER JOIN vedtak v ON o.vedtak_ref = v.id
                INNER JOIN person p ON v.person_ref = p.id
                INNER JOIN person_info pi ON p.info_ref = pi.id
                LEFT JOIN enhet e ON p.enhet_ref = e.id
                LEFT JOIN saksbehandleroppgavetype sot ON v.id = sot.vedtak_ref
                LEFT JOIN tildeling t ON o.id = t.oppgave_id_ref
                LEFT JOIN saksbehandler s on t.saksbehandler_ref = s.oid
            WHERE status = 'AvventerSaksbehandler'::oppgavestatus
                $eventuellEkskluderingAvRiskQA
                $gyldigeAdressebeskyttelser
                $eventuellEkskluderingAvBeslutterOppgaver
            ORDER BY
                CASE WHEN t.saksbehandler_ref IS NOT NULL THEN 0 ELSE 1 END,
                CASE WHEN o.type = 'STIKKPRØVE' THEN 0 ELSE 1 END,
                CASE WHEN o.type = 'RISK_QA' THEN 0 ELSE 1 END,
                opprettet ASC
                ;
            """
            session.run(
                queryOf(query)
                    .map(::saksbehandleroppgaveDto)
                    .asList
            )
        }

    fun hentFerdigstilteOppgaver(behandletAvIdent: String, fom: LocalDate?): List<FerdigstiltOppgaveDto> {
        return queryize("""
            SELECT o.id                                                     as oppgave_id,
                   o.type                                                   as oppgavetype,
                   o.oppdatert                                              as ferdigstilt_tidspunkt,
                   pi.fornavn                                               as soker_fornavn,
                   pi.mellomnavn                                            as soker_mellomnavn,
                   pi.etternavn                                             as soker_etternavn,
                   p.aktor_id                                               as soker_aktor_id,
                   sot.type                                                 as periodetype,
                   sot.inntektskilde                                        as inntektstype,
                   e.navn                                                   as bosted,
                   (SELECT count(distinct melding)
                    FROM warning w
                    WHERE w.vedtak_ref = o.vedtak_ref
                      AND (w.inaktiv_fra is null OR w.inaktiv_fra > now())) as antall_varsler
            FROM oppgave o
                     INNER JOIN vedtak v ON o.vedtak_ref = v.id
                     INNER JOIN person p ON v.person_ref = p.id
                     INNER JOIN person_info pi ON p.info_ref = pi.id
                     LEFT JOIN enhet e ON p.enhet_ref = e.id
                     LEFT JOIN saksbehandleroppgavetype sot ON v.id = sot.vedtak_ref
                     LEFT JOIN tildeling t ON o.id = t.oppgave_id_ref
                     LEFT JOIN saksbehandler s on t.saksbehandler_ref = s.oid
            WHERE o.status = 'Ferdigstilt'
              AND o.oppdatert >= :fom
              AND s.ident = :ident
            ORDER BY o.oppdatert;
        """.trimIndent()).list(mapOf("ident" to behandletAvIdent, "fom" to (fom ?: LocalDate.now()))) {
            FerdigstiltOppgaveDto(
                id = it.string("oppgave_id"),
                type = Oppgavetype.valueOf(it.string("oppgavetype")),
                ferdigstiltTidspunkt = it.localDateTime("ferdigstilt_tidspunkt"),
                personinfo = Personnavn(
                    fornavn = it.string("soker_fornavn"),
                    mellomnavn = it.stringOrNull("soker_mellomnavn"),
                    etternavn = it.string("soker_etternavn"),
                ),
                aktørId = it.string("soker_aktor_id"),
                antallVarsler = it.int("antall_varsler"),
                periodetype = Periodetype.valueOf(it.string("periodetype")),
                inntektskilde = Inntektskilde.valueOf(it.string("inntektstype")),
                bosted = it.string("bosted"),
            )
        }
    }


    companion object {
        private fun Long.toFødselsnummer() = if (this < 10000000000) "0$this" else this.toString()

        internal fun saksbehandleroppgaveDto(it: Row) = OppgaveForOversiktsvisningDto(
            oppgavereferanse = it.string("oppgave_id"),
            oppgavetype = it.string("oppgavetype"),
            opprettet = it.localDateTime("opprettet"),
            vedtaksperiodeId = UUID.fromString(it.string("vedtaksperiode_id")),
            personinfo = PersoninfoApiDto(
                it.string("fornavn"),
                it.stringOrNull("mellomnavn"),
                it.string("etternavn"),
                it.localDateOrNull("fodselsdato"),
                it.stringOrNull("kjonn")?.let(Kjønn::valueOf),
                it.string("adressebeskyttelse").let(Adressebeskyttelse::valueOf)
            ),
            aktørId = it.long("aktor_id").toString(),
            fødselsnummer = it.long("fodselsnummer").toFødselsnummer(),
            antallVarsler = it.int("antall_varsler"),
            type = it.stringOrNull("saksbehandleroppgavetype")?.let(Periodetype::valueOf),
            inntektskilde = it.stringOrNull("inntektskilde")?.let(Inntektskilde::valueOf),
            boenhet = EnhetDto(it.string("enhet_id"), it.string("enhet_navn")),
            tildeling = it.stringOrNull("epost")?.let { epost ->
                TildelingApiDto(
                    navn = it.string("saksbehandler_navn"),
                    epost = epost,
                    oid = UUID.fromString(it.string("oid")),
                    påVent = it.boolean("på_vent")
                )
            },
            erBeslutterOppgave = it.boolean("er_beslutter_oppgave"),
            erReturOppgave = it.boolean("er_retur_oppgave"),
            trengerTotrinnsvurdering = it.boolean("totrinnsvurdering"),
            tidligereSaksbehandlerOid = it.uuidOrNull("tidligere_saksbehandler_oid")
        )
    }
}

const val BESLUTTEROPPGAVE_PREFIX = "Beslutteroppgave:"
