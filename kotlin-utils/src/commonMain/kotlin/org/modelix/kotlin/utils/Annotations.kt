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

/**
 * Marks declarations in Modelix that are **delicate** &mdash;
 * they have limited use-case and shall be used with care in general code.
 * Any use of a delicate declaration has to be carefully reviewed to make sure it is
 * properly used.
 * Carefully read documentation of any declaration marked as `DelicateModelixApi`.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is a delicate API and its use requires care." +
        " Make sure you fully read and understand documentation of the declaration that is marked as a delicate API.",
)
annotation class DelicateModelixApi
