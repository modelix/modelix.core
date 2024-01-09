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

package org.modelix.model.datastructure

import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.persistent.IKVValue
import kotlin.reflect.KClass

typealias ObjectHash = String
typealias SerializedObject = String

abstract class ReplicatableObject {
    abstract fun getHash(): ObjectHash
    abstract fun getDeserializer(): IDeserializer<ReplicatableObject>
}

fun <T : ReplicatableObject> T.getCastedDeserializer() = getDeserializer() as IDeserializer<T>

abstract class ObjectReference<out E : ReplicatableObject> {
    abstract fun load(source: IBulkQuery): IBulkQuery.Value<LoadedObject<E>>
    abstract fun load(objects: Map<ObjectHash, SerializedObject>): LoadedObject<E>
    abstract fun tryLoad(objects: Map<ObjectHash, SerializedObject>): ObjectReference<E>
    abstract fun unloadObject(): UnloadedObject<E>
}

class LoadedObject<out E : ReplicatableObject>(private val obj: E) : ObjectReference<E>() {
    override fun load(source: IBulkQuery): IBulkQuery.Value<LoadedObject<E>> {
        TODO("Not yet implemented")
    }
    override fun load(objects: Map<ObjectHash, String>): LoadedObject<E> = this
    override fun tryLoad(objects: Map<ObjectHash, String>): ObjectReference<E> = this
    override fun unloadObject(): UnloadedObject<E> = UnloadedObject(obj.getHash(), obj.getCastedDeserializer())
}

class UnloadedObject<out E : ReplicatableObject>(private val hash: ObjectHash, private val deserializer: IDeserializer<E>) : ObjectReference<E>() {
    override fun load(source: IBulkQuery): IBulkQuery.Value<LoadedObject<E>> {
        TODO("Not yet implemented")
    }

    override fun load(objects: Map<String, String>): LoadedObject<E> {
        val serialized = checkNotNull(objects[hash]) { "Object not found: $hash" }
        return LoadedObject(deserializer.deserialize(serialized))
    }

    override fun tryLoad(objects: Map<String, String>): ObjectReference<E> {
        val serialized = objects[hash] ?: return this
        return LoadedObject(deserializer.deserialize(serialized))
    }

    override fun unloadObject(): UnloadedObject<E> = this
}

interface IDeserializer<out E : Any> {
    val type: KClass<out E>
    fun deserialize(serialized: String): E
    fun asFunction(): (String) -> E

    companion object {
        fun <T : Any> create(type: KClass<T>, deserializer: (String) -> T): IDeserializer<T> {
            return DeserializerFromLambda(type, deserializer)
        }
    }

    private class DeserializerFromLambda<E : Any>(override val type: KClass<E>, val deserializer: (String) -> E) : IDeserializer<E> {
        override fun deserialize(serialized: String): E = deserializer(serialized)
        override fun asFunction(): (String) -> E = deserializer
    }
}