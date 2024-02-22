package no.nav.helse.modell.saksbehandler.handlinger.dto

import java.time.LocalDate

data class OverstyrteArbeidsforholdDto(
    val fødselsnummer: String,
    val aktørId: String,
    val skjæringstidspunkt: LocalDate,
    val overstyrteArbeidsforhold: List<OverstyrtArbeidsforholdDto>
)

data class OverstyrtArbeidsforholdDto(
    val organisasjonsnummer: String,
    val deaktivert: Boolean,
    val begrunnelse: String,
    val forklaring: String
)