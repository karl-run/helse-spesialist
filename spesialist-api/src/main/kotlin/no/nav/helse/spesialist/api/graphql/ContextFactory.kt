@file:Suppress("DEPRECATION")

package no.nav.helse.spesialist.api.graphql

import com.expediagroup.graphql.generator.execution.GraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.ApplicationRequest
import java.util.UUID
import no.nav.helse.spesialist.api.SaksbehandlerTilganger
import no.nav.helse.spesialist.api.saksbehandler.Saksbehandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")

data class AuthorizedContext(val kanSeKode7: Boolean) : GraphQLContext

class ContextFactory(
    private val kode7Saksbehandlergruppe: UUID,
    private val skjermedePersonerSaksbehandlergruppe: UUID,
    private val beslutterSaksbehandlergruppe: UUID,
    private val riskSaksbehandlergruppe: UUID,
    private val saksbehandlereMedTilgangTilStikkprøve: List<String>
) : GraphQLContextFactory<AuthorizedContext, ApplicationRequest> {

    override suspend fun generateContextMap(request: ApplicationRequest): Map<GraphQLKonteksttype, Any> =
        mapOf(
            GraphQLKonteksttype.Tilganger to SaksbehandlerTilganger(
                gruppetilganger = request.getGrupper(),
                saksbehandlerIdent = request.getSaksbehandlerIdent(),
                kode7Saksbehandlergruppe = kode7Saksbehandlergruppe,
                riskSaksbehandlergruppe = riskSaksbehandlergruppe,
                beslutterSaksbehandlergruppe = beslutterSaksbehandlergruppe,
                skjermedePersonerSaksbehandlergruppe = skjermedePersonerSaksbehandlergruppe,
                saksbehandlereMedTilgangTilStikkprøve = saksbehandlereMedTilgangTilStikkprøve
            ),
            GraphQLKonteksttype.Saksbehandlernavn to request.getSaksbehandlerName(),
            GraphQLKonteksttype.Saksbehandler to request.getSaksbehandler()
        )

    @Deprecated("The generic context object is deprecated in favor of the context map")
    override suspend fun generateContext(request: ApplicationRequest): AuthorizedContext {
        return AuthorizedContext(request.getGrupper().contains(kode7Saksbehandlergruppe))
    }
}

private fun ApplicationRequest.getGrupper(): List<UUID> {
    val accessToken = call.principal<JWTPrincipal>()
    if (accessToken == null) {
        sikkerlogg.error("Ingen access_token for graphql-kall")
        return emptyList()
    }
    return accessToken.payload.getClaim("groups")?.asList(String::class.java)?.map(UUID::fromString) ?: emptyList()
}

private fun ApplicationRequest.getSaksbehandlerName(): String {
    val accessToken = call.principal<JWTPrincipal>()
    return accessToken?.payload?.getClaim("name")?.asString() ?: ""
}

private fun ApplicationRequest.getSaksbehandlerIdent(): String {
    val accessToken = call.principal<JWTPrincipal>()
    return accessToken?.payload?.getClaim("NAVident")?.asString() ?: ""
}

private fun ApplicationRequest.getSaksbehandler(): Saksbehandler {
    val accessToken = call.principal<JWTPrincipal>()
    return accessToken?.let(Saksbehandler::fraOnBehalfOfToken) ?: throw IllegalAccessException("Mangler access token")
}
