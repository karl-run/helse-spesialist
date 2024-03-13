package no.nav.helse.modell.vedtaksperiode

import java.util.UUID
import no.nav.helse.modell.varsel.Varsel
import no.nav.helse.modell.varsel.VarselStatusDto

internal class Vedtaksperiode private constructor(
    private val vedtaksperiodeId: UUID,
    private val organisasjonsnummer: String,
    private var forkastet: Boolean,
    generasjoner: List<Generasjon>
) {
    private val generasjoner = generasjoner.toMutableList()
    private val gjeldendeGenerasjon get() = generasjoner.last()
    private val fom get() = gjeldendeGenerasjon.fom()
    private val tom get() = gjeldendeGenerasjon.tom()

    fun vedtaksperiodeId() = vedtaksperiodeId

    internal fun toDto(): VedtaksperiodeDto {
        return VedtaksperiodeDto(
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            forkastet = forkastet,
            generasjoner = generasjoner.map { it.toDto() })
    }

    internal fun behandleTilbakedateringGodkjent(perioder: List<Periode>) {
        if (perioder.none { it.overlapperMed(Periode(fom, tom)) }) return
        deaktiverVarselMedKode("RV_SØ_3")
    }

    private fun deaktiverVarselMedKode(varselkode: String) {
        gjeldendeGenerasjon.deaktiverVarsel(varselkode)
    }

    internal fun håndter(spleisVedtaksperioder: List<SpleisVedtaksperiode>) {
        val spleisVedtaksperiode = spleisVedtaksperioder.find { it.erRelevant(vedtaksperiodeId) } ?: return
        gjeldendeGenerasjon.håndter(spleisVedtaksperiode)
    }

    internal fun nySpleisBehandling(spleisBehandling: SpleisBehandling) {
        if (!spleisBehandling.erRelevantFor(vedtaksperiodeId)) return
        gjeldendeGenerasjon.nySpleisBehandling(this, spleisBehandling)
    }

    internal fun nyGenerasjon(generasjon: Generasjon) {
        generasjoner.addLast(generasjon)
    }

    internal fun vedtakFattet(meldingId: UUID) {
        if (forkastet) return
        gjeldendeGenerasjon.håndterVedtakFattet(meldingId)
    }

    internal fun vedtaksperiodeForkastet() {
        forkastet = true
    }

    internal fun nyeVarsler(nyeVarsler: List<Varsel>) {
        val varsler = nyeVarsler.filter { it.erRelevantFor(vedtaksperiodeId) }
        if (varsler.isEmpty()) return
        varsler.forEach { gjeldendeGenerasjon.håndterNyttVarsel(it, UUID.randomUUID()) }
    }

    internal fun mottaBehandlingsinformasjon(tags: List<String>, spleisBehandlingId: UUID) =
        gjeldendeGenerasjon.oppdaterBehandlingsinformasjon(tags, spleisBehandlingId)

    companion object {
        fun nyVedtaksperiode(spleisBehandling: SpleisBehandling): Vedtaksperiode {
            return Vedtaksperiode(
                vedtaksperiodeId = spleisBehandling.vedtaksperiodeId,
                organisasjonsnummer = spleisBehandling.organisasjonsnummer,
                generasjoner = listOf(
                    Generasjon(
                        id = spleisBehandling.vedtaksperiodeId,
                        vedtaksperiodeId = spleisBehandling.vedtaksperiodeId,
                        fom = spleisBehandling.fom,
                        tom = spleisBehandling.tom,
                        skjæringstidspunkt = spleisBehandling.fom // Spleis sender oss ikke skjæringstidspunkt på dette tidspunktet
                    )
                ),
                forkastet = false
            )
        }

        fun gjenopprett(
            organisasjonsnummer: String,
            vedtaksperiodeId: UUID,
            forkastet: Boolean,
            generasjoner: List<GenerasjonDto>,
        ): Vedtaksperiode {
            check(generasjoner.isNotEmpty()) { "En vedtaksperiode uten generasjoner skal ikke være mulig" }
            return Vedtaksperiode(
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                forkastet = forkastet,
                generasjoner = generasjoner.map { it.tilGenerasjon() }
            )
        }

        private fun GenerasjonDto.tilGenerasjon(): Generasjon {
            return Generasjon.fraLagring(
                id = id,
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingId = utbetalingId,
                spleisBehandlingId = spleisBehandlingId,
                skjæringstidspunkt = skjæringstidspunkt,
                fom = fom,
                tom = tom,
                tilstand = when (tilstand) {
                    TilstandDto.Låst -> Generasjon.Låst
                    TilstandDto.Ulåst -> Generasjon.Ulåst
                    TilstandDto.AvsluttetUtenUtbetaling -> Generasjon.AvsluttetUtenUtbetaling
                    TilstandDto.UtenUtbetalingMåVurderes -> Generasjon.UtenUtbetalingMåVurderes
                },
                tags = tags.toList(),
                varsler = varsler.map { varselDto ->
                    Varsel(
                        id = varselDto.id,
                        varselkode = varselDto.varselkode,
                        opprettet = varselDto.opprettet,
                        vedtaksperiodeId = varselDto.vedtaksperiodeId,
                        status = when (varselDto.status) {
                            VarselStatusDto.AKTIV -> Varsel.Status.AKTIV
                            VarselStatusDto.INAKTIV -> Varsel.Status.INAKTIV
                            VarselStatusDto.GODKJENT -> Varsel.Status.GODKJENT
                            VarselStatusDto.VURDERT -> Varsel.Status.VURDERT
                            VarselStatusDto.AVVIST -> Varsel.Status.AVVIST
                            VarselStatusDto.AVVIKLET -> Varsel.Status.AVVIKLET
                        }
                    )
                }.toSet()
            )
        }
    }
}
