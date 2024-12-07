package org.modelix.model.server.api

import kotlinx.serialization.Serializable

@Serializable
data class ExceptionData(
    val message: String,
    val stacktrace: List<String>,
    val cause: ExceptionData? = null,
) {
    constructor(exception: Throwable) : this(
        exception.message ?: "",
        exception.stackTraceToString().lines(),
        if (exception.cause == exception) null else exception.cause?.let { ExceptionData(it) },
    )

    fun allMessages() = generateSequence(this) { it.cause }.map { it.message }

    override fun toString(): String {
        return stacktrace.joinToString("\n")
    }
}
