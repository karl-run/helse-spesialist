package no.nav.helse.mediator.oppgave

import java.sql.SQLException
import java.util.UUID
import no.nav.helse.Tilgangskontroll
import no.nav.helse.db.TotrinnsvurderingDao
import no.nav.helse.modell.oppgave.Oppgave
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.spesialist.api.abonnement.GodkjenningsbehovPayload
import no.nav.helse.spesialist.api.abonnement.GodkjenningsbehovPayload.Companion.lagre
import no.nav.helse.spesialist.api.abonnement.OpptegnelseDao
import no.nav.helse.spesialist.api.oppgave.Oppgavestatus
import no.nav.helse.spesialist.api.oppgave.Oppgavetype
import no.nav.helse.spesialist.api.reservasjon.ReservasjonDao
import no.nav.helse.spesialist.api.tildeling.TildelingDao
import org.slf4j.LoggerFactory

class OppgaveMediator(
    private val oppgaveDao: OppgaveDao,
    private val tildelingDao: TildelingDao,
    private val reservasjonDao: ReservasjonDao,
    private val opptegnelseDao: OpptegnelseDao,
    private val totrinnsvurderingDao: TotrinnsvurderingDao,
    private val harTilgangTil: Tilgangskontroll = { _, _ -> false },
) {
    private var oppgaveForLagring: Oppgave? = null
    private var oppgaveForOppdatering: Oppgave? = null
    private val oppgaverForPublisering = mutableMapOf<Long, String>()
    private val logg = LoggerFactory.getLogger(this::class.java)
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    fun opprett(oppgave: Oppgave) {
        leggPåVentForSenereLagring(oppgave)
    }

    fun nyOppgave(opprettOppgaveBlock: (reservertId: Long) -> Oppgave) {
        val nesteId = oppgaveDao.reserverNesteId()
        val oppgave = opprettOppgaveBlock(nesteId)
        leggPåVentForSenereLagring(oppgave)
    }

    fun tildel(oppgaveId: Long, saksbehandleroid: UUID, påVent: Boolean = false): Boolean {
        return tildelingDao.opprettTildeling(oppgaveId, saksbehandleroid, påVent) != null
    }

    fun oppgave(id: Long, oppgaveBlock: Oppgave.() -> Unit) {
        val oppgave = Oppgavehenter(oppgaveDao, totrinnsvurderingDao).oppgave(id)
        oppgaveBlock(oppgave)
        Oppgavelagrer().apply {
            oppgave.accept(this)
            oppdater(this@OppgaveMediator)
        }
    }

    /*
        For nå må oppgaver mellomlagres i denne mediatoren, fordi ved lagring skal det sendes ut meldinger på Kafka,
        og de skal inneholde standardfeltene for rapids-and-rivers, som i utgangspunktet kun er tilgjengelige via
        MessageContext, som HendelseMediator har tilgang til.
    */
    private fun leggPåVentForSenereLagring(oppgave: Oppgave) {
        oppgaveForLagring = oppgave
    }
    private fun leggPåVentForSenereOppdatering(oppgave: Oppgave) {
        oppgaveForOppdatering = oppgave
    }

    fun ferdigstill(oppgave: Oppgave, saksbehandlerIdent: String, oid: UUID) {
        oppgave.ferdigstill(saksbehandlerIdent, oid)
        leggPåVentForSenereOppdatering(oppgave)
    }

    fun ferdigstill(oppgave: Oppgave) {
        oppgave.ferdigstill()
        leggPåVentForSenereOppdatering(oppgave)
    }

    private fun avbryt(oppgave: Oppgave) {
        oppgave.avbryt()
        leggPåVentForSenereOppdatering(oppgave)
    }

    fun invalider(oppgave: Oppgave) {
        oppgave.avbryt()
        leggPåVentForSenereOppdatering(oppgave)
    }

    fun lagreOgTildelOppgaver(
        hendelseId: UUID,
        fødselsnummer: String,
        contextId: UUID,
        messageContext: MessageContext,
    ) {
        tildelOppgaver(fødselsnummer)
        lagreOppgaver(hendelseId, contextId, messageContext)
    }

    fun avbrytOppgaver(vedtaksperiodeId: UUID) {
        oppgaveDao.finnAktiv(vedtaksperiodeId)?.let { oppgave ->
            oppgave.loggOppgaverAvbrutt(vedtaksperiodeId)
            avbryt(oppgave)
        }
    }

    fun opprett(
        id: Long,
        contextId: UUID,
        vedtaksperiodeId: UUID,
        utbetalingId: UUID,
        navn: Oppgavetype,
        hendelseId: UUID
    ) {
        if (oppgaveDao.harGyldigOppgave(utbetalingId)) return
        oppgaveDao.opprettOppgave(id, contextId, navn, vedtaksperiodeId, utbetalingId)
        oppgaverForPublisering[id] = "oppgave_opprettet"
        GodkjenningsbehovPayload(hendelseId).lagre(opptegnelseDao, oppgaveDao.finnFødselsnummer(id))
    }

    fun oppdater(
        oppgaveId: Long,
        status: Oppgavestatus,
        ferdigstiltAvIdent: String?,
        ferdigstiltAvOid: UUID?
    ) {
        oppgaveDao.updateOppgave(oppgaveId, status, ferdigstiltAvIdent, ferdigstiltAvOid)
        oppgaverForPublisering[oppgaveId] = "oppgave_oppdatert"
    }

    fun reserverOppgave(saksbehandleroid: UUID, fødselsnummer: String) {
        try {
            reservasjonDao.reserverPerson(saksbehandleroid, fødselsnummer, false)
        } catch (e: SQLException) {
            logg.warn("Kunne ikke reservere person")
        }
    }

    private fun tildelOppgaver(fødselsnummer: String) {
        reservasjonDao.hentReservasjonFor(fødselsnummer)?.let { (saksbehandler, settPåVent) ->
            (oppgaveForLagring ?: oppgaveForOppdatering)?.forsøkTildeling(saksbehandler, settPåVent, harTilgangTil)
        }
    }

    private fun lagreOppgaver(
        hendelseId: UUID,
        contextId: UUID,
        messageContext: MessageContext
    ) {
        oppgaveForLagring?.let {
            Oppgavelagrer().apply {
                it.accept(this)
                lagre(this@OppgaveMediator, hendelseId, contextId)
            }
            logg.info("Oppgave lagret: $it")
            sikkerlogg.info("Oppgave lagret: $it")
        } ?: oppgaveForOppdatering?.let {
            Oppgavelagrer().apply {
                it.accept(this)
                oppdater(this@OppgaveMediator)
            }
            logg.info("Oppgave oppdatert: $it")
            sikkerlogg.info("Oppgave oppdatert: $it")
        }
        oppgaveForLagring = null
        oppgaveForOppdatering = null
        oppgaverForPublisering.onEach { (oppgaveId, eventName) ->
            messageContext.publish(Oppgave.lagMelding(oppgaveId, eventName, oppgaveDao = oppgaveDao).second.toJson())
        }.clear()
    }

    fun harFerdigstiltOppgave(vedtaksperiodeId: UUID) = oppgaveDao.harFerdigstiltOppgave(vedtaksperiodeId)
}
