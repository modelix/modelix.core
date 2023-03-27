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

interface IModelList : IReferenceResolutionScope {
    fun getModels(): List<IModel>
    fun getTransactionManager(): IModelTransactionManager
}

fun IModelList.concat(other: IModelList): IModelList = ModelList(getModels() + other.getModels())

class ModelList(private val models: List<IModel>) : IModelList, IModelTransactionManager {

    override fun getModels(): List<IModel> {
        return models
    }

    override fun getTransactionManager(): IModelTransactionManager = this

    override fun resolveNode(ref: INodeReference): INode? {
        return models.asSequence().mapNotNull { it.resolveNode(ref) }.firstOrNull()
    }

    override fun <T> executeRead(f: () -> T): T {
        return IReferenceResolutionScope.contextScope.offer(this) { readAll(models.iterator(), f) }
    }

    override fun <T> executeWrite(f: () -> T): T {
        return IReferenceResolutionScope.contextScope.offer(this) { writeAll(models.iterator(), f) }
    }

    private fun <T> readAll(modelsIterator: Iterator<IModel>, f: () -> T): T {
        return if (modelsIterator.hasNext()) {
            modelsIterator.next().getTransactionManager().executeRead {
                readAll(modelsIterator, f)
            }
        } else {
            f()
        }
    }

    private fun <T> writeAll(modelsIterator: Iterator<IModel>, f: () -> T): T {
        return if (modelsIterator.hasNext()) {
            modelsIterator.next().getTransactionManager().executeWrite {
                writeAll(modelsIterator, f)
            }
        } else {
            f()
        }
    }

    override fun canRead(): Boolean {
        return models.all { it.getTransactionManager().canRead() }
    }

    override fun canWrite(): Boolean {
        return models.all { it.getTransactionManager().canWrite() }
    }
}
