package no.nav.helse.modell.person

import no.nav.helse.modell.sykefraværstilfelle.SkjønnsfastsattSykepengegrunnlag
import no.nav.helse.modell.sykefraværstilfelle.SkjønnsfastsattSykepengegrunnlagDto
import no.nav.helse.modell.vedtaksperiode.vedtak.SykepengevedtakBuilder
import java.time.LocalDate

class SkjønnsfastsatteSykepengegrunnlag private constructor(
    private val skjønnsfastsatteSykepengegrunnlag: Map<LocalDate, List<SkjønnsfastsattSykepengegrunnlag>>,
) {
    internal fun toDto() = skjønnsfastsatteSykepengegrunnlag.flatMap { entry -> entry.value.map { it.toDto() } }

    internal fun byggVedtak(
        skjæringstidspunkt: LocalDate,
        sykepengevedtakBuilder: SykepengevedtakBuilder,
    ) {
        skjønnsfastsatteSykepengegrunnlag[skjæringstidspunkt]?.also { skjønnsfastsatteSykepengegrunnlag ->
            val skjønnsfastsattSykepengegrunnlag = skjønnsfastsatteSykepengegrunnlag.lastOrNull()
            skjønnsfastsattSykepengegrunnlag?.also {
                sykepengevedtakBuilder.skjønnsfastsattSykepengegrunnlag(it)
            }
        }
    }

    internal companion object {
        internal fun List<SkjønnsfastsattSykepengegrunnlagDto>.gjenopprett(): SkjønnsfastsatteSykepengegrunnlag {
            return SkjønnsfastsatteSykepengegrunnlag(
                this
                    .groupBy { it.skjæringstidspunkt }
                    .mapValues { (_, dtoer) ->
                        dtoer
                            .sortedBy { it.opprettet }
                            .map { SkjønnsfastsattSykepengegrunnlag.gjenopprett(it) }
                    },
            )
        }
    }
}
