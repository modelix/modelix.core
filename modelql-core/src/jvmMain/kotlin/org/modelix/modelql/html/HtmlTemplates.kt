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
package org.modelix.modelql.html

import io.ktor.server.html.TemplatePlaceholder
import org.modelix.modelql.core.IAsyncBuilder
import org.modelix.modelql.core.IModelQLTemplate
import org.modelix.modelql.core.IModelQLTemplateInstance
import org.modelix.modelql.core.IMonoStep

context(IAsyncBuilder<*, *>)
public fun <TTemplate : IModelQLTemplate<*, TOuter>, TTemplateInstance : IModelQLTemplateInstance<TOuter, TTemplate>, TOuter> TOuter.insert(
    templateInstance: TTemplateInstance,
    placeholder: TemplatePlaceholder<TTemplate>
) {
    placeholder.apply(templateInstance.getTemplate())
    applyTemplate(templateInstance)
}

context(IAsyncBuilder<*, *>)
public fun <TOuter, TTemplate : IModelQLTemplate<*, TOuter>, TTemplateInstance : IModelQLTemplateInstance<TOuter, TTemplate>> TOuter.insert(templateInstance: TTemplateInstance, build: TTemplate.() -> Unit) {
    templateInstance.getTemplate().build()
    applyTemplate(templateInstance)
}
