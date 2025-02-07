package no.nav.helse.spesialist.api.notat

import no.nav.helse.spesialist.api.graphql.schema.NotatType
import org.slf4j.LoggerFactory
import java.util.UUID

class NotatMediator(
    private val notatDao: NotatDao,
) {
    private companion object {
        private val log = LoggerFactory.getLogger(NotatMediator::class.java)
    }

    fun lagreForOppgaveId(
        oppgaveId: Long,
        tekst: String,
        saksbehandler_oid: UUID,
        notatType: NotatType = NotatType.Generelt,
    ): Long? {
        log.info("{} lagrer {} notat for oppgaveId {}", saksbehandler_oid, notatType, oppgaveId)
        return notatDao.opprettNotatForOppgaveId(oppgaveId, tekst, saksbehandler_oid, notatType)
    }
}
