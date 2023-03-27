/*
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
package org.modelix.model.api

interface IModel : IReferenceResolutionScope, IModelList {
    override fun getModels(): List<IModel> = listOf(this)
    fun getRootNode(): INode?
    fun addListener(l: IModelListener)
    fun removeListener(l: IModelListener)
}

fun IModel.asModelList(): ModelList = ModelList(listOf(this))

interface IModelTransactionManager {
    fun <T> executeRead(f: () -> T): T
    fun <T> executeWrite(f: () -> T): T
    fun canRead(): Boolean
    fun canWrite(): Boolean
}

interface IModelListener {
    fun modelChanged(changes: List<ModelChangeEvent>)
}

class ModelChangeEvent