/*
 * Copyright (c) 2023.
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

package org.modelix.kotlin.utils

/**
 * Stores information on [Deprecated] code. This information is intended to facilitate performing the deprecation cycle.
 *
 * @property since an information since when the construct is deprecated. Preferably, use an ISO 8601 data such as
 *                 2022-01-09
 * @property removalHint an optional hint on when it is ok to remove the deprecated code. For instance, this property
 *                       can include known uses of the deprecated construct that have to be checked before removing the
 *                       deprecated code.
 */
// The target list is copied from @Deprecated to ensure this annotation is usable in the same places.
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
// This is just metadata to be read by developers. No need for bloating the binary code with this information.
@Retention(AnnotationRetention.SOURCE)
annotation class DeprecationInfo(
    val since: String,
    val removalHint: String = "",
)
