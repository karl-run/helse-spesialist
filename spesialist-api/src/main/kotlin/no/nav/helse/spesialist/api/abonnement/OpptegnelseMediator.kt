package no.nav.helse.spesialist.api.abonnement

import java.util.UUID
import no.nav.helse.spesialist.api.modell.Saksbehandler
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerDao

class OpptegnelseMediator(
    private val opptegnelseDao: OpptegnelseDao,
    private val abonnementDao: AbonnementDao,
    private val saksbehandlerDao: SaksbehandlerDao,
) {
    internal fun opprettAbonnement(saksbehandler: Saksbehandler, person_id: Long) {
        saksbehandler.persister(saksbehandlerDao)
        abonnementDao.opprettAbonnement(saksbehandler.oid(), person_id)
    }

    fun hentAbonnerteOpptegnelser(saksbehandlerIdent: UUID, sisteSekvensId: Int): List<OpptegnelseDto> {
        abonnementDao.registrerSistekvensnummer(saksbehandlerIdent, sisteSekvensId)
        return opptegnelseDao.finnOpptegnelser(saksbehandlerIdent).map {
            OpptegnelseDto(
                it.aktorId.toLong(),
                it.sekvensnummer,
                OpptegnelseType.valueOf(it.type.toString()),
                it.payload
            )
        }
    }

    internal fun hentAbonnerteOpptegnelser(saksbehandlerIdent: UUID): List<OpptegnelseDto> {
        return opptegnelseDao.finnOpptegnelser(saksbehandlerIdent).map {
            OpptegnelseDto(
                it.aktorId.toLong(),
                it.sekvensnummer,
                OpptegnelseType.valueOf(it.type.toString()),
                it.payload
            )
        }
    }
}
