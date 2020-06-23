package no.nav.helse

import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.mediator.kafka.SpleisbehovMediator
import no.nav.helse.mediator.kafka.meldinger.GodkjenningMessage
import no.nav.helse.modell.arbeidsgiver.ArbeidsgiverDao
import no.nav.helse.modell.command.SpleisbehovDao
import no.nav.helse.modell.command.findNåværendeOppgave
import no.nav.helse.modell.person.PersonDao
import no.nav.helse.modell.vedtak.findVedtak
import no.nav.helse.modell.vedtak.snapshot.SnapshotDao
import no.nav.helse.modell.vedtak.snapshot.SpeilSnapshotRestDao
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class GodkjenningsbehovMediatorTest {
    private lateinit var dataSource: DataSource
    private lateinit var personDao: PersonDao
    private lateinit var arbeidsgiverDao: ArbeidsgiverDao
    private lateinit var snapshotDao: SnapshotDao
    private lateinit var speilSnapshotRestDao: SpeilSnapshotRestDao
    private lateinit var spleisbehovDao: SpleisbehovDao
    private lateinit var testDao: TestPersonDao

    private val spleisMockClient = SpleisMockClient()
    private val accessTokenClient = accessTokenClient()

    private val spesialistOID: UUID = UUID.randomUUID()

    @BeforeAll
    fun setup() {
        dataSource = setupDataSourceMedFlyway()
        personDao = PersonDao(dataSource)
        arbeidsgiverDao = ArbeidsgiverDao(dataSource)
        snapshotDao = SnapshotDao(dataSource)
        speilSnapshotRestDao = SpeilSnapshotRestDao(
            spleisMockClient.client,
            accessTokenClient,
            "spleisClientId"
        )
        spleisbehovDao = SpleisbehovDao(dataSource)
        testDao = TestPersonDao(dataSource)
    }

    @Test
    fun `Spleisbehov persisteres`() {
        val spleisbehovMediator = SpleisbehovMediator(
            dataSource = dataSource,
            spleisbehovDao = spleisbehovDao,
            personDao = personDao,
            arbeidsgiverDao = arbeidsgiverDao,
            snapshotDao = snapshotDao,
            speilSnapshotRestDao = speilSnapshotRestDao,
            spesialistOID = spesialistOID
        ).apply { init(TestRapid()) }
        val spleisbehovId = UUID.randomUUID()
        val godkjenningMessage = GodkjenningMessage(
            id = spleisbehovId,
            fødselsnummer = "12345",
            aktørId = "12345",
            organisasjonsnummer = "89123",
            vedtaksperiodeId = UUID.randomUUID(),
            periodeFom = LocalDate.of(2018, 1, 1),
            periodeTom = LocalDate.of(2018, 1, 31),
            warnings = emptyList()
        )
        spleisbehovMediator.håndter(godkjenningMessage, "{}")
        assertNotNull(spleisbehovDao.findBehov(spleisbehovId))
    }

    @Test
    fun `invaliderer oppgaver for vedtaksperioder som er rullet tilbake`() {
        val eventId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        val person = TestPerson(dataSource)
        person.sendGodkjenningMessage(eventId = eventId, vedtaksperiodeId = vedtaksperiodeId)
        person.sendPersoninfo(eventId = eventId)

        using(sessionOf(dataSource)) { session ->
            assertNotEquals(Oppgavestatus.Invalidert, session.findNåværendeOppgave(eventId)?.status)
            assertNotNull(session.findVedtak(vedtaksperiodeId))
            person.rullTilbake(UUID.randomUUID(), vedtaksperiodeId)
            assertEquals(Oppgavestatus.Invalidert, session.findNåværendeOppgave(eventId)?.status)
            assertNull(session.findVedtak(vedtaksperiodeId))
        }
    }

    @Test
    fun `behandler vedtaksperiode etter rollback`() {
        val eventId1 = UUID.randomUUID()
        val vedtaksperiodeId1 = UUID.randomUUID()
        val eventId2 = UUID.randomUUID()
        val vedtaksperiodeId2 = UUID.randomUUID()
        val person = TestPerson(dataSource)

        person.sendGodkjenningMessage(eventId = eventId1, vedtaksperiodeId = vedtaksperiodeId1)
        person.sendPersoninfo(eventId = eventId1)
        person.rullTilbake(UUID.randomUUID(), vedtaksperiodeId1)
        person.sendGodkjenningMessage(eventId = eventId2, vedtaksperiodeId = vedtaksperiodeId2)

        using(sessionOf(dataSource)) { session ->
            assertNull(session.findVedtak(vedtaksperiodeId1))
            assertNotNull(session.findVedtak(vedtaksperiodeId2))
        }
    }
}
