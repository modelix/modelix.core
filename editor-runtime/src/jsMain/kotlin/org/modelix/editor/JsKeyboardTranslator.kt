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
package org.modelix.editor

import org.w3c.dom.events.KeyboardEvent

fun KeyboardEvent.convert(): JSKeyboardEvent {
    val knownKey = KnownKeys.getIfKnown(key)
    var typedText: String? = key.let { if (it.length == 1) it else null }
    val locationEnum = when (this.location) {
        KeyboardEvent.DOM_KEY_LOCATION_STANDARD -> KeyLocation.STANDARD
        KeyboardEvent.DOM_KEY_LOCATION_LEFT -> KeyLocation.LEFT
        KeyboardEvent.DOM_KEY_LOCATION_RIGHT -> KeyLocation.RIGHT
        KeyboardEvent.DOM_KEY_LOCATION_NUMPAD -> KeyLocation.NUMPAD
        else -> KeyLocation.STANDARD
    }
    return JSKeyboardEvent(
        typedText = typedText,
        knownKey = knownKey,
        rawKey = key,
        modifiers = Modifiers(ctrlKey, altKey, shiftKey, metaKey),
        location = locationEnum,
        repeat = this.repeat,
        composing = this.isComposing
    )
}