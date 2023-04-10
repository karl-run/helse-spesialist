package no.nav.helse.mediator.api

import ToggleHelpers.disable
import ToggleHelpers.enable
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime.now
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.helse.mediator.HendelseMediator
import no.nav.helse.mediator.Toggle
import no.nav.helse.modell.oppgave.OppgaveDao
import no.nav.helse.modell.tildeling.TildelingService
import no.nav.helse.modell.totrinnsvurdering.Totrinnsvurdering
import no.nav.helse.modell.totrinnsvurdering.TotrinnsvurderingMediator
import no.nav.helse.objectMapper
import no.nav.helse.spesialist.api.graphql.schema.NotatType
import no.nav.helse.spesialist.api.notat.NotatMediator
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkDao
import no.nav.helse.spesialist.api.periodehistorikk.PeriodehistorikkType
import no.nav.helse.spesialist.api.varsel.ApiVarselRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TotrinnsvurderingApiTest : AbstractApiTest() {

    private val varselRepository = mockk<ApiVarselRepository>(relaxed = true)
    private val oppgaveDao = mockk<OppgaveDao>(relaxed = true)
    private val periodehistorikkDao = mockk<PeriodehistorikkDao>(relaxed = true)
    private val notatMediator = mockk<NotatMediator>(relaxed = true)
    private val tildelingService = mockk<TildelingService>(relaxed = true)
    private val hendelseMediator = mockk<HendelseMediator>(relaxed = true)
    private val totrinnsvurderingMediator = mockk<TotrinnsvurderingMediator>(relaxed = true)

    private val saksbehandler_oid = UUID.randomUUID()

    private val totrinnsvurderingDto = TotrinnsvurderingDto(oppgavereferanse = 1L)
    private val returDtoMedNotat = TotrinnsvurderingReturDto(
        oppgavereferanse = 1L,
        notat = NotatApiDto("notat_tekst", NotatType.Retur)
    )

    private val TOTRINNSVURDERING_URL = "/api/totrinnsvurdering"
    private val RETUR_URL = "/api/totrinnsvurdering/retur"

    @BeforeAll
    fun setupTotrinnsvurdering() {
        setupServer {
            totrinnsvurderingApi(
                varselRepository,
                oppgaveDao,
                notatMediator,
                tildelingService,
                hendelseMediator,
                totrinnsvurderingMediator
            )
        }
    }

    @BeforeEach
    fun setup() {
        clearMocks(oppgaveDao, periodehistorikkDao, notatMediator, tildelingService, hendelseMediator)
    }

    @Test
    fun `Kan ikke gjøres til beslutteroppgave hvis den allerede er beslutteroppgave`() {
        every { oppgaveDao.erBeslutteroppgave(1L) } returns true
        val response = runBlocking {
            client.post("/api/totrinnsvurdering") {
                contentType(ContentType.Application.Json)
                setBody<JsonNode>(objectMapper.valueToTree(totrinnsvurderingDto))
                authentication(saksbehandler_oid)
            }
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `en vedtaksperiode kan godkjennes hvis alle varsler er vurdert`() {
        every { oppgaveDao.venterPåSaksbehandler(1L) } returns true
        every { oppgaveDao.erRiskoppgave(1L) } returns false
        every { varselRepository.ikkeVurderteVarslerFor(1L) } returns 0
        every { totrinnsvurderingMediator.hentAktiv(oppgaveId = any()) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = true,
            saksbehandler = UUID.randomUUID(),
            beslutter = UUID.randomUUID(),
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )
        val response = runBlocking {
            client.post("/api/totrinnsvurdering") {
                contentType(ContentType.Application.Json)
                setBody<JsonNode>(objectMapper.valueToTree(totrinnsvurderingDto))
                authentication(saksbehandler_oid)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `en vedtaksperiode kan ikke godkjennes hvis det fins aktive varsler`() {
        every { oppgaveDao.venterPåSaksbehandler(1L) } returns true
        every { oppgaveDao.erRiskoppgave(1L) } returns false
        every { varselRepository.ikkeVurderteVarslerFor(1L) } returns 1
        val response = runBlocking {
            client.post("/api/totrinnsvurdering") {
                contentType(ContentType.Application.Json)
                setBody<JsonNode>(objectMapper.valueToTree(totrinnsvurderingDto))
                authentication(saksbehandler_oid)
            }
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun totrinnsvurderingOk() {
        every { totrinnsvurderingMediator.hentAktiv(oppgaveId = any()) } returns null
        val response = runBlocking {
            client.post(TOTRINNSVURDERING_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(totrinnsvurderingDto)
                authentication(saksbehandler_oid)
            }
        }

        verify(exactly = 1) {
            oppgaveDao.setBeslutteroppgave(
                oppgaveId = totrinnsvurderingDto.oppgavereferanse,
                tidligereSaksbehandlerOID = saksbehandler_oid
            )
        }
        verify(exactly = 1) {
            tildelingService.fjernTildelingOgTildelNySaksbehandlerHvisFinnes(
                totrinnsvurderingDto.oppgavereferanse,
                any(),
                any()
            )
        }
        verify(exactly = 1) {
            totrinnsvurderingMediator.lagrePeriodehistorikk(
                totrinnsvurderingDto.oppgavereferanse,
                saksbehandler_oid,
                PeriodehistorikkType.TOTRINNSVURDERING_TIL_GODKJENNING
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }


    @Test
    fun returOk() {
        Toggle.Totrinnsvurdering.disable()
        val response = runBlocking {
            client.post(RETUR_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(returDtoMedNotat)
                authentication(saksbehandler_oid)
            }
        }

        verify(exactly = 1) {
            oppgaveDao.setReturoppgave(
                oppgaveId = returDtoMedNotat.oppgavereferanse,
                beslutterSaksbehandlerOid = saksbehandler_oid
            )
        }
        verify(exactly = 1) {
            tildelingService.fjernTildelingOgTildelNySaksbehandlerHvisFinnes(
                returDtoMedNotat.oppgavereferanse,
                any(),
                any()
            )
        }
        verify(exactly = 1) {
            notatMediator.lagreForOppgaveId(
                returDtoMedNotat.oppgavereferanse,
                returDtoMedNotat.notat.tekst,
                saksbehandler_oid,
                returDtoMedNotat.notat.type
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        Toggle.Totrinnsvurdering.enable()
    }

    @Test
    fun totrinnsvurderingManglerPeriodeId() {
        val response = runBlocking {
            client.post(TOTRINNSVURDERING_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(objectMapper.writeValueAsString("{oppgavereferanse: 1L}"))
                authentication(saksbehandler_oid)
            }
        }

        verify(exactly = 0) { oppgaveDao.setBeslutteroppgave(any(), any()) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun returManglerPeriodeId() {
        val response = runBlocking {
            client.post(RETUR_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(objectMapper.writeValueAsString("{oppgavereferanse: 1L}"))
                authentication(saksbehandler_oid)
            }
        }

        verify(exactly = 0) { oppgaveDao.setBeslutteroppgave(any(), any()) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun totrinnsvurderingManglerAccessToken() {
        val response = runBlocking {
            client.post(TOTRINNSVURDERING_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(totrinnsvurderingDto)
            }
        }

        verify(exactly = 0) { oppgaveDao.setBeslutteroppgave(any(), any()) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun returManglerAccessToken() {
        val response = runBlocking {
            client.post(RETUR_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(totrinnsvurderingDto)
            }
        }

        verify(exactly = 0) { oppgaveDao.setBeslutteroppgave(any(), any()) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `Totrinnsvurdering kan ikke gjøres til beslutteroppgave hvis den allerede er beslutteroppgave`() {
        every { totrinnsvurderingMediator.hentAktiv(1L) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = false,
            saksbehandler = UUID.randomUUID(),
            beslutter = UUID.randomUUID(),
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )

        val response = runBlocking {
            client.post("/api/totrinnsvurdering") {
                contentType(ContentType.Application.Json)
                setBody<JsonNode>(objectMapper.valueToTree(totrinnsvurderingDto))
                authentication(saksbehandler_oid)
            }
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `Totrinnsvurdering kan gjøres til beslutteroppgave hvis den er en returoppgave`() {
        every { oppgaveDao.erBeslutteroppgave(1L) } returns false
        every { totrinnsvurderingMediator.hentAktiv(oppgaveId = any()) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = true,
            saksbehandler = UUID.randomUUID(),
            beslutter = UUID.randomUUID(),
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )

        val response = runBlocking {
            client.post("/api/totrinnsvurdering") {
                contentType(ContentType.Application.Json)
                setBody<JsonNode>(objectMapper.valueToTree(totrinnsvurderingDto))
                authentication(saksbehandler_oid)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `Oppretter totrinnsvurdering dersom den kun finnes i legacyløsning`() {
        every { totrinnsvurderingMediator.hentAktiv(1L) } returns null
        every { totrinnsvurderingMediator.opprettFraLegacy(1L) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = false,
            saksbehandler = null,
            beslutter = null,
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )

        val response = runBlocking {
            client.post("/api/totrinnsvurdering") {
                contentType(ContentType.Application.Json)
                setBody<JsonNode>(objectMapper.valueToTree(totrinnsvurderingDto))
                authentication(saksbehandler_oid)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `Oppretter totrinnsvurdering dersom den kun finnes i legacyløsning ved retur`() {
        every { totrinnsvurderingMediator.hentAktiv(1L) } returns null
        every { totrinnsvurderingMediator.opprettFraLegacy(1L) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = false,
            saksbehandler = saksbehandler_oid,
            beslutter = null,
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )

        val response = runBlocking {
            client.post(RETUR_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(TotrinnsvurderingReturDto(
                    oppgavereferanse = 1,
                    notat = NotatApiDto("tekst", NotatType.Retur)
                ))
                authentication(saksbehandler_oid)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `Setter saksbehandler for totrinnsvurdering når oppgave blir sendt til godkjenning`() {
        every { totrinnsvurderingMediator.hentAktiv(10L) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = false,
            saksbehandler = null,
            beslutter = null,
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )
        every { varselRepository.ikkeVurderteVarslerFor(10L) } returns 0
        // TODO hvorfor kjører testen grønt uansett hva finnBeslutterSaksbehandler returnerer?
        every { oppgaveDao.finnBeslutterSaksbehandler(10L) } returns null

        val response = runBlocking {
            client.post(TOTRINNSVURDERING_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(TotrinnsvurderingDto(10L))
                authentication(saksbehandler_oid)
            }
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { totrinnsvurderingMediator.settSaksbehandler(10L, saksbehandler_oid) }
    }

    @Test
    fun `Tildeler oppgaven til beslutter dersom den finnes ved returnert retur`() {
        val beslutterSaksbehandlerOid = UUID.randomUUID()
        every { totrinnsvurderingMediator.hentAktiv(10L) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = true,
            saksbehandler = UUID.randomUUID(),
            beslutter = beslutterSaksbehandlerOid,
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )
        every { oppgaveDao.finnBeslutterSaksbehandler(10L) } returns null

        val response = runBlocking {
            client.post(TOTRINNSVURDERING_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(TotrinnsvurderingDto(10L))
                authentication(saksbehandler_oid)
            }
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) {
            tildelingService.fjernTildelingOgTildelNySaksbehandlerHvisFinnes(
                10L,
                beslutterSaksbehandlerOid,
                any()
            )
        }
    }

    @Test
    fun `Setter totrinnsvurdering til retur false ved returnert retur`() {
        every { totrinnsvurderingMediator.hentAktiv(10L) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = true,
            saksbehandler = UUID.randomUUID(),
            beslutter = UUID.randomUUID(),
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )
        // TODO hvorfor kjører testen grønt uansett hva finnBeslutterSaksbehandler returnerer?
        every { oppgaveDao.finnBeslutterSaksbehandler(10L) } returns null

        val response = runBlocking {
            client.post(TOTRINNSVURDERING_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(TotrinnsvurderingDto(10L))
                authentication(saksbehandler_oid)
            }
        }

        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) {
            totrinnsvurderingMediator.settHåndtertRetur(10L)
        }
    }

    @Test
    fun `Sende totrinnsvurdering i retur`() {
        Toggle.Totrinnsvurdering.enable()
        val tidligereSaksbehandlerOid = UUID.randomUUID()
        val oppgaveId = 2L
        every { totrinnsvurderingMediator.hentAktiv(oppgaveId = oppgaveId) } returns Totrinnsvurdering(
            vedtaksperiodeId = UUID.randomUUID(),
            erRetur = true,
            saksbehandler = tidligereSaksbehandlerOid,
            beslutter = null,
            utbetalingIdRef = null,
            oppdatert = now(),
            opprettet = now()
        )
        every { oppgaveDao.finnTidligereSaksbehandler(any()) } returns null

        val notat = "notat_tekst"
        val response = runBlocking {
            client.post(RETUR_URL) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(TotrinnsvurderingReturDto(
                    oppgavereferanse = oppgaveId,
                    notat = NotatApiDto(notat, NotatType.Retur)
                ))
                authentication(saksbehandler_oid)
            }
        }

        verify(exactly = 1) { totrinnsvurderingMediator.settRetur(oppgaveId, saksbehandler_oid, notat) }

        verify(exactly = 1) {
            tildelingService.fjernTildelingOgTildelNySaksbehandlerHvisFinnes(
                oppgaveId,
                tidligereSaksbehandlerOid,
                any()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        Toggle.Totrinnsvurdering.disable()
    }
}
