package no.nav.helse.modell

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import no.nav.helse.modell.oppgave.Oppgave
import no.nav.helse.modell.oppgave.OppgaveDao
import no.nav.helse.modell.oppgave.OppgaveMediator
import no.nav.helse.spesialist.api.abonnement.OpptegnelseDao
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus
import no.nav.helse.spesialist.api.oppgave.Oppgavetype.STIKKPRØVE
import no.nav.helse.spesialist.api.oppgave.Oppgavetype.SØKNAD
import no.nav.helse.spesialist.api.reservasjon.ReservasjonDao
import no.nav.helse.spesialist.api.tildeling.TildelingDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random.Default.nextLong

internal class OppgaveTest {
    private companion object {
        private val OPPGAVETYPE = SØKNAD
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private val UTBETALING_ID = UUID.randomUUID()
        private val COMMAND_CONTEXT_ID = UUID.randomUUID()
        private val HENDELSE_ID = UUID.randomUUID()
        private const val SAKSBEHANDLERIDENT = "Z999999"
        private val SAKSBEHANDLEROID = UUID.randomUUID()
        private val OPPGAVE_ID = nextLong()
    }

    private val oppgaveDao = mockk<OppgaveDao>(relaxed = true)
    private val vedtakDao = mockk<VedtakDao>()
    private val tildelingDao = mockk<TildelingDao>(relaxed = true)
    private val reservasjonDao = mockk<ReservasjonDao>(relaxed = true)
    private val opptegnelseDao = mockk<OpptegnelseDao>(relaxed = true)
    private val oppgaveMediator =
        OppgaveMediator(oppgaveDao, tildelingDao, reservasjonDao, opptegnelseDao)

    private val oppgave = Oppgave.oppgaveMedEgenskaper(OPPGAVE_ID, VEDTAKSPERIODE_ID, UTBETALING_ID, listOf(SØKNAD))

    @BeforeEach
    fun setup() {
        clearMocks(oppgaveDao, vedtakDao, tildelingDao)
    }

    @Test
    fun `oppretter ny oppgave`() {
        oppgave.lagre(oppgaveMediator, COMMAND_CONTEXT_ID, HENDELSE_ID)
        verify(exactly = 1) { oppgaveDao.opprettOppgave(OPPGAVE_ID, COMMAND_CONTEXT_ID, OPPGAVETYPE, VEDTAKSPERIODE_ID, UTBETALING_ID) }
    }

    @Test
    fun `oppdater oppgave`() {
        val oppgave = Oppgave(
            OPPGAVE_ID,
            OPPGAVETYPE,
            Oppgavestatus.AvventerSaksbehandler,
            VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID
        )
        oppgave.ferdigstill(SAKSBEHANDLERIDENT, SAKSBEHANDLEROID)
        oppgave.oppdater(oppgaveMediator)
        verify(exactly = 1) { oppgaveDao.updateOppgave(OPPGAVE_ID, Oppgavestatus.Ferdigstilt, SAKSBEHANDLERIDENT, SAKSBEHANDLEROID) }
    }

    @Test
    fun `gjenoppretter oppgave`() {
        val oppgave1 = Oppgave(
            OPPGAVE_ID,
            OPPGAVETYPE,
            Oppgavestatus.AvventerSaksbehandler,
            VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID
        )
        val oppgave2 = Oppgave(
            OPPGAVE_ID,
            OPPGAVETYPE,
            Oppgavestatus.AvventerSaksbehandler,
            VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID
        )
        val oppgave3 = Oppgave(
            OPPGAVE_ID,
            OPPGAVETYPE,
            Oppgavestatus.AvventerSystem,
            VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID
        )
        assertEquals(oppgave1, oppgave2)
        assertEquals(oppgave1, oppgave3)
    }

    @Test
    fun `Setter oppgavestatus til INVALIDERT når oppgaven avbrytes`() {
        val oppgave = Oppgave(
            OPPGAVE_ID,
            OPPGAVETYPE,
            Oppgavestatus.AvventerSaksbehandler,
            VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID
        )
        oppgave.avbryt()
        oppgave.oppdater(oppgaveMediator)
        verify(exactly = 1) { oppgaveDao.updateOppgave(OPPGAVE_ID, Oppgavestatus.Invalidert, null, null) }
    }

    @Test
    fun equals() {
        val gjenopptattOppgave = Oppgave(
            1L,
            OPPGAVETYPE,
            Oppgavestatus.AvventerSaksbehandler,
            VEDTAKSPERIODE_ID,
            utbetalingId = UTBETALING_ID
        )
        val oppgave1 = Oppgave.oppgaveMedEgenskaper(OPPGAVE_ID, VEDTAKSPERIODE_ID, UTBETALING_ID, listOf(SØKNAD))
        val oppgave2 = Oppgave.oppgaveMedEgenskaper(OPPGAVE_ID, VEDTAKSPERIODE_ID, UTBETALING_ID, listOf(SØKNAD))
        val oppgave3 = Oppgave.oppgaveMedEgenskaper(OPPGAVE_ID, UUID.randomUUID(), UTBETALING_ID, listOf(SØKNAD))
        val oppgave4 = Oppgave.oppgaveMedEgenskaper(OPPGAVE_ID, VEDTAKSPERIODE_ID, UTBETALING_ID, listOf(STIKKPRØVE))
        assertEquals(oppgave1, oppgave2)
        assertEquals(oppgave1.hashCode(), oppgave2.hashCode())
        assertNotEquals(oppgave1, oppgave3)
        assertNotEquals(oppgave1.hashCode(), oppgave3.hashCode())
        assertNotEquals(oppgave1, oppgave4)
        assertNotEquals(oppgave1.hashCode(), oppgave4.hashCode())

        gjenopptattOppgave.ferdigstill(SAKSBEHANDLERIDENT, SAKSBEHANDLEROID)
        assertNotEquals(gjenopptattOppgave.hashCode(), oppgave2.hashCode())
        assertNotEquals(gjenopptattOppgave, oppgave2)
        assertNotEquals(gjenopptattOppgave, oppgave3)
        assertNotEquals(gjenopptattOppgave, oppgave4)

        oppgave2.ferdigstill("ANNEN_SAKSBEHANDLER", UUID.randomUUID())
        assertEquals(oppgave1, oppgave2)
        assertEquals(oppgave1.hashCode(), oppgave2.hashCode())
    }
}
