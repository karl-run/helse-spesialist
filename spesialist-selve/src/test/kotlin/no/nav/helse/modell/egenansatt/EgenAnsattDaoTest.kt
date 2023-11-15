package no.nav.helse.modell.egenansatt

import DatabaseIntegrationTest
import java.time.Duration
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


internal class EgenAnsattDaoTest : DatabaseIntegrationTest() {
    @BeforeEach
    fun setup() {
        testhendelse()
        opprettPerson()
    }

    @Test
    fun `setter og henter egen ansatt`() {
        egenAnsattDao.lagre(FNR, false, LocalDateTime.now())
        val egenAnsattSvar = egenAnsattDao.erEgenAnsatt(FNR)
        assertNotNull(egenAnsattSvar)
        if (egenAnsattSvar != null) {
            assertFalse(egenAnsattSvar)
        }
    }

    @Test
    fun `vet hvor gammel informasjonen er`() {
        val now = LocalDateTime.now()
        egenAnsattDao.lagre(FNR, false, now)
        val egenAnsattSistOppdatert = egenAnsattDao.sistOppdatert(FNR)
        assertEquals(0, Duration.between(now, egenAnsattSistOppdatert).seconds)
    }
}
