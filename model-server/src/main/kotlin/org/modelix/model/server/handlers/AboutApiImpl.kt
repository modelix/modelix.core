package org.modelix.model.server.handlers

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import org.modelix.model.server.MODELIX_VERSION

/**
 * Responding information about the model server.
 */
object AboutApiImpl : AboutApi() {
    override suspend fun PipelineContext<Unit, ApplicationCall>.getAboutInformationV1() {
        val about = AboutV1(MODELIX_VERSION)
        call.respond(about)
    }
}
