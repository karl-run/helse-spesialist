package no.nav.helse.modell.dto

import com.fasterxml.jackson.databind.JsonNode
import java.util.*

data class PersonFraSpleisDto(
    val aktørId: String,
    val fødselsnummer: String,
    val arbeidsgivere: List<ArbeidsgiverFraSpleisDto>
)

data class ArbeidsgiverFraSpleisDto(
    val organisasjonsnummer: String,
    val id: UUID,
    val vedtaksperioder: List<JsonNode>
)

