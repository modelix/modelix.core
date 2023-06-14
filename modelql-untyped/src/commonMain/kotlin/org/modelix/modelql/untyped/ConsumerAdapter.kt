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
package org.modelix.modelql.untyped

import org.modelix.model.api.IAsyncNode
import org.modelix.modelql.core.IConsumer

class ConsumerAdapter<E>(val consumer: IConsumer<E>): IAsyncNode.IVisitor<E> {
    override fun onNext(it: E) = consumer.onNext(it)
    override fun onComplete() = consumer.onComplete()
    override fun onException(ex: Exception) = consumer.onException(ex)
}