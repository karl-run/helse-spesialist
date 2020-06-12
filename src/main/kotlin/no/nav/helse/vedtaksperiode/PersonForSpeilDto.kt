package no.nav.helse.vedtaksperiode

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.modell.risiko.RisikovurderingDto
import no.nav.helse.modell.vedtak.EnhetDto
import no.nav.helse.modell.vedtak.NavnDto
import java.util.*


data class PersonForSpeilDto(
    val aktørId: String,
    val fødselsnummer: String,
    val navn: NavnDto,
    val arbeidsgivere: List<ArbeidsgiverForSpeilDto>,
    val infotrygdutbetalinger: JsonNode?,
    val enhet: EnhetDto
)

data class ArbeidsgiverForSpeilDto(
    val organisasjonsnummer: String,
    val navn: String,
    val id: UUID,
    val vedtaksperioder: List<JsonNode>,
    val risikovurderinger: List<RisikovurderingDto>
)
