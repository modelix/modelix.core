package org.modelix.model.server.handlers

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
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
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/**
 * REST API implementation for providing micrometer metrics.
 */
class MetricsApiImpl : MetricsApi() {

    private val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    fun init(application: Application) {
        application.apply {
            install(MicrometerMetrics) {
                registry = appMicrometerRegistry
                distributionStatisticConfig = DistributionStatisticConfig.Builder()
                    .percentilesHistogram(true)
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
                installRoutes(this)
            }
        }
    }

    override suspend fun RoutingContext.getMetrics() {
        call.respond(appMicrometerRegistry.scrape())
    }
}
