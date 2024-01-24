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
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.modelix.api.public.Paths
import java.time.Duration

class MetricsHandler() {

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    fun init(application: Application) {
        application.apply {
            install(MicrometerMetrics) {
                registry = appMicrometerRegistry
                distributionStatisticConfig = DistributionStatisticConfig.Builder()
                    .percentilesHistogram(true)
                    .maximumExpectedValue(Duration.ofSeconds(20).toNanos().toDouble())
                    .serviceLevelObjectives(
                        Duration.ofMillis(100).toNanos().toDouble(),
                        Duration.ofMillis(500).toNanos().toDouble(),
                    )
                    .build()
                meterBinders = listOf(
                    // jvm
                    ClassLoaderMetrics(),
                    JvmGcMetrics(),
                    JvmInfoMetrics(),
                    JvmMemoryMetrics(),
                    JvmThreadMetrics(),
                    // system
                    FileDescriptorMetrics(),
                    ProcessorMetrics(),
                    UptimeMetrics(),
                )
            }

            routing {
                installHandlers()
            }
        }
    }

    private fun Route.installHandlers() {
        get<Paths.getMetrics> {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}
