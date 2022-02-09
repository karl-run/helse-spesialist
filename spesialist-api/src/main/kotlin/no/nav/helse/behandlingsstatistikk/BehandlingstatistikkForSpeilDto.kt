package no.nav.helse.behandlingsstatistikk

data class BehandlingstatistikkForSpeilDto(
    val antallOppgaverTilGodkjenning: OppgavestatistikkForSpeilDto,
    val antallTildelteOppgaver: OppgavestatistikkForSpeilDto,
    val fullførteBehandlinger: BehandlingerForSpeilDto
) {

    data class PerPeriodetype(
        val periodetypeForSpeil: PeriodetypeForSpeil,
        val antall: Int
    )

    data class OppgavestatistikkForSpeilDto(
        val totalt: Int,
        val perPeriodetype: List<PerPeriodetype>
    )

    data class BehandlingerForSpeilDto(
        val totalt: Int,
        val annulleringer: Int,
        val manuelt: Int,
        val automatisk: Int
    )

    enum class PeriodetypeForSpeil {
        FØRSTEGANGSBEHANDLING,
        FORLENGELSE,
        OVERGANG_FRA_IT,
        UTBETALING_TIL_SYKMELDT,
        DELVIS_REFUSJON
    }

    companion object {
        fun toSpeilMap(behandlingsstatistikkDto: BehandlingsstatistikkDto) = BehandlingstatistikkForSpeilDto(
            antallOppgaverTilGodkjenning = OppgavestatistikkForSpeilDto(
                totalt = behandlingsstatistikkDto.oppgaverTilGodkjenning.totalt,
                perPeriodetype = behandlingsstatistikkDto.oppgaverTilGodkjenning.perPeriodetype.toPerStatistikktypeForSpeil()
            ),
            antallTildelteOppgaver = OppgavestatistikkForSpeilDto(
                totalt = behandlingsstatistikkDto.tildelteOppgaver.totalt,
                perPeriodetype = behandlingsstatistikkDto.tildelteOppgaver.perPeriodetype.toPerStatistikktypeForSpeil()
            ),
            fullførteBehandlinger = BehandlingerForSpeilDto(
                totalt = behandlingsstatistikkDto.fullførteBehandlinger.totalt,
                annulleringer = behandlingsstatistikkDto.fullførteBehandlinger.annullert,
                manuelt = behandlingsstatistikkDto.fullførteBehandlinger.manuelt,
                automatisk = behandlingsstatistikkDto.fullførteBehandlinger.automatisk,
            )
        )

        private fun List<Pair<BehandlingsstatistikkType, Int>>.toPerStatistikktypeForSpeil() =
            groupBy { toPeriodetypeForSpeil(it.first) }
            .map { (key, value) ->
                PerPeriodetype(key, value.sumOf { it.second })
            }

        private fun toPeriodetypeForSpeil(behandlingsstatistikkType: BehandlingsstatistikkType) = when (behandlingsstatistikkType) {
            BehandlingsstatistikkType.FORLENGELSE,
            BehandlingsstatistikkType.INFOTRYGDFORLENGELSE -> PeriodetypeForSpeil.FORLENGELSE
            BehandlingsstatistikkType.FØRSTEGANGSBEHANDLING -> PeriodetypeForSpeil.FØRSTEGANGSBEHANDLING
            BehandlingsstatistikkType.OVERGANG_FRA_IT -> PeriodetypeForSpeil.OVERGANG_FRA_IT
            BehandlingsstatistikkType.UTBETALING_TIL_SYKMELDT -> PeriodetypeForSpeil.UTBETALING_TIL_SYKMELDT
            BehandlingsstatistikkType.DELVIS_REFUSJON -> PeriodetypeForSpeil.DELVIS_REFUSJON
        }
    }
}

