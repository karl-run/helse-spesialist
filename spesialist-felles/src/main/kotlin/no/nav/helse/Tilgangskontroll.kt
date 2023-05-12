package no.nav.helse

import java.util.UUID

// Definisjoner på gruppene vi har et forhold til, og deres runtime UUID-er

enum class Gruppe(private val gruppenøkkel: String) {
    RISK_QA("RISK_SUPERSAKSBEHANDLER_GROUP"),
    KODE7("KODE7_SAKSBEHANDLER_GROUP"),
    BESLUTTER("BESLUTTER_SAKSBEHANDLER_GROUP"),
    SKJERMEDE("SKJERMEDE_PERSONER_GROUP"),
    STIKKPRØVE("STIKKPRØVER_GROUP");

    fun idFra(env: Map<String, String>): UUID = UUID.fromString(env.getValue(gruppenøkkel))

    companion object {
        fun __indreInnhold_kunForTest() = values().associate { it.name to it.gruppenøkkel }
    }
}

class Tilgangsgrupper(private val env: Map<String, String>) {
    fun gruppeId(gruppe: Gruppe): UUID = gruppe.idFra(env)
    fun harTilgang(gruppemedlemskap: List<UUID>, gruppe: Gruppe) = gruppemedlemskap.contains(gruppeId(gruppe))
}
