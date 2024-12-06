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

package org.modelix.authorization

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import java.security.Key

internal class CompositeJWSKeySelector<C : SecurityContext>(val selectors: List<JWSKeySelector<C>>) : JWSKeySelector<C> {
    override fun selectJWSKeys(
        header: JWSHeader,
        context: C?,
    ): List<Key> {
        return selectors.flatMap { it.selectJWSKeys(header, context) }
    }
}
