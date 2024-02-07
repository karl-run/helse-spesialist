package no.nav.helse.e2e

import AbstractE2ETest
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.TestRapidHelpers.siste
import no.nav.helse.Testdata.ORGNR
import no.nav.helse.Testdata.SAKSBEHANDLERTILGANGER_UTEN_TILGANGER
import no.nav.helse.Testdata.UTBETALING_ID
import no.nav.helse.januar
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import no.nav.helse.spesialist.api.arbeidsgiver.ArbeidsgiverApiDao
import no.nav.helse.spesialist.api.egenAnsatt.EgenAnsattApiDao
import no.nav.helse.spesialist.api.graphql.query.PersonQuery
import no.nav.helse.spesialist.api.graphql.schema.Arbeidsforholdoverstyring
import no.nav.helse.spesialist.api.graphql.schema.Dagoverstyring
import no.nav.helse.spesialist.api.graphql.schema.Inntektoverstyring
import no.nav.helse.spesialist.api.graphql.schema.Person
import no.nav.helse.spesialist.api.notat.NotatDao
import no.nav.helse.spesialist.api.oppgave.OppgaveApiDao
import no.nav.helse.spesialist.api.overstyring.OverstyringApiDao
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkDao
import no.nav.helse.spesialist.api.person.PersonApiDao
import no.nav.helse.spesialist.api.påvent.PåVentApiDao
import no.nav.helse.spesialist.api.risikovurdering.RisikovurderingApiDao
import no.nav.helse.spesialist.api.saksbehandler.handlinger.LovhjemmelFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrArbeidsforholdHandlingFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrInntektOgRefusjonHandlingFraApi.OverstyrArbeidsgiverFraApi
import no.nav.helse.spesialist.api.saksbehandler.handlinger.OverstyrTidslinjeHandlingFraApi
import no.nav.helse.spesialist.api.snapshot.SnapshotApiDao
import no.nav.helse.spesialist.api.snapshot.SnapshotMediator
import no.nav.helse.spesialist.api.tildeling.TildelingDao
import no.nav.helse.spesialist.api.totrinnsvurdering.TotrinnsvurderingApiDao
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class OverstyringE2ETest : AbstractE2ETest() {

    @Test
    fun `saksbehandler overstyrer sykdomstidslinje`() {
        fremTilSaksbehandleroppgave()
        håndterOverstyrTidslinje(dager = listOf(
            OverstyrTidslinjeHandlingFraApi.OverstyrDagFraApi(
                dato = 20.januar,
                type = "Feriedag",
                fraType = "Sykedag",
                grad = null,
                fraGrad = 100,
                lovhjemmel = null
            )
        ))
        assertOverstyrTidslinje(FØDSELSNUMMER, 1)

        assertOppgaver(UTBETALING_ID, "AvventerSaksbehandler", 0)

        val nyUtbetalingId = UUID.randomUUID()
        fremTilSaksbehandleroppgave(harOppdatertMetadata = true, harRisikovurdering = true, utbetalingId = nyUtbetalingId)
        assertOppgaver(nyUtbetalingId, "AvventerSaksbehandler", 1)
    }

    @Test
    fun `saksbehandler overstyrer sykdomstidslinje med referanse til lovhjemmel`() {
        fremTilSaksbehandleroppgave()
        håndterOverstyrTidslinje(dager = listOf(
            OverstyrTidslinjeHandlingFraApi.OverstyrDagFraApi(
                dato = 20.januar,
                type = "Feriedag",
                fraType = "Sykedag",
                grad = null,
                fraGrad = 100,
                lovhjemmel = LovhjemmelFraApi(paragraf = "EN PARAGRAF", ledd = "ET LEDD", bokstav = "EN BOKSTAV", lovverk = "folketrygdloven", lovverksversjon = "1970-01-01")
            )
        ))
        assertOverstyrTidslinje(FØDSELSNUMMER, 1)
        val subsumsjon = inspektør.siste("subsumsjon").path("subsumsjon")

        assertNotNull(subsumsjon["sporing"]["overstyrtidslinje"])
    }

    @Test
    fun `saksbehandler overstyrer inntekt og refusjon`() {
        fremTilSaksbehandleroppgave()
        håndterOverstyrInntektOgRefusjon(
            arbeidsgivere = listOf(
                OverstyrArbeidsgiverFraApi(
                    organisasjonsnummer = ORGNR,
                    månedligInntekt = 25000.0,
                    fraMånedligInntekt = 25001.0,
                    forklaring = "testbortforklaring",
                    lovhjemmel = LovhjemmelFraApi("8-28", "LEDD_1", "BOKSTAV_A", "folketrygdloven", "1970-01-01"),
                    refusjonsopplysninger = null,
                    fraRefusjonsopplysninger = null,
                    begrunnelse = "begrunnelse")
            ),
            skjæringstidspunkt = 1.januar,
        )

        assertOppgaver(UTBETALING_ID, "AvventerSaksbehandler", 0)
        assertOverstyrInntektOgRefusjon(FØDSELSNUMMER, 1)

        val nyUtbetalingId = UUID.randomUUID()
        fremTilSaksbehandleroppgave(harOppdatertMetadata = true, harRisikovurdering = true, utbetalingId = nyUtbetalingId)

        assertOppgaver(nyUtbetalingId, "AvventerSaksbehandler", 1)
        assertTildeling(SAKSBEHANDLER_EPOST, nyUtbetalingId)
    }

    @Test
    fun `saksbehandler overstyrer arbeidsforhold`() {
        fremTilSaksbehandleroppgave()
        håndterOverstyrArbeidsforhold(
            overstyrteArbeidsforhold = listOf(
                OverstyrArbeidsforholdHandlingFraApi.ArbeidsforholdFraApi(
                    orgnummer = ORGNR,
                    deaktivert = true,
                    begrunnelse = "begrunnelse",
                    forklaring = "forklaring"
                )
            )
        )
        assertOppgaver(UTBETALING_ID, "AvventerSaksbehandler", 0)
        assertOverstyrArbeidsforhold(FØDSELSNUMMER, 1)

        val nyUtbetalingId = UUID.randomUUID()
        fremTilSaksbehandleroppgave(harOppdatertMetadata = true, harRisikovurdering = true, utbetalingId = nyUtbetalingId)

        assertOppgaver(nyUtbetalingId, "AvventerSaksbehandler", 1)
        assertTildeling(SAKSBEHANDLER_EPOST, nyUtbetalingId)
    }

    @Test
    fun `legger ved overstyringer i speil snapshot`() {
        fremTilSaksbehandleroppgave()
        håndterOverstyrTidslinje()
        fremTilSaksbehandleroppgave(harOppdatertMetadata = true, harRisikovurdering = true)
        håndterOverstyrInntektOgRefusjon()
        fremTilSaksbehandleroppgave(harOppdatertMetadata = true, harRisikovurdering = true)
        håndterOverstyrArbeidsforhold()

        every { dataFetchingEnvironment.graphQlContext.get<String>("saksbehandlerNavn") } returns "saksbehandler"
        every { dataFetchingEnvironment.graphQlContext.get<SaksbehandlerTilganger>("tilganger") } returns SAKSBEHANDLERTILGANGER_UTEN_TILGANGER

        val nyUtbetalingId = UUID.randomUUID()
        fremTilSaksbehandleroppgave(harOppdatertMetadata = true, harRisikovurdering = true, utbetalingId = nyUtbetalingId)
        assertOppgaver(nyUtbetalingId, "AvventerSaksbehandler", 1)

        val snapshot: Person = runBlocking { personQuery.person(FØDSELSNUMMER, null, dataFetchingEnvironment).data!! }

        assertNotNull(snapshot)
        val overstyringer = snapshot.arbeidsgivere().first().overstyringer()
        assertEquals(3, overstyringer.size)
        assertEquals(1, (overstyringer[0] as Dagoverstyring).dager.size)
        assertEquals(25000.0, (overstyringer[1] as Inntektoverstyring).inntekt.manedligInntekt)
        assertEquals(true, (overstyringer[2] as Arbeidsforholdoverstyring).deaktivert)
        assertFalse(overstyringer.first().ferdigstilt)
        assertFalse(overstyringer[1].ferdigstilt)
        assertFalse(overstyringer.last().ferdigstilt)
    }

    private fun assertOppgaver(utbetalingId: UUID, status: String, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query = "SELECT COUNT(1) FROM oppgave o WHERE o.utbetaling_id = ? AND o.status = ?::oppgavestatus"
        val antallOppgaver = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, utbetalingId, status).map { it.int(1) }.asSingle) ?: 0
        }
        assertEquals(forventetAntall, antallOppgaver)
    }

    private fun assertOverstyrTidslinje(fødselsnummer: String, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query =
            """
                SELECT COUNT(1) FROM overstyring o 
                INNER JOIN overstyring_tidslinje ot on o.id = ot.overstyring_ref 
                WHERE o.person_ref = (SELECT id FROM person WHERE fodselsnummer = :fodselsnummer)
            """
        val antallOverstyrTidslinje = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, mapOf(
                "fodselsnummer" to fødselsnummer.toLong()
            )).map { it.int(1) }.asSingle)
        } ?: 0

        assertEquals(forventetAntall, antallOverstyrTidslinje)
    }

    private fun assertOverstyrArbeidsforhold(fødselsnummer: String, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query =
            """
                SELECT COUNT(1) FROM overstyring o
                INNER JOIN overstyring_arbeidsforhold oa on o.id = oa.overstyring_ref
                WHERE o.person_ref = (SELECT id FROM person WHERE fodselsnummer = :fodselsnummer)
            """
        val antallOverstyrArbeidsforhold = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, mapOf(
                "fodselsnummer" to fødselsnummer.toLong()
            )).map { it.int(1) }.asSingle)
        } ?: 0

        assertEquals(forventetAntall, antallOverstyrArbeidsforhold)
    }

    private fun assertOverstyrInntektOgRefusjon(fødselsnummer: String, forventetAntall: Int) {
        @Language("PostgreSQL")
        val query =
            """
                SELECT COUNT(1) FROM overstyring o
                INNER JOIN overstyring_inntekt oi on o.id = oi.overstyring_ref
                WHERE o.person_ref = (SELECT id FROM person WHERE fodselsnummer = :fodselsnummer)
            """
        val antallOverstyrInntektOgRefusjon = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, mapOf(
                "fodselsnummer" to fødselsnummer.toLong()
            )).map { it.int(1) }.asSingle)
        } ?: 0

        assertEquals(forventetAntall, antallOverstyrInntektOgRefusjon)
    }

    private fun assertTildeling(saksbehandlerEpost: String, utbetalingId: UUID) {
        @Language("PostgreSQL")
        val query =
            """
                SELECT epost FROM saksbehandler s 
                INNER JOIN tildeling t on s.oid = t.saksbehandler_ref
                INNER JOIN oppgave o on o.id = t.oppgave_id_ref
                WHERE o.utbetaling_id = ?
            """
        val tildeltEpost = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, utbetalingId).map { it.string("epost") }.asSingle)
        }
        assertEquals(saksbehandlerEpost, tildeltEpost)
    }

    private val dataFetchingEnvironment = mockk<DataFetchingEnvironment>(relaxed = true)

    private val personQuery = PersonQuery(
        personApiDao = PersonApiDao(dataSource),
        egenAnsattApiDao = EgenAnsattApiDao(dataSource),
        tildelingDao = TildelingDao(dataSource),
        arbeidsgiverApiDao = ArbeidsgiverApiDao(dataSource),
        overstyringApiDao = OverstyringApiDao(dataSource),
        risikovurderingApiDao = RisikovurderingApiDao(dataSource),
        varselRepository = ApiVarselRepository(dataSource),
        oppgaveApiDao = OppgaveApiDao(dataSource),
        periodehistorikkDao = PeriodehistorikkDao(dataSource),
        notatDao = NotatDao(dataSource),
        totrinnsvurderingApiDao = TotrinnsvurderingApiDao(dataSource),
        påVentApiDao = PåVentApiDao(dataSource),
        snapshotMediator = SnapshotMediator(SnapshotApiDao(dataSource), mockk(relaxed = true)),
        reservasjonClient = mockk(relaxed = true),
        oppgavehåndterer = mockk(relaxed = true),
        avviksvurderinghenter = mockk(relaxed = true),
    )
}
