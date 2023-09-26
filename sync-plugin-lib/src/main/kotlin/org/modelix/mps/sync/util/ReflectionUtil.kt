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

package org.modelix.mps.sync.util

import java.lang.reflect.Field
import java.lang.reflect.Modifier

// status: ready to test
object ReflectionUtil {
    fun readField(cls: Class<*>, obj: Any, fieldName: String): Any {
        return try {
            val field = cls.getDeclaredField(fieldName)
            field.setAccessible(true)
            field[obj]
        } catch (ex: Exception) {
            throw RuntimeException("Cannot read field '$fieldName' in class '$cls' of object: $obj", ex)
        }
    }

    fun writeField(cls: Class<*>, obj: Any, fieldName: String, value: Any) {
        try {
            val field = cls.getDeclaredField(fieldName)
            field.setAccessible(true)
            if (Modifier.isFinal(field.modifiers)) {
                val modifiersField = Field::class.java.getDeclaredField("modifiers")
                modifiersField.setAccessible(true)
                val originalModifier = field.modifiers
                modifiersField.setInt(field, originalModifier and Modifier.FINAL.inv())
            }
            field[obj] = value
        } catch (ex: Exception) {
            throw RuntimeException("Cannot write field '$fieldName' in class '$cls' of object: $obj", ex)
        }
    }

    fun callMethod(
        cls: Class<*>,
        obj: Any?,
        methodName: String,
        argumentTypes: Array<Class<*>>,
        arguments: Array<Any>,
    ): Any {
        return try {
            val method = cls.getDeclaredMethod(methodName, *argumentTypes)
            method.setAccessible(true)
            method.invoke(obj, *arguments)
        } catch (ex: Exception) {
            throw RuntimeException("Cannot call method '$methodName' in class '$cls' of object: $obj", ex)
        }
    }

    fun callVoidMethod(
        cls: Class<*>,
        obj: Any?,
        methodName: String,
        argumentTypes: Array<Class<*>>,
        arguments: Array<Any>,
    ) {
        callMethod(cls, obj, methodName, argumentTypes, arguments)
    }

    fun callStaticMethod(
        cls: Class<*>,
        methodName: String,
        argumentTypes: Array<Class<*>>,
        arguments: Array<Any>,
    ): Any {
        return callMethod(cls, null, methodName, argumentTypes, arguments)
    }

    fun callStaticVoidMethod(
        cls: Class<*>,
        methodName: String,
        argumentTypes: Array<Class<*>>,
        arguments: Array<Any>,
    ) {
        callStaticMethod(cls, methodName, argumentTypes, arguments)
    }

    fun getClass(fqName: String): Class<*> {
        return try {
            Class.forName(fqName)
        } catch (ex: ClassNotFoundException) {
            throw RuntimeException("", ex)
        }
    }
}
