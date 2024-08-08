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

package org.modelix.model.api.async

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.area.IArea

class AsyncNodeAsNode(val node: IAsyncNode) : INode, INodeWithAsyncSupport {
    override fun getAsyncNode(): IAsyncNode = node

    override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
        TODO("Not yet implemented")
    }

    override fun getArea(): IArea {
        TODO("Not yet implemented")
    }

    override val isValid: Boolean
        get() = TODO("Not yet implemented")
    override val reference: INodeReference
        get() = TODO("Not yet implemented")
    override val concept: IConcept?
        get() = TODO("Not yet implemented")
    override val roleInParent: String?
        get() = TODO("Not yet implemented")
    override val parent: INode?
        get() = TODO("Not yet implemented")

    override fun getConceptReference(): IConceptReference? {
        TODO("Not yet implemented")
    }

    override fun getChildren(role: String?): Iterable<INode> {
        TODO("Not yet implemented")
    }

    override val allChildren: Iterable<INode>
        get() = TODO("Not yet implemented")

    override fun moveChild(role: String?, index: Int, child: INode) {
        TODO("Not yet implemented")
    }

    override fun removeChild(child: INode) {
        TODO("Not yet implemented")
    }

    override fun getReferenceTarget(role: String): INode? {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(role: String, target: INode?) {
        TODO("Not yet implemented")
    }

    override fun getPropertyValue(role: String): String? {
        TODO("Not yet implemented")
    }

    override fun setPropertyValue(role: String, value: String?) {
        TODO("Not yet implemented")
    }

    override fun getPropertyRoles(): List<String> {
        TODO("Not yet implemented")
    }

    override fun getReferenceRoles(): List<String> {
        TODO("Not yet implemented")
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode {
        TODO("Not yet implemented")
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode {
        TODO("Not yet implemented")
    }

    override fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode {
        TODO("Not yet implemented")
    }

    override fun addNewChildren(link: IChildLink, index: Int, concepts: List<IConceptReference?>): List<INode> {
        TODO("Not yet implemented")
    }

    override fun addNewChildren(role: String?, index: Int, concepts: List<IConceptReference?>): List<INode> {
        TODO("Not yet implemented")
    }

    override fun getAllChildrenAsFlow(): Flow<INode> {
        return node.getAllChildren().asFlow().flatMapConcat { it.asFlow() }.map { it.asRegularNode() }
    }

    override fun getAllProperties(): List<Pair<IProperty, String>> {
        TODO("Not yet implemented")
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLink, INodeReference>> {
        TODO("Not yet implemented")
    }

    override fun getAllReferenceTargetRefsAsFlow(): Flow<Pair<IReferenceLink, INodeReference>> {
        return node.getAllReferenceTargetRefs().asFlow().flatMapConcat { it.asFlow() }
            .map { it.first.toLegacy() to it.second }
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLink, INode>> {
        TODO("Not yet implemented")
    }

    override fun getAllReferenceTargetsAsFlow(): Flow<Pair<IReferenceLink, INode>> {
        return node.getAllReferenceTargets().asFlattenedFlow()
            .map { it.first.toLegacy() to it.second.asRegularNode() }
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        TODO("Not yet implemented")
    }

    override fun getChildrenAsFlow(role: IChildLink): Flow<INode> {
        return node.getChildren(role.toReference()).asFlattenedFlow().map { it.asRegularNode() }
    }

    override fun getContainmentLink(): IChildLink? {
        TODO("Not yet implemented")
    }

    override fun getDescendantsAsFlow(includeSelf: Boolean): Flow<INode> {
        TODO("Not yet implemented")
    }

    override fun getOriginalReference(): String? {
        TODO("Not yet implemented")
    }

    override fun getParentAsFlow(): Flow<INode> {
        return node.getParent().asFlow().filterNotNull().map { it.asRegularNode() }
    }

    override fun getPropertyLinks(): List<IProperty> {
        TODO("Not yet implemented")
    }

    override fun getPropertyValue(property: IProperty): String? {
        TODO("Not yet implemented")
    }

    override fun getPropertyValueAsFlow(role: IProperty): Flow<String?> {
        return node.getPropertyValue(role.toReference()).asFlow()
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        TODO("Not yet implemented")
    }

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        TODO("Not yet implemented")
    }

    override fun getReferenceTargetAsFlow(role: IReferenceLink): Flow<INode> {
        return node.getReferenceTarget(role.toReference()).asFlow().filterNotNull().map { it.asRegularNode() }
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        TODO("Not yet implemented")
    }

    override fun getReferenceTargetRef(role: String): INodeReference? {
        TODO("Not yet implemented")
    }

    override fun getReferenceTargetRefAsFlow(role: IReferenceLink): Flow<INodeReference> {
        return node.getReferenceTargetRef(role.toReference()).asFlow().filterNotNull()
    }

    override fun moveChild(role: IChildLink, index: Int, child: INode) {
        TODO("Not yet implemented")
    }

    override fun removeReference(role: IReferenceLink) {
        TODO("Not yet implemented")
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(role: String, target: INodeReference?) {
        TODO("Not yet implemented")
    }

    override fun tryGetConcept(): IConcept? {
        TODO("Not yet implemented")
    }

    override fun usesRoleIds(): Boolean {
        TODO("Not yet implemented")
    }
}
