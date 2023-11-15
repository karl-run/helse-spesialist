package no.nav.helse.modell.egenansatt

import java.time.LocalDateTime
import javax.sql.DataSource
import no.nav.helse.DbQueries
import no.nav.helse.query

class EgenAnsattDao(dataSource: DataSource) {
    private val dbQueries = DbQueries(dataSource)

    internal fun lagre(fødselsnummer: String, erEgenAnsatt: Boolean, opprettet: LocalDateTime) {
        val query = query(
            """
            INSERT INTO egen_ansatt (person_ref, er_egen_ansatt, opprettet)
            VALUES (
                (SELECT id FROM person WHERE fodselsnummer = :fodselsnummer), :er_egen_ansatt, :opprettet
            )
            ON CONFLICT (person_ref) DO UPDATE SET er_egen_ansatt = :er_egen_ansatt, opprettet = :opprettet
        """.trimIndent(),
            "fodselsnummer" to fødselsnummer.toLong(),
            "er_egen_ansatt" to erEgenAnsatt,
            "opprettet" to opprettet
        )

        dbQueries.run { query.update() }
    }

    internal fun erEgenAnsatt(fødselsnummer: String): Boolean? {
        val query = query(
            """
            SELECT er_egen_ansatt
            FROM egen_ansatt ea
            INNER JOIN person p on p.id = ea.person_ref
            WHERE p.fodselsnummer = :fodselsnummer
        """.trimIndent(), "fodselsnummer" to fødselsnummer.toLong()
        )

        return dbQueries.run { query.single { it.boolean("er_egen_ansatt") } }
    }
}
