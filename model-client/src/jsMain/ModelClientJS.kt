/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.modelix.model.ModelFacade
import org.modelix.model.api.NodeAdapterJS
import org.modelix.model.data.ModelData

@JsExport
object ModelClientJS {
    fun loadModelsFromJson(json: Array<String>): INodeJS {
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        json.forEach { ModelData.fromJson(it).load(branch)  }
        val rootNode = ModelFacade.getRootNode(branch)
        return NodeAdapterJS(rootNode)
    }
}
