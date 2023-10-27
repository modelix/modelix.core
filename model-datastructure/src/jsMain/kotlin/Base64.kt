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

@file:JsModule("js-base64")

import org.khronos.webgl.Uint8Array

@JsName("Base64")
external object Base64 {
    fun fromUint8Array(input: Uint8Array, uriSafe: Boolean): String
    fun decode(input: String): String
    fun encode(input: String): String
    fun encode(input: String, uriSafe: Boolean): String
}
