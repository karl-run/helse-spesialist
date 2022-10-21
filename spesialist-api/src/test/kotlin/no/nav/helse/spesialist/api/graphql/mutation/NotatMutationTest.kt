package no.nav.helse.spesialist.api.graphql.mutation

import no.nav.helse.spesialist.api.AbstractGraphQLApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class NotatMutationTest : AbstractGraphQLApiTest() {

    @BeforeAll
    fun setup() {
        setupGraphQLServer()
    }

    @Test
    fun `feilregistrerer notat`() {
        opprettSaksbehandler()
        opprettVedtaksperiode(opprettPerson(), opprettArbeidsgiver())
        val notatId = opprettNotat()

        val body = runQuery(
            """
            mutation FeilregistrerNotat {
                feilregistrerNotat(id: $notatId)
            }
        """
        )

        assertTrue(body["data"]["feilregistrerNotat"].asBoolean())
    }

    @Test
    fun `feilregistrerer kommentar`() {
        opprettSaksbehandler()
        opprettVedtaksperiode(opprettPerson(), opprettArbeidsgiver())
        val notatId = opprettNotat()!!
        val kommentarId = opprettKommentar(notatRef = notatId.toInt())

        val body = runQuery(
            """
            mutation FeilregistrerKommentar {
                feilregistrerKommentar(id: $kommentarId)
            }
        """
        )

        assertTrue(body["data"]["feilregistrerKommentar"].asBoolean())
    }

    @Test
    fun `legger til notat`() {
        opprettSaksbehandler()
        opprettVedtaksperiode(opprettPerson(), opprettArbeidsgiver())

        val antallNyeNotater = runQuery(
            """
            mutation LeggTilNotat {
                leggTilNotat(
                    tekst: "Dette er et notat",
                    type: Generelt,
                    vedtaksperiodeId: "${PERIODE.id}",
                    saksbehandlerOid: "${SAKSBEHANDLER.oid}"
                )
            }
        """
        )["data"]["leggTilNotat"].asInt()

        assertEquals(1, antallNyeNotater)
    }

    @Test
    fun `legger til ny kommentar`() {
        opprettSaksbehandler()
        opprettVedtaksperiode(opprettPerson(), opprettArbeidsgiver())
        val notatId = opprettNotat()

        val body = runQuery(
            """
            mutation LeggTilKommentar {
                leggTilKommentar(notatId: $notatId, tekst: "En kommentar", saksbehandlerident: "${SAKSBEHANDLER.ident}") {
                    tekst
                }
            }
        """
        )

        assertEquals("En kommentar", body["data"]["leggTilKommentar"]["tekst"].asText())
    }

    @Test
    fun `får 404-feil ved oppretting av kommentar dersom notat ikke finnes`() {
        opprettSaksbehandler()
        opprettVedtaksperiode(opprettPerson(), opprettArbeidsgiver())

        val body = runQuery(
            """
            mutation LeggTilKommentar {
                leggTilKommentar(notatId: 1, tekst: "En kommentar", saksbehandlerident: "${SAKSBEHANDLER.ident}") {
                    id
                }
            }
        """
        )

        assertEquals(404, body["errors"].first()["extensions"]["code"].asInt())
    }

}