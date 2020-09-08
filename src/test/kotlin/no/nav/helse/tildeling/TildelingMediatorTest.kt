package no.nav.helse.tildeling

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.ktor.http.*
import no.nav.helse.modell.feilhåndtering.ModellFeil
import no.nav.helse.modell.feilhåndtering.OppgaveErAlleredeTildelt
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import javax.sql.DataSource
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TildelingMediatorTest {
    private lateinit var embeddedPostgres: EmbeddedPostgres
    private lateinit var dataSource: DataSource
    private var vedtakId by Delegates.notNull<Long>()

    @BeforeAll
    fun setup(@TempDir postgresPath: Path) {
        embeddedPostgres = EmbeddedPostgres.builder()
            .setOverrideWorkingDirectory(postgresPath.toFile())
            .setDataDirectory(postgresPath.resolve("datadir"))
            .start()
        dataSource = embeddedPostgres.setupDataSource()
        vedtakId = dataSource.opprettVedtak().toLong()
    }

    @AfterAll
    fun tearDown() {
        embeddedPostgres.close()
    }

    @Test
    fun `lagre en tildeling`() {
        val oppgavereferanse = UUID.randomUUID()
        val saksbehandlerReferanse = UUID.randomUUID()
        val epost = "sara.saksbehandler@nav.no"

        dataSource.opprettSaksbehandler(saksbehandlerReferanse, epost)

        TildelingMediator(dataSource).tildelOppgaveTilSaksbehandler(oppgavereferanse, saksbehandlerReferanse)

        assertEquals(epost, TildelingMediator(dataSource).hentSaksbehandlerFor(oppgavereferanse))
    }

    @Test
    fun `fjerne en tildeling`() {
        val tildelingMediator = TildelingMediator(dataSource)
        val oppgavereferanse = UUID.randomUUID()
        val saksbehandlerReferanse = UUID.randomUUID()

        dataSource.opprettSaksbehandler(saksbehandlerReferanse)

        tildelingMediator.tildelOppgaveTilSaksbehandler(oppgavereferanse, saksbehandlerReferanse)
        tildelingMediator.fjernTildeling(oppgavereferanse)

        assertNull(tildelingMediator.hentSaksbehandlerFor(oppgavereferanse))
    }

    @Test
    fun `kan fjerne en ikke eksisterende tildeling`() {
        val tildelingMediator = TildelingMediator(dataSource)
        val oppgavereferanse = UUID.randomUUID()
        val saksbehandlerReferanse = UUID.randomUUID()

        dataSource.opprettSaksbehandler(saksbehandlerReferanse)

        tildelingMediator.fjernTildeling(oppgavereferanse)

        assertNull(tildelingMediator.hentSaksbehandlerFor(oppgavereferanse))
    }

    @Test
    fun `epost til saksbehandler blir lagt til i saksbehandler payload`() {
        val oppgavereferanse = UUID.randomUUID()
        val saksbehandlerReferanse = UUID.randomUUID()
        val epost = "sara.saksbehandler@nav.no"

        dataSource.opprettSaksbehandler(saksbehandlerReferanse, epost)
        dataSource.opprettSaksbehandlerOppgave(oppgavereferanse, vedtakId)

        val tildelingMediator = TildelingMediator(dataSource)
        tildelingMediator.tildelOppgaveTilSaksbehandler(oppgavereferanse, saksbehandlerReferanse)

        val oppgaver = dataSource.finnSaksbehandlerOppgaver()
        val oppgave = oppgaver.first { it.oppgavereferanse == oppgavereferanse }

        assertEquals(epost, oppgave.saksbehandlerepost)
    }

    @Test
    fun `stopper tildeling av allerede tildelt sak`() {
        val oppgavereferanse = UUID.randomUUID()
        val saksbehandlerReferanse = UUID.randomUUID()
        val saksbehandlerReferanse2 = UUID.randomUUID()

        val epost = "sara.saksbehandler@nav.no"
        val epost2 = "mille.mellomleder@nav.no"

        dataSource.opprettSaksbehandler(saksbehandlerReferanse, epost)
        dataSource.opprettSaksbehandler(saksbehandlerReferanse2, epost2)

        TildelingMediator(dataSource).tildelOppgaveTilSaksbehandler(oppgavereferanse, saksbehandlerReferanse)
        assertEquals(epost, TildelingMediator(dataSource).hentSaksbehandlerFor(oppgavereferanse))


        val feil = assertThrows<ModellFeil> {
            TildelingMediator(dataSource).tildelOppgaveTilSaksbehandler(oppgavereferanse, saksbehandlerReferanse2)
        }
        assertEquals(feil.httpKode(), HttpStatusCode.BadRequest)
        assertEquals(feil.feil, OppgaveErAlleredeTildelt)
    }

    @Test
    fun `stopper tildeling av allerede tildelt sak også for en selv`() {
        val oppgavereferanse = UUID.randomUUID()
        val saksbehandlerReferanse = UUID.randomUUID()

        val epost = "sara.saksbehandler@nav.no"

        dataSource.opprettSaksbehandler(saksbehandlerReferanse, epost)

        TildelingMediator(dataSource).tildelOppgaveTilSaksbehandler(oppgavereferanse, saksbehandlerReferanse)
        assertEquals(epost, TildelingMediator(dataSource).hentSaksbehandlerFor(oppgavereferanse))


        val feil = assertThrows<ModellFeil> {
            TildelingMediator(dataSource).tildelOppgaveTilSaksbehandler(oppgavereferanse, saksbehandlerReferanse)
        }
        assertEquals(feil.httpKode(), HttpStatusCode.BadRequest)
        assertEquals(feil.feil, OppgaveErAlleredeTildelt)
    }
}
