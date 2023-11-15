package no.nav.helse.modell.egenansatt

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.mediator.meldinger.løsninger.EgenAnsattløsning
import no.nav.helse.modell.kommando.CommandContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class EgenAnsattCommandTest {
    private companion object {
        private const val FNR = "12345678911"
    }

    private val dao = mockk<EgenAnsattDao>(relaxed = true)

    private val command = EgenAnsattCommand(FNR, dao)
    private val context: CommandContext = CommandContext(UUID.randomUUID())

    @BeforeEach
    fun setup() {
        clearMocks(dao)
    }

    @Test
    fun `ber om informasjon om egen ansatt`() {
        every { dao.sistOppdatert(FNR) } returns null
        assertFalse(command.execute(context))
        assertEquals(listOf("EgenAnsatt"), context.behov().keys.toList())
    }

    @Test
    fun `ber om informasjon om egen ansatt hvis den vi har er utdatert`() {
        every { dao.sistOppdatert(FNR) } returns LocalDateTime.now().minusHours(8)
        assertFalse(command.execute(context))
        assertEquals(listOf("EgenAnsatt"), context.behov().keys.toList())
    }

    @Test
    fun `ber ikke om informasjon om egen ansatt hvis den vi har er oppdatert`() {
        every { dao.sistOppdatert(FNR) } returns LocalDateTime.now().minusHours(7)
        assertTrue(command.execute(context))
        assertEquals(emptyList<String>(), context.behov().keys.toList())
    }

    @Test
    fun `mangler løsning ved resume`() {
        assertFalse(command.resume(context))
        verify(exactly = 0) { dao.lagre(any(), any(), any()) }
    }

    @Test
    fun `lagrer løsning ved resume`() {
        context.add(EgenAnsattløsning(LocalDateTime.now(), FNR, false))
        assertTrue(command.resume(context))
        verify(exactly = 1) { dao.lagre(FNR, false, any()) }
    }
}
