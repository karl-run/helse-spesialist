package no.nav.helse.spesialist.api.varsel

import no.nav.helse.spesialist.api.graphql.schema.VarselDTO
import no.nav.helse.spesialist.api.graphql.schema.VarselDTO.VarselvurderingDTO
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.AKTIV
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.AVVIST
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.GODKJENT
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.VURDERT
import java.time.LocalDateTime
import java.util.UUID

data class Varsel(
    private val varselId: UUID,
    private val generasjonId: UUID,
    private val definisjonId: UUID,
    private val opprettet: LocalDateTime,
    private val kode: String,
    private var status: Varselstatus,
    private val tittel: String,
    private val forklaring: String?,
    private val handling: String?,
    private val vurdering: Varselvurdering?,
) {
    internal companion object {
        internal fun Set<Varsel>.toDto(): Set<VarselDTO> {
            return map { it.toDto() }.toSet()
        }
    }

    internal fun toDto() =
        VarselDTO(
            generasjonId,
            definisjonId,
            opprettet.toString(),
            kode,
            tittel,
            forklaring,
            handling,
            vurdering?.toDto(status),
        )

    internal fun erAktiv(): Boolean {
        return status == AKTIV
    }

    internal fun vurder(
        godkjent: Boolean,
        fødselsnummer: String,
        behandlingId: UUID,
        vedtaksperiodeId: UUID,
        ident: String,
        vurderer: (
            fødselsnummer: String,
            behandlingId: UUID,
            vedtaksperiodeId: UUID,
            varselId: UUID,
            varselTittel: String,
            varselkode: String,
            forrigeStatus: Varselstatus,
            gjeldendeStatus: Varselstatus,
            saksbehandlerIdent: String,
        ) -> Unit,
    ) {
        if (status !in (listOf(AKTIV, VURDERT))) return

        val forrigeStatus = status
        status = if (godkjent) GODKJENT else AVVIST
        vurderer(fødselsnummer, behandlingId, vedtaksperiodeId, varselId, tittel, kode, forrigeStatus, status, ident)
    }

    data class Varselvurdering(
        private val ident: String,
        private val tidsstempel: LocalDateTime,
    ) {
        internal fun toDto(status: Varselstatus) =
            VarselvurderingDTO(
                ident,
                tidsstempel.toString(),
                no.nav.helse.spesialist.api.graphql.schema.Varselstatus.valueOf(status.name),
            )
    }

    enum class Varselstatus {
        INAKTIV,
        AKTIV,
        VURDERT,
        GODKJENT,
        AVVIST,
    }
}
