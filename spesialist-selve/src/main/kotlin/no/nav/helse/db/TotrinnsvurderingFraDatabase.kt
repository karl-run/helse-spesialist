package no.nav.helse.db

import java.time.LocalDateTime
import java.util.UUID

class TotrinnsvurderingFraDatabase(
    val vedtaksperiodeId: UUID,
    val erRetur: Boolean,
    val saksbehandler: UUID?,
    val beslutter: UUID?,
    val utbetalingIdRef: Long?,
    val opprettet: LocalDateTime,
    val oppdatert: LocalDateTime?,
)