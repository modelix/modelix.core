import org.modelix.model.api.INode

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

@JsExport
interface INodeJS {
    fun unwrap(): INode
    fun getConcept(): IConceptJS?
    fun getConceptUID(): String?
    fun getReference(): INodeReferenceJS
    fun getRoleInParent(): String?
    fun getParent(): INodeJS?
    fun getChildren(role: String?): Array<INodeJS>
    fun getAllChildren(): Array<INodeJS>
    fun moveChild(role: String?, index: Int, child: INodeJS)
    fun addNewChild(role: String?, index: Int, concept: IConceptJS?): INodeJS
    fun removeChild(child: INodeJS)
    fun getReferenceRoles(): Array<String>
    fun getReferenceTargetNode(role: String): INodeJS?
    fun getReferenceTargetRef(role: String): INodeReferenceJS?
    fun setReferenceTargetNode(role: String, target: INodeJS?)
    fun setReferenceTargetRef(role: String, target: INodeReferenceJS?)
    fun getPropertyRoles(): Array<String>
    fun getPropertyValue(role: String): String?
    fun setPropertyValue(role: String, value: String?)
}
