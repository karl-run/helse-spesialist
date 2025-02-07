package no.nav.helse.spesialist.api.graphql.schema

data class Skjonnsfastsettelse(
    val aktorId: String,
    val fodselsnummer: String,
    val skjaringstidspunkt: DateString,
    val arbeidsgivere: List<SkjonnsfastsettelseArbeidsgiver>,
    val vedtaksperiodeId: String? = null,
)

data class SkjonnsfastsettelseArbeidsgiver(
    val organisasjonsnummer: String,
    val arlig: Double,
    val fraArlig: Double,
    val arsak: String,
    val type: SkjonnsfastsettelseType,
    val begrunnelseMal: String?,
    val begrunnelseFritekst: String?,
    val begrunnelseKonklusjon: String?,
    val lovhjemmel: Lovhjemmel?,
    val initierendeVedtaksperiodeId: String?,
) {
    enum class SkjonnsfastsettelseType {
        OMREGNET_ARSINNTEKT,
        RAPPORTERT_ARSINNTEKT,
        ANNET,
    }
}
