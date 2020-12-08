package no.nav.helse.mediator.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.mediator.AbonnementMediator
import no.nav.helse.modell.abonnement.OpptegnelseDto
import no.nav.helse.modell.abonnement.OpptegnelseType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbonnementApiTest : AbstractApiTest() {

    private val oppdateringMediator = mockk<AbonnementMediator>(relaxed = true)
    private val SAKSBEHANDLER_ID = UUID.randomUUID()
    private val AKTØR_ID = 12341234L

    @BeforeAll
    fun setupTildeling() {
        setupServer {
            abonnementApi(oppdateringMediator)
        }
    }

    @Test
    fun oppdateringOk() {
        val response = runBlocking {
            client.post<HttpResponse>("/api/abonner/$AKTØR_ID") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                authentication(SAKSBEHANDLER_ID)
            }
        }
        assertTrue(response.status.isSuccess(), "HTTP response burde returnere en OK verdi, fikk ${response.status}")
    }

    @Test
    fun `Kan hente nye oppdateringer for abonnement`() {
        val oppdatering1 = OpptegnelseDto(
            aktørId=11,
            sekvensnummer = 0,
            type=OpptegnelseType.ANNULLERING_FEILET,
            payload = """{ "test": "1" }""")
        val oppdatering2 = OpptegnelseDto(
            aktørId=12,
            sekvensnummer = 1,
            type=OpptegnelseType.ANNULLERING_OK,
            payload = """{ "test": "2" }""")
        val expected = listOf(oppdatering1, oppdatering2)

        every { oppdateringMediator.hentOpptegnelser(any(), any()) } returns expected

        val response = runBlocking {
            client.get<HttpResponse>("/api/oppdatering/123") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                authentication(SAKSBEHANDLER_ID)
            }
        }
        assertTrue(response.status.isSuccess(), "HTTP response burde returnere en OK verdi, fikk ${response.status}")

        val actual = runBlocking { response.receive<List<OpptegnelseDto>>() }
        assertEquals(expected, actual)
    }

}
