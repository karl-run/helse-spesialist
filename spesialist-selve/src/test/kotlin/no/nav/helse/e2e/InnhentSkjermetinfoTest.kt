package no.nav.helse.e2e

import AbstractE2ETest
import io.mockk.every
import java.util.UUID
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


internal class InnhentSkjermetinfoTest : AbstractE2ETest() {

    @Test
    fun `Ignorerer hendelsen for ukjente personer`() {
        sendInnhentSkjermetinfo()
        assertEquals(0, testRapid.inspektør.behov().size)
    }

    @Test
    fun `Ignorerer skjermetinfo for kjente personer vi har skjermetinfo for`() {
        val godkjenningsmeldingId = sendGodkjenningsbehov(ORGNR, VEDTAKSPERIODE_ID, UUID.randomUUID())
        sendPersoninfoløsning(godkjenningsmeldingId, ORGNR, VEDTAKSPERIODE_ID)
        sendArbeidsgiverinformasjonløsning(godkjenningsmeldingId, ORGNR, VEDTAKSPERIODE_ID)
        sendArbeidsforholdløsning(godkjenningsmeldingId, ORGNR, VEDTAKSPERIODE_ID)
        sendEgenAnsattløsning(godkjenningsmeldingId, false)

        testRapid.reset()
        sendInnhentSkjermetinfo()
        assertEquals(0, testRapid.inspektør.behov().size)
    }

    @Test
    fun `Etterspør skjermetinfo for kjente personer hvor skjermetinfo mangler i basen`() {
        val godkjenningsmeldingId = sendGodkjenningsbehov(ORGNR, VEDTAKSPERIODE_ID, UUID.randomUUID())
        sendPersoninfoløsning(godkjenningsmeldingId, ORGNR, VEDTAKSPERIODE_ID)
        sendArbeidsgiverinformasjonløsning(godkjenningsmeldingId, ORGNR, VEDTAKSPERIODE_ID)
        sendArbeidsforholdløsning(godkjenningsmeldingId, ORGNR, VEDTAKSPERIODE_ID)

        testRapid.reset()
        sendInnhentSkjermetinfo()
        assertEquals(1, testRapid.inspektør.behov().size)
    }

    @BeforeEach
    fun setup() {
        every { snapshotClient.hentSnapshot(FØDSELSNUMMER) } returns snapshot()
    }

    private fun sendInnhentSkjermetinfo() {
        @Language("JSON")
        val json = """{
          "@event_name": "innhent_skjermetinfo",
          "@id": "${UUID.randomUUID()}",
          "fødselsnummer": "$FØDSELSNUMMER"
        }"""
        testRapid.sendTestMessage(json)
    }

}
