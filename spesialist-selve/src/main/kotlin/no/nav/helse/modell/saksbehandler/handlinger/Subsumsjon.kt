package no.nav.helse.modell.saksbehandler.handlinger

import no.nav.helse.modell.saksbehandler.handlinger.dto.SubsumsjonDto
import no.nav.helse.spesialist.api.modell.SubsumsjonEvent

class Subsumsjon(
    private val paragraf: String,
    private val ledd: String? = null,
    private val bokstav: String? = null,
) {
    fun byggEvent() =
        SubsumsjonEvent(paragraf, ledd, bokstav)

    fun toDto() = SubsumsjonDto(paragraf = paragraf, ledd = ledd, bokstav = bokstav)
}