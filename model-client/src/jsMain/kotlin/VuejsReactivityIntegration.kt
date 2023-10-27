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
import org.modelix.incremental.DependencyTracking
import org.modelix.incremental.IDependencyListener
import org.modelix.incremental.IStateVariableGroup
import org.modelix.incremental.IStateVariableReference

@JsExport
class VuejsReactivityIntegration<RefT>(
    private val createCustomRef: () -> RefT,
    private val notifyAccess: (RefT) -> Unit,
    private val notifyModification: (RefT) -> Unit,
) {
    private val listener = object : IDependencyListener {
        override fun accessed(key: IStateVariableReference<*>) {
            notifyAccess(customRefs.getOrPut(key) { createCustomRef() })
        }

        override fun modified(key: IStateVariableReference<*>) {
            // An access can happen on a group while a modification can happen on a variable inside that group
            // (e.g. AllChildrenDependency is a parent of ChildrenDependency)
            generateSequence<IStateVariableGroup>(key) { runCatching { it.getGroup() }.getOrNull() }.forEach {
                customRefs[it]?.let(notifyModification)
            }
        }

        override fun parentGroupChanged(childGroup: IStateVariableGroup) {}
    }
    private val customRefs: MutableMap<IStateVariableGroup, RefT> = HashMap()
    private var started = false

    fun start() {
        check(!started)
        started = true
        DependencyTracking.registerListener(listener)
    }

    fun stop() {
        check(started)
        started = false
        DependencyTracking.removeListener(listener)
        customRefs.clear()
    }
}

@JsExport
fun <RefT> startVuejsIntegration(
    createCustomRef: () -> RefT,
    notifyAccess: (RefT) -> Unit,
    notifyModification: (RefT) -> Unit,
): VuejsReactivityIntegration<RefT> {
    return VuejsReactivityIntegration(createCustomRef, notifyAccess, notifyModification).also { it.start() }
}
