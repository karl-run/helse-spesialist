package no.nav.helse.e2e

import AbstractE2ETest
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus.Ferdigstilt
import org.junit.jupiter.api.Test

internal class VedtaksperiodeForkastetE2ETest : AbstractE2ETest() {

    @Test
    fun `VedtaksperiodeForkastet oppdaterer ikke oppgave-tabellen dersom status er inaktiv`() {
        fremTilSaksbehandleroppgave()
        håndterSaksbehandlerløsning()
        håndterVedtakFattet()

        assertSaksbehandleroppgave(oppgavestatus = Ferdigstilt)
        håndterVedtaksperiodeForkastet()
        assertSaksbehandleroppgave(oppgavestatus = Ferdigstilt)
        assertVedtaksperiodeForkastet(testperson.vedtaksperiodeId1)
    }

    @Test
    fun `VedtaksperiodeForkastet medfører at perioden blir markert som forkastet`() {
        fremTilSaksbehandleroppgave()
        håndterSaksbehandlerløsning()
        håndterVedtakFattet()

        håndterVedtaksperiodeForkastet()
        assertVedtaksperiodeForkastet(testperson.vedtaksperiodeId1)
    }
}
