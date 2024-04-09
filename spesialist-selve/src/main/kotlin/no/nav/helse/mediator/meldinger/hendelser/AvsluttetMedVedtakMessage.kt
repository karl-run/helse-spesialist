package no.nav.helse.mediator.meldinger.hendelser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import no.nav.helse.db.AvviksvurderingDao
import no.nav.helse.mediator.Kommandofabrikk
import no.nav.helse.mediator.asUUID
import no.nav.helse.mediator.meldinger.Vedtaksperiodemelding
import no.nav.helse.modell.avviksvurdering.Avviksvurdering.Companion.finnRiktigAvviksvurdering
import no.nav.helse.modell.avviksvurdering.InnrapportertInntektDto
import no.nav.helse.modell.person.Person
import no.nav.helse.modell.vedtaksperiode.vedtak.AvsluttetMedVedtak
import no.nav.helse.modell.vedtaksperiode.vedtak.Faktatype
import no.nav.helse.modell.vedtaksperiode.vedtak.Sykepengegrunnlagsfakta
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class AvsluttetMedVedtakMessage(
    private val fødselsnummer: String,
    private val aktørId: String,
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val vedtakFattetTidspunkt: LocalDateTime,
    private val vedtaksperiodeId: UUID,
    private val spleisBehandlingId: UUID,
    private val organisasjonsnummer: String,
    private val utbetalingId: UUID,
    private val skjæringstidspunkt: LocalDate,
    private val hendelser: List<UUID>,
    private val sykepengegrunnlag: Double,
    private val grunnlagForSykepengegrunnlag: Double,
    private val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>,
    private val begrensning: String,
    private val inntekt: Double,
    private val tags: List<String>,
    private val sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta,
    private val packet: JsonMessage,
    private val avviksvurderingDao: AvviksvurderingDao,
) : Vedtaksperiodemelding {
    internal constructor(jsonNode: JsonNode, avviksvurderingDao: AvviksvurderingDao) : this(
        fødselsnummer = jsonNode["fødselsnummer"].asText(),
        aktørId = jsonNode["aktørId"].asText(),
        fom = jsonNode["fom"].asLocalDate(),
        tom = jsonNode["tom"].asLocalDate(),
        vedtakFattetTidspunkt = jsonNode["vedtakFattetTidspunkt"].asLocalDateTime(),
        vedtaksperiodeId = UUID.fromString(jsonNode["vedtaksperiodeId"].asText()),
        spleisBehandlingId = UUID.fromString(jsonNode["behandlingId"].asText()),
        organisasjonsnummer = jsonNode["organisasjonsnummer"].asText(),
        utbetalingId = jsonNode["utbetalingId"].asUUID(),
        skjæringstidspunkt = jsonNode["skjæringstidspunkt"].asLocalDate(),
        hendelser = jsonNode["hendelser"].map { it.asUUID() },
        sykepengegrunnlag = jsonNode["sykepengegrunnlag"].asDouble(),
        grunnlagForSykepengegrunnlag = jsonNode["grunnlagForSykepengegrunnlag"].asDouble(),
        grunnlagForSykepengegrunnlagPerArbeidsgiver =
            jacksonObjectMapper().treeToValue<Map<String, Double>>(
                jsonNode["grunnlagForSykepengegrunnlagPerArbeidsgiver"],
            ),
        begrensning = jsonNode["begrensning"].asText(),
        inntekt = jsonNode["inntekt"].asDouble(),
        tags = jsonNode["tags"].map { it.asText() },
        sykepengegrunnlagsfakta = sykepengegrunnlagsfakta(jsonNode["sykepengegrunnlagsfakta"], faktatype(jsonNode)),
        avviksvurderingDao = avviksvurderingDao,
    )

    private val fødselsnummer: String = packet["fødselsnummer"].asText()
    private val aktørId: String = packet["aktørId"].asText()
    private val fom: LocalDate = packet["fom"].asLocalDate()
    private val tom: LocalDate = packet["tom"].asLocalDate()
    private val vedtakFattetTidspunkt: LocalDateTime = packet["vedtakFattetTidspunkt"].asLocalDateTime()
    private val vedtaksperiodeId: UUID = UUID.fromString(packet["vedtaksperiodeId"].asText())
    private val spleisBehandlingId: UUID = UUID.fromString(packet["behandlingId"].asText())
    private val organisasjonsnummer: String = packet["organisasjonsnummer"].asText()
    private val utbetalingId: UUID = packet["utbetalingId"].asUUID()
    private val skjæringstidspunkt: LocalDate = packet["skjæringstidspunkt"].asLocalDate()
    private val hendelser: List<UUID> = packet["hendelser"].map { it.asUUID() }
    private val sykepengegrunnlag: Double = packet["sykepengegrunnlag"].asDouble()
    private val grunnlagForSykepengegrunnlag: Double = packet["grunnlagForSykepengegrunnlag"].asDouble()
    private val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double> =
        jacksonObjectMapper().treeToValue<Map<String, Double>>(
            packet["grunnlagForSykepengegrunnlagPerArbeidsgiver"],
        )
    private val begrensning: String = packet["begrensning"].asText()
    private val inntekt: Double = packet["inntekt"].asDouble()
    private val tags: List<String> = packet["tags"].map { it.asText() }
    private val sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta = sykepengegrunnlagsfakta(packet, faktatype(packet))

    internal fun skjæringstidspunkt() = skjæringstidspunkt

    override fun fødselsnummer(): String = fødselsnummer

    override fun vedtaksperiodeId(): UUID = vedtaksperiodeId

    override fun behandle(
        person: Person,
        kommandofabrikk: Kommandofabrikk,
    ) {
        person.avsluttetMedVedtak(avsluttetMedVedtak)
    }

    override val id: UUID = packet["@id"].asUUID()

    override fun toJson(): String = packet.toJson()

    private val avsluttetMedVedtak get() =
        AvsluttetMedVedtak(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            spleisBehandlingId = spleisBehandlingId,
            utbetalingId = utbetalingId,
            skjæringstidspunkt = skjæringstidspunkt,
            hendelser = hendelser,
            sykepengegrunnlag = sykepengegrunnlag,
            grunnlagForSykepengegrunnlag = grunnlagForSykepengegrunnlag,
            grunnlagForSykepengegrunnlagPerArbeidsgiver = grunnlagForSykepengegrunnlagPerArbeidsgiver,
            begrensning = begrensning,
            inntekt = inntekt,
            sykepengegrunnlagsfakta = sykepengegrunnlagsfakta,
            fom = fom,
            tom = tom,
            vedtakFattetTidspunkt = vedtakFattetTidspunkt,
            tags = tags,
        )

    private fun faktatype(packet: JsonMessage): Faktatype {
        return when (val fastsattString = packet["sykepengegrunnlagsfakta.fastsatt"].asText()) {
            "EtterSkjønn" -> Faktatype.ETTER_SKJØNN
            "EtterHovedregel" -> Faktatype.ETTER_HOVEDREGEL
            "IInfotrygd" -> Faktatype.I_INFOTRYGD
            else -> throw IllegalArgumentException("FastsattType $fastsattString er ikke støttet")
        }
    }

    private fun sykepengegrunnlagsfakta(
        faktaNode: JsonNode,
        faktatype: Faktatype,
    ): Sykepengegrunnlagsfakta {
        if (faktatype == Faktatype.I_INFOTRYGD) {
            return Sykepengegrunnlagsfakta.Infotrygd(
                omregnetÅrsinntekt = faktaNode["omregnetÅrsinntekt"].asDouble(),
            )
        }

        val avviksvurderingDto = finnAvviksvurdering().toDto()
        val innrapportertÅrsinntekt = avviksvurderingDto.sammenligningsgrunnlag.totalbeløp
        val avviksprosent = avviksvurderingDto.avviksprosent
        val innrapporterteInntekter = avviksvurderingDto.sammenligningsgrunnlag.innrapporterteInntekter

        return when (faktatype) {
            Faktatype.ETTER_SKJØNN ->
                Sykepengegrunnlagsfakta.Spleis.EtterSkjønn(
                    omregnetÅrsinntekt = faktaNode["omregnetÅrsinntekt"].asDouble(),
                    innrapportertÅrsinntekt = innrapportertÅrsinntekt,
                    avviksprosent = avviksprosent,
                    seksG = faktaNode["6G"].asDouble(),
                    skjønnsfastsatt = faktaNode["skjønnsfastsatt"].asDouble(),
                    tags = faktaNode["tags"].map { it.asText() },
                    arbeidsgivere =
                        faktaNode["arbeidsgivere"].map { arbeidsgiver ->
                            val organisasjonsnummer = arbeidsgiver["arbeidsgiver"].asText()
                            Sykepengegrunnlagsfakta.Spleis.Arbeidsgiver.EtterSkjønn(
                                organisasjonsnummer = organisasjonsnummer,
                                omregnetÅrsinntekt = arbeidsgiver["omregnetÅrsinntekt"].asDouble(),
                                innrapportertÅrsinntekt = innrapporterteInntekter(organisasjonsnummer, innrapporterteInntekter),
                                skjønnsfastsatt = arbeidsgiver["skjønnsfastsatt"].asDouble(),
                            )
                        },
                )

            Faktatype.ETTER_HOVEDREGEL ->
                Sykepengegrunnlagsfakta.Spleis.EtterHovedregel(
                    omregnetÅrsinntekt = faktaNode["omregnetÅrsinntekt"].asDouble(),
                    innrapportertÅrsinntekt = innrapportertÅrsinntekt,
                    avviksprosent = avviksprosent,
                    seksG = faktaNode["6G"].asDouble(),
                    tags = faktaNode["tags"].map { it.asText() },
                    arbeidsgivere =
                        faktaNode["arbeidsgivere"].map { arbeidsgiver ->
                            val organisasjonsnummer = arbeidsgiver["arbeidsgiver"].asText()
                            Sykepengegrunnlagsfakta.Spleis.Arbeidsgiver.EtterHovedregel(
                                organisasjonsnummer = organisasjonsnummer,
                                omregnetÅrsinntekt = arbeidsgiver["omregnetÅrsinntekt"].asDouble(),
                                innrapportertÅrsinntekt = innrapporterteInntekter(organisasjonsnummer, innrapporterteInntekter),
                            )
                        },
                )

            else -> error("Her vet vi ikke hva som har skjedd. Feil i kompilatoren?")
        }
    }

    private fun finnAvviksvurdering() =
        checkNotNull(
            avviksvurderingDao.finnAvviksvurderinger(fødselsnummer).finnRiktigAvviksvurdering(skjæringstidspunkt),
        ) {
            "Forventet å finne avviksvurdering for $aktørId og skjæringstidspunkt $skjæringstidspunkt"
        }

    private fun innrapporterteInntekter(
        arbeidsgiverreferanse: String,
        innrapportertInntekter: List<InnrapportertInntektDto>,
    ): Double =
        innrapportertInntekter
            .filter { it.arbeidsgiverreferanse == arbeidsgiverreferanse }
            .flatMap { it.inntekter }
            .sumOf { it.beløp }
}
