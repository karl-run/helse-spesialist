package no.nav.helse.spesialist.api.varsel

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.spesialist.api.graphql.schema.VarselDTO
import no.nav.helse.spesialist.api.graphql.schema.VarselDTO.VarselvurderingDTO
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.AKTIV
import no.nav.helse.spesialist.api.varsel.Varsel.Varselstatus.INAKTIV

data class Varsel(
    private val generasjonId: UUID,
    private val definisjonId: UUID,
    private val kode: String,
    private val tittel: String,
    private val forklaring: String?,
    private val handling: String?,
    private val vurdering: Varselvurdering?,
) {
    internal companion object {
        internal fun Set<Varsel>.toDto(): Set<VarselDTO> {
            return map { it.toDto() }.toSet()
        }

        internal fun Set<Varsel>.antallIkkeVurderte(): Int {
            return filter { !it.erVurdert() }.size
        }

        internal fun Set<Varsel>.antallIkkeVurderteEkskludertBesluttervarsler(): Int {
            return filter { !it.erVurdert() && !it.erBeslutterVarsel() }.size
        }
    }

    internal fun toDto() = VarselDTO(
        generasjonId.toString(),
        definisjonId.toString(),
        kode,
        tittel,
        forklaring,
        handling,
        vurdering?.toDto()
    )

    private fun erBeslutterVarsel(): Boolean {
        return kode.startsWith("SB_BO_")
    }

    private fun erVurdert(): Boolean {
        return vurdering?.erIkkeVurdert() == false
    }

    data class Varselvurdering(
        private val ident: String,
        private val tidsstempel: LocalDateTime,
        private val status: Varselstatus,
    ) {
        internal fun erIkkeVurdert() = status == AKTIV

        internal fun toDto(): VarselvurderingDTO {
            if (status == INAKTIV) throw IllegalStateException("Sende INAKTIV til frontend støttes ikke")
            return VarselvurderingDTO(
                ident,
                tidsstempel.toString(),
                no.nav.helse.spesialist.api.graphql.schema.Varselstatus.valueOf(status.name)
            )
        }
        override fun equals(other: Any?): Boolean =
            this === other || (other is Varselvurdering
                    && javaClass == other.javaClass
                    && ident == other.ident
                    && tidsstempel.withNano(0) == other.tidsstempel.withNano(0)
                    && status == other.status)

        override fun hashCode(): Int {
            var result = ident.hashCode()
            result = 31 * result + status.hashCode()
            result = 31 * result + tidsstempel.withNano(0).hashCode()
            return result
        }
    }

    enum class Varselstatus {
        INAKTIV,
        AKTIV,
        VURDERT,
        GODKJENT,
        AVVIST,
    }
}