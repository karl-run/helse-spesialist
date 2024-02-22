package no.nav.helse.db

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class OverstyrteArbeidsforholdForDatabase(
    val id: UUID = UUID.randomUUID(),
    val fødselsnummer: String,
    val aktørId: String,
    val skjæringstidspunkt: LocalDate,
    val overstyrteArbeidsforhold: List<OverstyrtArbeidsforholdForDatabase>,
    val opprettet: LocalDateTime
) {
}

class OverstyrtArbeidsforholdForDatabase(
    val organisasjonsnummer: String,
    val deaktivert: Boolean,
    val begrunnelse: String,
    val forklaring: String
)