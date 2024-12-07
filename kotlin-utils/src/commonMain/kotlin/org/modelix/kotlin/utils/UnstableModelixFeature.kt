package org.modelix.kotlin.utils

/**
 * Marks an API as unstable.
 *
 * @param reason Describes why the feature is experimental.
 * @param intendedFinalization Describes when this API is intended to be
 *        finalized or removed. This field is intended to capture a condition and
 *        progress metadata such as a ticket number describing when the feature
 *        becomes ready. It's not meant to capture a date or specific release.
 */
@RequiresOptIn(message = "This API is experimental. It may be changed in the future without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPEALIAS,
)
annotation class UnstableModelixFeature(
    val reason: String,
    val intendedFinalization: String,
)
