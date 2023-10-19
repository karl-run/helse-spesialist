package no.nav.helse.mediator.dokument

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.modell.dokument.DokumentDao
import no.nav.helse.objectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spesialist.api.Dokumenthåndterer
import org.slf4j.LoggerFactory

class DokumentMediator(
    private val dokumentDao: DokumentDao,
    private val rapidsConnection: RapidsConnection,
) : Dokumenthåndterer {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val retries = 30
    }

    override fun håndter(fødselsnummer: String, dokumentId: UUID, dokumentType: String): JsonNode {
        return dokumentDao.hent(fødselsnummer, dokumentId).let { søknad ->
            if (søknad == null) {
                sendHentDokument(fødselsnummer, dokumentId, dokumentType)

                val response = runBlocking {
                    delay(100)
                    hentDokument(fødselsnummer, dokumentId, retries)
                }
                return@let response
            }

            return@let søknad
        }
    }

    private suspend fun hentDokument(fødselsnummer: String, dokumentId: UUID, retries: Int): JsonNode {
        if (retries == 0) return objectMapper.createObjectNode()

        val response = runBlocking {
            val søknad = dokumentDao.hent(fødselsnummer, dokumentId)
            if (søknad == null) {
                delay(100)
                hentDokument(fødselsnummer, dokumentId, retries - 1)
            } else {
                return@runBlocking søknad
            }
        }
        return response
    }

    private fun sendHentDokument(
        fødselsnummer: String, dokumentId: UUID, dokumentType: String
    ) {
        val message = JsonMessage.newMessage(
            "hent-dokument", mapOf(
                "fødselsnummer" to fødselsnummer,
                "dokumentId" to dokumentId,
                "dokumentType" to dokumentType
            )
        )
        sikkerlogg.info(
            "Publiserer hent-dokument med {}, {}",
            StructuredArguments.kv("dokumentId", dokumentId),
            StructuredArguments.kv("dokumentType", dokumentType),
        )
        rapidsConnection.publish(fødselsnummer, message.toJson())
    }
}