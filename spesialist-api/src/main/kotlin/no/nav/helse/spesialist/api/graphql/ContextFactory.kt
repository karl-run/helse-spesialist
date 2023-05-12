@file:Suppress("DEPRECATION")

package no.nav.helse.spesialist.api.graphql

import com.expediagroup.graphql.generator.execution.GraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.ApplicationRequest
import no.nav.helse.Tilgangsgrupper
import no.nav.helse.felles.tilganger

data class AuthorizedContext(val kanSeKode7: Boolean) : GraphQLContext

class ContextFactory(
    val tilgangsgrupper: Tilgangsgrupper
) : GraphQLContextFactory<AuthorizedContext, ApplicationRequest> {

    override suspend fun generateContextMap(request: ApplicationRequest): Map<String, Any> =
        mapOf(
            "tilganger" to request.tilganger(tilgangsgrupper),
            "saksbehandlerNavn" to request.getSaksbehandlerName()
        )

    @Deprecated(
        "The generic context object is deprecated in favor of the context map",
        replaceWith = ReplaceWith("generateContextMap(request)")
    )
    override suspend fun generateContext(request: ApplicationRequest): AuthorizedContext {
        return AuthorizedContext(false)
    }
}

private fun ApplicationRequest.getSaksbehandlerName(): String {
    val accessToken = call.principal<JWTPrincipal>()
    return accessToken?.payload?.getClaim("name")?.asString() ?: ""
}
