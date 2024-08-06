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

package org.modelix.model.api.roles

sealed interface IRoleReference {
    fun getRoleId(): IRoleId?
    fun getName(): String?
}

interface IPropertyReference : IRoleReference

sealed interface ILinkReference : IRoleReference

interface IChildLinkReference : ILinkReference

interface IReferenceLinkReference : ILinkReference

data class PropertyId(private val uid: String) : IPropertyId, IPropertyReference {
    override fun getUID() = uid
    override fun getRoleId() = this
    override fun getName() = null
}

data class ChildLinkId(private val uid: String) : IChildLinkId, IChildLinkReference {
    override fun getUID() = uid
    override fun getRoleId() = this
    override fun getName() = null
}

data class ReferenceLinkId(private val uid: String) : IReferenceLinkId, IReferenceLinkReference {
    override fun getUID() = uid
    override fun getRoleId() = this
    override fun getName() = null
}

data class PropertyName(private val name: String) : IPropertyReference {
    override fun getRoleId() = null
    override fun getName() = name
}

data class ChildLinkName(private val name: String?) : IChildLinkReference {
    override fun getRoleId() = null
    override fun getName() = name
}

data class ReferenceLinkName(private val name: String) : IReferenceLinkReference {
    override fun getRoleId() = null
    override fun getName() = name
}
