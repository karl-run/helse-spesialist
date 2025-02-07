package no.nav.helse.modell.person.vedtaksperiode

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Status.AKTIV
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Status.AVVIST
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Status.GODKJENT
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Status.INAKTIV
import no.nav.helse.modell.person.vedtaksperiode.Varsel.Status.VURDERT
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDateTime
import java.util.UUID

class Varsel(
    private val id: UUID,
    private val varselkode: String,
    private val opprettet: LocalDateTime,
    private val vedtaksperiodeId: UUID,
    private var status: Status = AKTIV,
) {
    enum class Status {
        AKTIV,
        INAKTIV,
        GODKJENT,
        VURDERT,
        AVVIST,
        AVVIKLET,
        ;

        fun toDto(): VarselStatusDto {
            return when (this) {
                AKTIV -> VarselStatusDto.AKTIV
                INAKTIV -> VarselStatusDto.INAKTIV
                GODKJENT -> VarselStatusDto.GODKJENT
                VURDERT -> VarselStatusDto.VURDERT
                AVVIST -> VarselStatusDto.AVVIST
                AVVIKLET -> VarselStatusDto.AVVIKLET
            }
        }
    }

    private val observers = mutableSetOf<IVedtaksperiodeObserver>()

    fun vedtaksperiodeId() = vedtaksperiodeId

    fun registrer(vararg observer: IVedtaksperiodeObserver) {
        observers.addAll(observer)
    }

    fun toDto(): VarselDto {
        return VarselDto(id, varselkode, opprettet, vedtaksperiodeId, status.toDto())
    }

    fun erAktiv(): Boolean = this.status == AKTIV

    fun erVarselOmAvvik(): Boolean {
        return this.varselkode == "RV_IV_2"
    }

    fun opprett(generasjonId: UUID) {
        observers.forEach { it.varselOpprettet(id, vedtaksperiodeId, generasjonId, varselkode, opprettet) }
    }

    fun reaktiver(generasjonId: UUID) {
        if (status != INAKTIV) return
        this.status = AKTIV
        observers.forEach { it.varselReaktivert(id, varselkode, generasjonId, vedtaksperiodeId) }
    }

    fun deaktiver(generasjonId: UUID) {
        if (status != AKTIV) return
        this.status = INAKTIV
        observers.forEach { it.varselDeaktivert(id, varselkode, generasjonId, vedtaksperiodeId) }
    }

    override fun toString(): String {
        return "varselkode=$varselkode, vedtaksperiodeId=$vedtaksperiodeId, status=${status.name}"
    }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is Varsel &&
                javaClass == other.javaClass &&
                id == other.id &&
                vedtaksperiodeId == other.vedtaksperiodeId &&
                opprettet.withNano(0) == other.opprettet.withNano(0) &&
                varselkode == other.varselkode
        )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + varselkode.hashCode()
        result = 31 * result + opprettet.withNano(0).hashCode()
        result = 31 * result + vedtaksperiodeId.hashCode()
        return result
    }

    fun erRelevantFor(vedtaksperiodeId: UUID): Boolean = this.vedtaksperiodeId == vedtaksperiodeId

    fun godkjennSpesialsakvarsel(generasjonId: UUID) {
        if (status == GODKJENT) return
        status = GODKJENT
        observers.forEach { it.varselGodkjent(id, varselkode, generasjonId, vedtaksperiodeId, "Automatisk godkjent - spesialsak") }
    }

    companion object {
        fun List<Varsel>.finnEksisterendeVarsel(varsel: Varsel): Varsel? {
            return find { it.varselkode == varsel.varselkode }
        }

        fun List<Varsel>.finnEksisterendeVarsel(varselkode: String): Varsel? {
            return find { it.varselkode == varselkode }
        }

        fun List<Varsel>.inneholderMedlemskapsvarsel(): Boolean {
            return any { it.status == AKTIV && it.varselkode == "RV_MV_1" }
        }

        fun List<Varsel>.inneholderVarselOmNegativtBeløp(): Boolean {
            return any { it.status == AKTIV && it.varselkode == "RV_UT_23" }
        }

        fun List<Varsel>.inneholderAktivtVarselOmAvvik(): Boolean {
            return any { it.status == AKTIV && it.varselkode == "RV_IV_2" }
        }

        fun List<Varsel>.inneholderVarselOmAvvik(): Boolean {
            return any { it.varselkode == "RV_IV_2" }
        }

        fun List<Varsel>.inneholderVarselOmTilbakedatering(): Boolean {
            return any { it.status == AKTIV && it.varselkode == "RV_SØ_3" }
        }

        fun List<Varsel>.inneholderSvartelistedeVarsler(): Boolean {
            return any { it.varselkode in neiVarsler }
        }

        fun List<Varsel>.automatiskGodkjennSpesialsakvarsler(generasjonId: UUID) {
            forEach { it.godkjennSpesialsakvarsel(generasjonId) }
        }

        private val neiVarsler =
            listOf(
                "RV_IT_3",
                "RV_SI_3",
                "RV_UT_23",
                "RV_VV_8",
                "SB_RV_2",
                "SB_RV_3",
            )

        fun List<Varsel>.forhindrerAutomatisering() = any { it.status in listOf(VURDERT, AKTIV, AVVIST) }

        fun JsonNode.varsler(): List<Varsel> {
            return this
                .filter { it["nivå"].asText() == "VARSEL" && it["varselkode"]?.asText() != null }
                .filter { it["kontekster"].any { kontekst -> kontekst["konteksttype"].asText() == "Vedtaksperiode" } }
                .map { jsonNode ->
                    val vedtaksperiodeId =
                        UUID.fromString(
                            jsonNode["kontekster"]
                                .find { it["konteksttype"].asText() == "Vedtaksperiode" }!!["kontekstmap"]
                                .get("vedtaksperiodeId").asText(),
                        )
                    Varsel(
                        UUID.fromString(jsonNode["id"].asText()),
                        jsonNode["varselkode"].asText(),
                        jsonNode["tidsstempel"].asLocalDateTime(),
                        vedtaksperiodeId,
                    )
                }
        }
    }
}
