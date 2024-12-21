package org.modelix.model.server.handlers

import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.modelix.model.server.MODELIX_VERSION

/**
 * Responding information about the model server.
 */
object AboutApiImpl : AboutApi() {
    override suspend fun RoutingContext.getAboutInformationV1() {
        val about = AboutV1(MODELIX_VERSION)
        call.respond(about)
    }
}
