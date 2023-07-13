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

import io.ktor.server.html.Template
import io.ktor.server.html.TemplatePlaceholder
import org.modelix.modelql.core.IBoundFragment
import org.modelix.modelql.core.IFragmentBuilder
import org.modelix.modelql.core.IMonoStep

interface IModelQLTemplate<In, Context> {
    fun IFragmentBuilder<In, Context>.buildFragment()
}

class ModelQLTemplateInstance<Context, Template : IModelQLTemplate<*, Context>>(
    val template: Template,
    val fragment: IBoundFragment<Context>
)

context(IFragmentBuilder<In, Context>)
fun <In, Context, Template : IModelQLTemplate<In, Context>> IMonoStep<In>.buildTemplate(template: Template): ModelQLTemplateInstance<Context, Template> {
    val fragment = buildFragment<In, Context> {
        with(template) {
            buildFragment()
        }
    }
    return ModelQLTemplateInstance<Context, Template>(template, fragment)
}

context(IFragmentBuilder<*, *>)
fun <TTemplate : IModelQLTemplate<*, TOuter>, TOuter> TOuter.insert(
    templateInstance: ModelQLTemplateInstance<TOuter, TTemplate>,
    placeholder: TemplatePlaceholder<TTemplate>
) {
    placeholder.apply(templateInstance.template)
    insertFragment(templateInstance.fragment)
}

context(IFragmentBuilder<*, *>)
fun <TOuter, TTemplate : IModelQLTemplate<*, TOuter>> TOuter.insert(templateInstance: ModelQLTemplateInstance<TOuter, TTemplate>, build: TTemplate.() -> Unit) {
    templateInstance.template.build()
    insertFragment(templateInstance.fragment)
}

context(IFragmentBuilder<*, *>)
fun <TOuter> IBoundFragment<TOuter>.toKotlinTemplate(): Template<TOuter> {
    val preparedFragment = this
    return object : Template<TOuter> {
        override fun TOuter.apply() {
            insertFragment(preparedFragment)
        }
    }
}

context(IFragmentBuilder<*, *>)
fun <TOuter> ModelQLTemplateInstance<TOuter, *>.toKotlinTemplate(): Template<TOuter> {
    val templateInstance = this
    return object : Template<TOuter> {
        override fun TOuter.apply() {
            insertFragment(templateInstance.fragment)
        }
    }
}
