/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.modelix.modelql.core.IBoundFragment
import org.modelix.modelql.core.IFragmentBuilder
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IMonoUnboundQuery
import org.modelix.modelql.core.IRecursiveFragmentBuilder
import org.modelix.modelql.core.IRequestedFragment
import org.modelix.modelql.core.IUnboundQuery
import org.modelix.modelql.core.bindFragment
import org.modelix.modelql.core.mapLocal

interface IModelQLTemplate<in In, in Context> {
    fun IFragmentBuilder<In, Context>.buildFragment()
}

class ModelQLTemplateInstance<in Context, out Template : IModelQLTemplate<*, Context>>(
    val template: Template,
    val fragment: IRequestedFragment<Context>,
)

context(IFragmentBuilder<*, *>)
fun <In, Context, Template : IModelQLTemplate<In, Context>> IMonoStep<In>.requestTemplate(template: Template): ModelQLTemplateInstance<Context, Template> {
    val fragment = requestFragment<In, Context>(eager = true) {
        with(template) {
            buildFragment()
        }
    }
    return ModelQLTemplateInstance<Context, Template>(template, fragment)
}

fun <TTemplate : IModelQLTemplate<*, TOuter>, TOuter> TOuter.insert(
    templateInstance: ModelQLTemplateInstance<TOuter, TTemplate>,
    placeholder: TemplatePlaceholder<TTemplate>,
) {
    placeholder.apply(templateInstance.template)
    templateInstance.fragment.get().insertInto(this)
}

fun <TOuter, TTemplate : IModelQLTemplate<*, TOuter>> TOuter.insert(templateInstance: ModelQLTemplateInstance<TOuter, TTemplate>, build: TTemplate.() -> Unit) {
    templateInstance.template.build()
    templateInstance.fragment.get().insertInto(this)
}

fun <TOuter> IBoundFragment<TOuter>.toKotlinTemplate(): Template<TOuter> {
    val preparedFragment = this
    return object : Template<TOuter> {
        override fun TOuter.apply() {
            preparedFragment.insertInto(this)
        }
    }
}

fun <TOuter> ModelQLTemplateInstance<TOuter, *>.toKotlinTemplate(): Template<TOuter> {
    return this.fragment.toKotlinTemplate()
}

fun <TOuter> IRequestedFragment<TOuter>.toKotlinTemplate(): Template<TOuter> {
    val fragment = this
    return object : Template<TOuter> {
        override fun TOuter.apply() {
            fragment.get().insertInto(this)
        }
    }
}

fun <In> buildHtmlQuery(body: IRecursiveFragmentBuilder<In, HTML>.() -> Unit): IMonoUnboundQuery<In, String> {
    return IUnboundQuery.buildMono<In, String> { repository ->
        repository.bindFragment { body() }.mapLocal { result ->
            createHTML(prettyPrint = false).html {
                result.insertInto(this)
            }
        }
    }
}
