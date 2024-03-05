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

package org.modelix.model.mpsadapters

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.IRole

// statically ensures that receiver and parameter are of the same type
internal fun IChildLink.conformsTo(other: IChildLink) = conformsToRole(other)
internal fun IReferenceLink.conformsTo(other: IReferenceLink) = conformsToRole(other)
internal fun IProperty.conformsTo(other: IProperty) = conformsToRole(other)

private fun IRole.conformsToRole(other: IRole): Boolean {
    return this.getUID().endsWith(other.getUID()) ||
        this.getUID().contains(other.getSimpleName()) ||
        this.getSimpleName() == other.getSimpleName()
}
