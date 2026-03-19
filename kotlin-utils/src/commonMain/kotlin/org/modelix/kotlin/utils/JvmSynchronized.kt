package org.modelix.kotlin.utils

import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER

@OptIn(ExperimentalMultiplatform::class)
@Target(FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
@MustBeDocumented
@OptionalExpectation
expect annotation class JvmSynchronized()
