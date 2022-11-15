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

class JSKeyboardEvent(
    val typedText: String?,
    val knownKey: KnownKeys?,
    val rawKey: String,
    val modifiers: Modifiers,
    val location: KeyLocation,
    val repeat: Boolean,
    val composing: Boolean
) {
    constructor(knownKey: KnownKeys) : this(null, knownKey, knownKey.name, Modifiers.NONE, KeyLocation.STANDARD, false, false)
}

data class Modifiers(val ctrl: Boolean, val alt: Boolean, val shift: Boolean, val meta: Boolean) {
    companion object {
        val NONE = Modifiers(ctrl = false, alt = false, shift = false, meta = false)
        val CTRL = Modifiers(ctrl = true, alt = false, shift = false, meta = false)
        val ALT = Modifiers(ctrl = false, alt = true, shift = false, meta = false)
        val CTRL_ALT = Modifiers(ctrl = true, alt = true, shift = false, meta = false)
        val SHIFT = Modifiers(ctrl = false, alt = false, shift = true, meta = false)
        val CTRL_SHIFT = Modifiers(ctrl = true, alt = false, shift = true, meta = false)
        val ALT_SHIFT = Modifiers(ctrl = false, alt = true, shift = true, meta = false)
        val CTRL_ALT_SHIFT = Modifiers(ctrl = true, alt = true, shift = true, meta = false)
        val META = Modifiers(ctrl = false, alt = false, shift = false, meta = true)
        val CTRL_META = Modifiers(ctrl = true, alt = false, shift = false, meta = true)
        val ALT_META = Modifiers(ctrl = false, alt = true, shift = false, meta = true)
        val CTRL_ALT_META = Modifiers(ctrl = true, alt = true, shift = false, meta = true)
        val SHIFT_META = Modifiers(ctrl = false, alt = false, shift = true, meta = true)
        val CTRL_SHIFT_META = Modifiers(ctrl = true, alt = false, shift = true, meta = true)
        val ALT_SHIFT_META = Modifiers(ctrl = false, alt = true, shift = true, meta = true)
        val CTRL_ALT_SHIFT_META = Modifiers(ctrl = true, alt = true, shift = true, meta = true)
    }
}

enum class KnownKeys {
    Alt,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    ArrowUp,
    AudioVolumeDown,
    AudioVolumeMute,
    AudioVolumeUp,
    Backspace,
    CapsLock,
    ContextMenu,
    Control,
    Delete,
    End,
    Enter,
    Escape,
    F1,
    F10,
    F11,
    F12,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    Home,
    Insert,
    LaunchApplication1,
    LaunchApplication2,
    LaunchMediaPlayer,
    Meta,
    NumLock,
    PageDown,
    PageUp,
    Pause,
    PrintScreen,
    ScrollLock,
    Shift,
    Tab;

    companion object {
        private val allEntries: Map<String, KnownKeys> = KnownKeys.values().associateBy { it.name }

        fun getIfKnown(value: String?): KnownKeys? = allEntries[value]
    }
}

enum class KeyLocation {
    STANDARD,
    LEFT,
    RIGHT,
    NUMPAD
}