package no.nav.helse.spesialist.api

import javax.sql.DataSource
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerDao
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerDto
import no.nav.helse.spesialist.api.saksbehandler.SaksbehandlerObserver
import no.nav.helse.spesialist.api.utbetaling.SaksbehandlerHendelse
import org.slf4j.LoggerFactory

class SaksbehandlerMediator(
    dataSource: DataSource,
    private val rapidsConnection: RapidsConnection
): SaksbehandlerObserver {
    private val saksbehandlerDao = SaksbehandlerDao(dataSource)

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    fun <T: SaksbehandlerHendelse> håndter(hendelse: T, saksbehandlerDto: SaksbehandlerDto) {
        val saksbehandlerOid = saksbehandlerDto.oid
        if (saksbehandlerOid != hendelse.saksbehandlerOid()) throw IllegalStateException()
        val saksbehandler = saksbehandlerDao.finnSaksbehandlerFor(saksbehandlerOid) ?: saksbehandlerDao.opprettFra(saksbehandlerDto)
        saksbehandler.register(this)
        sikkerlogg.info("$saksbehandler behandler nå ${hendelse::class.simpleName}")
        hendelse.håndter(saksbehandler)
        sikkerlogg.info("$saksbehandler har ferdigbehandlet ${hendelse::class.simpleName}")
        tellHendelse(hendelse)
    }

    override fun annulleringEvent(
        aktørId: String,
        fødselsnummer: String,
        organisasjonsnummer: String,
        saksbehandler: Map<String, Any>,
        fagsystemId: String,
        begrunnelser: List<String>,
        kommentar: String?
    ) {
        val annulleringMessage = JsonMessage.newMessage("annullering", mutableMapOf(
            "fødselsnummer" to fødselsnummer,
            "organisasjonsnummer" to organisasjonsnummer,
            "aktørId" to aktørId,
            "saksbehandler" to saksbehandler,
            "fagsystemId" to fagsystemId,
            "begrunnelser" to begrunnelser,
        ).apply {
            compute("kommentar") { _, _ -> kommentar }
        })

        rapidsConnection.publish(fødselsnummer, annulleringMessage.toJson().also {
            sikkerlogg.info(
                "sender annullering for {}, {}\n\t$it",
                keyValue("fødselsnummer", fødselsnummer),
                keyValue("organisasjonsnummer", organisasjonsnummer)
            )
        })
    }
}