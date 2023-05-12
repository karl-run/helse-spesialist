package no.nav.helse

import java.util.UUID
import no.nav.helse.felles.ApiTilgangskontroll

val testEnv = Gruppe.__indreInnhold_kunForTest().values.associateWith { _ -> UUID.randomUUID().toString() }
fun idForGruppe(gruppe: Gruppe) = Tilgangsgrupper(testEnv).gruppeId(gruppe).toString()

fun lagApiTilgangskontroll(tilgangsgrupper: Tilgangsgrupper, gruppemedlemskap: List<UUID>) =
    ApiTilgangskontroll { gruppe ->
        Gruppe.values().associateWith { tilgangsgrupper.harTilgang(gruppemedlemskap, it) }[gruppe]!!
    }

fun medTilgangTil(vararg innloggetBrukersGrupper: Gruppe = emptyArray()) =
    lagApiTilgangskontroll(
        Tilgangsgrupper(testEnv),
        innloggetBrukersGrupper.map(::idForGruppe).map(UUID::fromString)
    )

fun utenNoenTilganger(vararg innloggetBrukersGrupper: String = emptyArray()) =
    lagApiTilgangskontroll(
        Tilgangsgrupper(testEnv),
        innloggetBrukersGrupper.map(UUID::fromString)
    )

