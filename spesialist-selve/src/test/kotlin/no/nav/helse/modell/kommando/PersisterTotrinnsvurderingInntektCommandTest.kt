package no.nav.helse.modell.kommando

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import no.nav.helse.januar
import no.nav.helse.modell.automatisering.AutomatiseringDao
import no.nav.helse.oppgave.OppgaveDao
import no.nav.helse.overstyring.OverstyringType
import no.nav.helse.overstyring.OverstyrtVedtaksperiodeDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PersisterTotrinnsvurderingInntektCommandTest {
    private companion object {
        private val VEDTAKSPERIODE_ID = UUID.randomUUID()
        private const val FNR = "12345678911"
        private const val ORGNR = "123456789"
        private val SKJÆRINGSTIDSPUNKT = 1.januar
    }

    private val oppgaveDao = mockk<OppgaveDao>(relaxed = true)
    private val overstyrtVedtaksperiodeDao = mockk<OverstyrtVedtaksperiodeDao>(relaxed = true)
    private val automatiseringDao = mockk<AutomatiseringDao>(relaxed = true)

    private val context = CommandContext(UUID.randomUUID())

    @BeforeEach
    fun setup() {
        clearMocks(oppgaveDao, overstyrtVedtaksperiodeDao)
    }

    @Test
    fun `lagrer overstyrt vedtaksperiode hvis vi finner utbetalt eller aktiv vedtaksperiode for skjæringstidspunkt`() {
        every { oppgaveDao.finnNyesteUtbetalteEllerAktiveVedtaksperiodeIdForSkjæringstidspunkt(FNR, ORGNR, SKJÆRINGSTIDSPUNKT) }.returns(VEDTAKSPERIODE_ID)

        val command = PersisterTotrinnsvurderingInntektCommand(
            FNR,
            ORGNR,
            SKJÆRINGSTIDSPUNKT,
            oppgaveDao,
            overstyrtVedtaksperiodeDao,
            automatiseringDao
        )
        command.execute(context)

        verify(exactly = 1) {
            overstyrtVedtaksperiodeDao.lagreOverstyrtVedtaksperiode(VEDTAKSPERIODE_ID, OverstyringType.Inntekt)
        }
    }

    @Test
    fun `lagrer ikke overstyrt vedtaksperiode hvis vi ikke finner utbetalt eller aktiv vedtaksperiode for skjæringstidspunkt`() {
        every { oppgaveDao.finnNyesteUtbetalteEllerAktiveVedtaksperiodeIdForSkjæringstidspunkt(any(), any(), any()) }.returns(null)
        every { automatiseringDao.finnSisteAutomatiserteVedtaksperiodeId(any(), any()) }.returns(null)

        val command = PersisterTotrinnsvurderingInntektCommand(
            FNR,
            ORGNR,
            SKJÆRINGSTIDSPUNKT,
            oppgaveDao,
            overstyrtVedtaksperiodeDao,
            automatiseringDao)
        command.execute(context)

        verify(exactly = 0) {
            overstyrtVedtaksperiodeDao.lagreOverstyrtVedtaksperiode(any(), any())
        }
    }
}