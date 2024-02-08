/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.plugin.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object CloudIcons {

    private const val FONT_SIZE = 14

    val ROOT_ICON = LetterInSquareIcon("C", FONT_SIZE, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val MODEL_SERVER_ICON = LetterInSquareIcon("S", FONT_SIZE, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val REPOSITORY_ICON = LetterInSquareIcon("R", FONT_SIZE, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val BRANCH_ICON = LetterInSquareIcon("B", FONT_SIZE, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val MODULE_ICON = LetterInSquareIcon("M", FONT_SIZE, 2.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val MODEL_ICON = LetterInSquareIcon("m", FONT_SIZE, 2.0f, 12.0f, JBColor.YELLOW, JBColor.BLACK)
    val CONNECTION_ON = LetterInSquareIcon("", FONT_SIZE, 2.0f, 12.0f, JBColor.GREEN, JBColor.BLACK)
    val CONNECTION_OFF = LetterInSquareIcon("", FONT_SIZE, 2.0f, 12.0f, JBColor.RED, JBColor.BLACK)
    val PLUGIN_ICON = IconLoader.getIcon("/META-INF/pluginIcon.svg", javaClass)
}
