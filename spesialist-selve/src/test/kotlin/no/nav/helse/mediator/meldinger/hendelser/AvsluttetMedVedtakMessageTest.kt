package no.nav.helse.mediator.meldinger.hendelser

import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.db.AvviksvurderingDao
import no.nav.helse.januar
import no.nav.helse.modell.avviksvurdering.Avviksvurdering
import no.nav.helse.modell.avviksvurdering.BeregningsgrunnlagDto
import no.nav.helse.modell.avviksvurdering.InnrapportertInntektDto
import no.nav.helse.modell.avviksvurdering.InntektDto
import no.nav.helse.modell.avviksvurdering.OmregnetÅrsinntektDto
import no.nav.helse.modell.avviksvurdering.SammenligningsgrunnlagDto
import no.nav.helse.objectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AvsluttetMedVedtakMessageTest {

    @Test
    fun mapper() {
        val forventet = avviksvurdering
        val avviksvurderingDao = mockk<AvviksvurderingDao> {
            every { finnAvviksvurderinger(any()) } returns listOf(avviksvurdering)
        }
        val a = AvsluttetMedVedtakMessage(objectMapper.readTree(json), avviksvurderingDao)
        assertEquals(forventet, a.fødselsnummer())
    }

    @Language("json")
    private val json = """
        {
          "@event_name": "avsluttet_med_vedtak",
          "organisasjonsnummer": "987654321",
          "vedtaksperiodeId": "${UUID.randomUUID()}",
          "fom": "2018-01-01",
          "tom": "2018-01-31",
          "hendelser": [
            "${UUID.randomUUID()}"
          ],
          "skjæringstidspunkt": "2018-01-01",
          "sykepengegrunnlag": 700000.0,
          "grunnlagForSykepengegrunnlag": 700000.0,
          "grunnlagForSykepengegrunnlagPerArbeidsgiver": {
            "987654321": 700000.0
          },
          "sykepengegrunnlagsfakta": {
            "fastsatt": "EtterHovedregel",
            "omregnetÅrsinntekt": 800000.00,
            "innrapportertÅrsinntekt": 755555.00,
            "avviksprosent": 26.38,
            "6G": 711720,
            "tags": [
              "6GBegrenset"
            ],
            "arbeidsgivere": [
              {
                "arbeidsgiver": "987654321",
                "omregnetÅrsinntekt": 800000.00
              }
            ]
          },
          "begrensning": "ER_6G_BEGRENSET",
          "inntekt": 60000.0,
          "vedtakFattetTidspunkt": "2018-02-01T00:00:00.000",
          "utbetalingId": "${UUID.randomUUID()}",
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "2018-02-01T00:00:00.000",
          "aktørId": "1234567891011",
          "fødselsnummer": "12345678910",
          "tags": [
            "IngenNyArbeidsgiverperiode"
          ]
        }
    """.trimIndent()

    private val avviksvurdering = Avviksvurdering(
        unikId = UUID.randomUUID(),
        vilkårsgrunnlagId = null,
        fødselsnummer = "12345678910",
        skjæringstidspunkt = 1.januar,
        opprettet = LocalDateTime.now(),
        avviksprosent = 24.0,
        sammenligningsgrunnlag = SammenligningsgrunnlagDto(
            totalbeløp = 50000.0,
            innrapporterteInntekter = listOf(
                InnrapportertInntektDto(
                    arbeidsgiverreferanse = "000000000",
                    inntekter = listOf(
                        InntektDto(
                            årMåned = YearMonth.of(2018, 1),
                            beløp = 50000.0
                        )
                    )
                )
            )
        ),
        beregningsgrunnlag = BeregningsgrunnlagDto(
            totalbeløp = 120000.0,
            omregnedeÅrsinntekter = listOf(
                OmregnetÅrsinntektDto(
                    arbeidsgiverreferanse = "000000000",
                    beløp = 10000.0
                )
            )
        ),
    )

}
