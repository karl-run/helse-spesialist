package no.nav.helse.modell

import DatabaseIntegrationTest
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.modell.vedtak.Saksbehandleroppgavetype
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import java.time.LocalDate
import java.util.*

internal class VedtakDaoTest : DatabaseIntegrationTest() {

    @Test
    fun `lagre vedtak`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettSnapshot()
        vedtakDao.opprett(VEDTAKSPERIODE, FOM, TOM, personId, arbeidsgiverId, snapshotId)
        assertNotNull(vedtakDao.findVedtak(VEDTAKSPERIODE))
        assertEquals(1, vedtak().size)
        vedtak().first().assertEquals(VEDTAKSPERIODE, FOM, TOM, personId, arbeidsgiverId, snapshotId)
    }

    @Test
    fun `opprette duplikat vedtak`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettSnapshot()
        vedtakDao.opprett(VEDTAKSPERIODE, FOM, TOM, personId, arbeidsgiverId, snapshotId)
        val nyFom = LocalDate.now().minusMonths(1)
        val nyTom = LocalDate.now()
        val nySnapshotRef = snapshotDao.insertSpeilSnapshot("{}")
        assertThrows<PSQLException> { vedtakDao.opprett(VEDTAKSPERIODE, nyFom, nyTom, personId, arbeidsgiverId, nySnapshotRef) }
    }

    @Test
    fun `oppdatere vedtak`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettSnapshot()
        opprettVedtaksperiode()
        val nyFom = LocalDate.now().minusMonths(1)
        val nyTom = LocalDate.now()
        val nySnapshotRef = snapshotDao.insertSpeilSnapshot("{}")
        vedtakDao.oppdater(vedtakId, nyFom, nyTom, nySnapshotRef)
        assertEquals(1, vedtak().size)
        vedtak().first().assertEquals(VEDTAKSPERIODE, nyFom, nyTom, personId, arbeidsgiverId, nySnapshotRef)
    }

    @Test
    fun `lagrer warnings`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        val testwarnings= listOf("Warning A", "Warning B")
        vedtakDao.leggTilWarnings(VEDTAKSPERIODE, testwarnings)
        assertWarnings(VEDTAKSPERIODE, testwarnings)
    }

    @Test
    fun `lagrer vedtaksperiodetype hvis den er satt`() {
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        val vedtaksperiodetype = Saksbehandleroppgavetype.FØRSTEGANGSBEHANDLING
        vedtakDao.leggTilVedtaksperiodetype(VEDTAKSPERIODE, vedtaksperiodetype)
        assertVedtaksperiodetype(VEDTAKSPERIODE, vedtaksperiodetype)
    }

    @Test
    fun `oppretter innslag i koblingstabellen`() {
        godkjenningsbehov(HENDELSE_ID)
        nyPerson()
        vedtakDao.opprettKobling(VEDTAKSPERIODE, HENDELSE_ID)
        assertEquals(vedtakId, finnKobling(HENDELSE_ID))
    }

    @Test
    fun `fjerner innslag i koblingstabellen`() {
        godkjenningsbehov(HENDELSE_ID)
        nyPerson()
        vedtakDao.opprettKobling(VEDTAKSPERIODE, HENDELSE_ID)
        assertEquals(vedtakId, finnKobling(HENDELSE_ID))

        vedtakDao.fjernKobling(VEDTAKSPERIODE, HENDELSE_ID)

        assertNull(finnKobling(HENDELSE_ID))
    }

    private fun assertVedtaksperiodetype(vedtaksperiodeId: UUID, type: Saksbehandleroppgavetype) {
        assertEquals(type, vedtaksperiodetype(vedtaksperiodeId))
    }

    @Test
    fun `fjerner vedtak`() {
        val NY_VEDTAKSPERIODE = UUID.randomUUID()
        opprettPerson()
        opprettArbeidsgiver()
        opprettVedtaksperiode()
        opprettVedtaksperiode(NY_VEDTAKSPERIODE)
        vedtakDao.fjernVedtaksperioder(listOf(VEDTAKSPERIODE, NY_VEDTAKSPERIODE))
        assertEquals(0, vedtak().size)
    }

    private fun assertWarnings(vedtaksperiodeId: UUID, warnings: List<String>) {
        assertEquals(warnings, finnWarnings(vedtaksperiodeId))
    }

    private fun vedtaksperiodetype(vedtaksperiodeId: UUID): Saksbehandleroppgavetype? {
        return using(sessionOf(dataSource)) {
            it.run(queryOf("SELECT type FROM saksbehandleroppgavetype WHERE vedtak_ref = (SELECT id FROM vedtak WHERE vedtaksperiode_id = ?)", vedtaksperiodeId).map { row ->
                Saksbehandleroppgavetype.valueOf(row.string("type"))
            }.asSingle)
        }
    }

    private fun finnWarnings(vedtaksperiodeId: UUID) = using(sessionOf(dataSource)) {
        it.run(
            queryOf("SELECT melding FROM warning WHERE vedtak_ref = (SELECT id FROM vedtak WHERE vedtaksperiode_id = ?)", vedtaksperiodeId).map { row ->
                row.string("melding")
            }.asList
        )
    }

    private fun finnKobling(hendelseId: UUID) = using(sessionOf(dataSource)) {
        it.run(
            queryOf("SELECT vedtaksperiode_ref FROM vedtaksperiode_hendelse WHERE hendelse_ref = ?", hendelseId)
                .map { it.long(1)
            }.asSingle
        )
    }

    private fun vedtak() = using(sessionOf(dataSource)) {
        it.run(queryOf("SELECT vedtaksperiode_id, fom, tom, person_ref, arbeidsgiver_ref, speil_snapshot_ref FROM vedtak").map {
            Vedtak(
                UUID.fromString(it.string("vedtaksperiode_id")),
                it.localDate("fom"),
                it.localDate("tom"),
                it.int("person_ref"),
                it.int("arbeidsgiver_ref"),
                it.int("speil_snapshot_ref")
            )
        }.asList)
    }

    private class Vedtak(
        private val vedtaksperiodeId: UUID,
        private val fom: LocalDate,
        private val tom: LocalDate,
        private val personRef: Int,
        private val arbeidsgiverRef: Int,
        private val snapshotRef: Int
    ) {
        fun assertEquals(forventetVedtaksperiodeId: UUID, forventetFom: LocalDate, forventetTom: LocalDate, forventetPersonRef: Int, forventetArbeidsgiverRef: Int, forventetSnapshotRef: Int) {
            assertEquals(forventetVedtaksperiodeId, vedtaksperiodeId)
            assertEquals(forventetFom, fom)
            assertEquals(forventetTom, tom)
            assertEquals(forventetPersonRef, personRef)
            assertEquals(forventetArbeidsgiverRef, arbeidsgiverRef)
            assertEquals(forventetSnapshotRef, snapshotRef)
        }
    }
}
