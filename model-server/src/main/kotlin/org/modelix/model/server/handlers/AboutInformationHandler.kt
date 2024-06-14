/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server.handlers

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import org.modelix.api.operative.AboutInformation
import org.modelix.api.operative.Paths
import org.modelix.model.server.MODELIX_VERSION

object AboutInformationHandler {
    fun init(application: Application) {
        with(application) {
            routing {
                get<Paths.getAboutInformation> {
                    val about = AboutInformation(MODELIX_VERSION)
                    call.respond(about)
                }
            }
        }
    }
}
