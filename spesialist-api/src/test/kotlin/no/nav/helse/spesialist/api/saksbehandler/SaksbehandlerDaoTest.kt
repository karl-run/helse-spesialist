package no.nav.helse.spesialist.api.saksbehandler

import java.util.UUID
import no.nav.helse.spesialist.api.DatabaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SaksbehandlerDaoTest : DatabaseIntegrationTest() {

    private companion object {
        private const val IS_UPDATED = 1
        private const val IS_NOT_UPDATED = 0
        private val SAKSBEHANDLER_OID = UUID.randomUUID()
        private const val SAKSBEHANDLEREPOST = "saksbehandlerepost@nav.no"
        private const val SAKSBEHANDLER_IDENT = "S123456"
        private const val SAKSBEHANDLER_NAVN = "Fornavn Mellomnavn Etternavn"
    }

    @Test
    fun `oppretter og finner saksbehandler`() {
        saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, "Navn Navnesen", SAKSBEHANDLEREPOST, SAKSBEHANDLER_IDENT)
        assertSaksbehandler(SAKSBEHANDLER_OID, "Navn Navnesen", SAKSBEHANDLEREPOST, SAKSBEHANDLER_IDENT)
    }

    @Test
    fun `Oppdaterer saksbehandlers navn, epost og ident ved konflikt på oid`() {
        val (nyttNavn, nyEpost, nyIdent) = Triple(
            "Navn Navne Navnesen",
            "navn.navne.navnesen@nav.no",
            "Z999999"
        )
        saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, "Navn Navnesen", SAKSBEHANDLEREPOST, SAKSBEHANDLER_IDENT)
        assertEquals(IS_UPDATED, saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, nyttNavn, nyEpost, nyIdent))
        assertSaksbehandler(SAKSBEHANDLER_OID, nyttNavn, nyEpost, nyIdent)
    }

    @Test
    fun `Oppdaterer ikke saksbehandlers navn, epost og ident ved konflikt på oid dersom navn, epost og ident er uendret`() {
        saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, SAKSBEHANDLER_NAVN, SAKSBEHANDLEREPOST, SAKSBEHANDLER_IDENT)
        assertEquals(
            IS_NOT_UPDATED,
            saksbehandlerDao.opprettSaksbehandler(
                SAKSBEHANDLER_OID,
                SAKSBEHANDLER_NAVN,
                SAKSBEHANDLEREPOST,
                SAKSBEHANDLER_IDENT
            )
        )
        assertSaksbehandler(SAKSBEHANDLER_OID, SAKSBEHANDLER_NAVN, SAKSBEHANDLEREPOST, SAKSBEHANDLER_IDENT)
    }

    @Test
    fun `finner saksbehandler vha epost`() {
        saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, SAKSBEHANDLER_NAVN, SAKSBEHANDLEREPOST, SAKSBEHANDLER_IDENT)
        val saksbehandler = saksbehandlerDao.finnSaksbehandler(SAKSBEHANDLEREPOST)
        assertNotNull(saksbehandler)
    }

    @Test
    fun `finner ikke noen saksbehandler hvis det ikke finnes noen`() {
        val saksbehandler = saksbehandlerDao.finnSaksbehandlerFor(UUID.randomUUID())
        assertNull(saksbehandler)
    }

    @Test
    fun `henter ut saksbehandler vha oid`() {
        saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, SAKSBEHANDLER_NAVN, SAKSBEHANDLEREPOST, SAKSBEHANDLER_IDENT)
        val saksbehandler = saksbehandlerDao.finnSaksbehandlerFor(SAKSBEHANDLER_OID)
        assertNotNull(saksbehandler)
        assertEquals(Saksbehandler(SAKSBEHANDLEREPOST, SAKSBEHANDLER_OID, SAKSBEHANDLER_NAVN, SAKSBEHANDLER_IDENT), saksbehandler)
    }

    @Test
    fun `finner saksbehandler vha epost uavhengig av store bokstaver`() {
        saksbehandlerDao.opprettSaksbehandler(SAKSBEHANDLER_OID, SAKSBEHANDLER_NAVN, SAKSBEHANDLEREPOST.uppercase(), SAKSBEHANDLER_IDENT)
        val saksbehandler = saksbehandlerDao.finnSaksbehandler(SAKSBEHANDLEREPOST.lowercase())
        assertNotNull(saksbehandler)
    }

    private fun saksbehandler(oid: UUID) = saksbehandlerDao.finnSaksbehandler(oid)

    private fun assertSaksbehandler(oid: UUID, navn: String, epost: String, ident: String) {
        val saksbehandler = saksbehandler(oid)
        assertNotNull(saksbehandler)
        assertEquals(navn, saksbehandler?.navn)
        assertEquals(epost, saksbehandler?.epost)
        assertEquals(ident, saksbehandler?.ident)
    }
}
