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

package org.modelix.mps.sync.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor

// status: ready to test
/**
 * Perhaps we need an Icon for Project?
 */
object CloudIcons {

    val ROOT_ICON = LetterInSquareIcon("C", 14, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val MODEL_SERVER_ICON = LetterInSquareIcon("S", 14, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val REPOSITORY_ICON = LetterInSquareIcon("R", 14, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val BRANCH_ICON = LetterInSquareIcon("B", 14, 3.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val MODULE_ICON = LetterInSquareIcon("M", 14, 2.0f, 13.0f, JBColor.YELLOW, JBColor.BLACK)
    val MODEL_ICON = LetterInSquareIcon("m", 14, 2.0f, 12.0f, JBColor.YELLOW, JBColor.BLACK)
    val PLUGIN_ICON = IconLoader.getIcon("/META-INF/pluginIcon.svg", javaClass)
}
