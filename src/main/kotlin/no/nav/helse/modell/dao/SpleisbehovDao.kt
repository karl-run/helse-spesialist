package no.nav.helse.modell.dao

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.modell.dto.SpleisbehovDBDto
import java.util.UUID
import javax.sql.DataSource

internal class SpleisbehovDao(private val dataSource: DataSource) {

    internal fun insertBehov(id: UUID, spleisReferanse: UUID, behov: String, original: String) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "INSERT INTO spleisbehov(id, spleis_referanse, data, original) VALUES(?, ?, CAST(? as json), CAST(? as json))",
                    id,
                    spleisReferanse,
                    behov,
                    original
                ).asUpdate
            )

        }
    }

    internal fun updateBehov(id: UUID, behov: String) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "UPDATE spleisbehov SET data=CAST(? as json) WHERE id=?", behov, id
                ).asUpdate
            )
        }
    }

    internal fun findBehov(id: UUID): SpleisbehovDBDto? = using(sessionOf(dataSource)) { session ->
        session.run(queryOf("SELECT spleis_referanse, data FROM spleisbehov WHERE id=?", id)
            .map {
                SpleisbehovDBDto(
                    id = id,
                    spleisReferanse = UUID.fromString(it.string("spleis_referanse")),
                    data = it.string("data")
                )
            }
            .asSingle
        )
    }

    internal fun findBehovMedSpleisReferanse(spleisReferanse: UUID): SpleisbehovDBDto? = using(sessionOf(dataSource)) { session ->
        session.run(queryOf("SELECT  id, data FROM spleisbehov WHERE id=?", spleisReferanse)
            .map {
                SpleisbehovDBDto(
                    id = UUID.fromString(it.string("id")),
                    spleisReferanse = spleisReferanse,
                    data = it.string("data")
                )
            }
            .asSingle
        )
    }

    internal fun findOriginalBehov(id: UUID): String? = using(sessionOf(dataSource)) { session ->
        session.run(queryOf("SELECT original FROM spleisbehov WHERE id=?", id)
            .map { it.string("original") }
            .asSingle)
    }
}
