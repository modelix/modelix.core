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

import kotlin.random.Random

object EditorTestUtils {
    val noSpace = Any()
    val newLine = Any()
    val indentChildren = Any()

    fun buildCells(template: Any): Cell {
        return when (template) {
            is Cell -> template
            noSpace -> Cell(CellData().apply { properties[CommonCellProperties.noSpace] = true })
            newLine -> Cell(CellData().apply { properties[CommonCellProperties.onNewLine] = true })
            is String -> Cell(TextCellData(template, ""))
            is List<*> -> Cell(CellData()).apply {
                template.forEach { child ->
                    when (child) {
                        indentChildren -> data.properties[CommonCellProperties.indentChildren] = true
                        is ECellLayout -> data.properties[CommonCellProperties.layout] = child
                        else -> addChild(buildCells(child!!))
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported: $template")
        }
    }

    fun buildRandomCells(rand: Random, cellsPerLevel: Int, levels: Int): Cell {
        return buildCells(buildRandomTemplate(rand, cellsPerLevel, levels))
    }

    fun buildRandomTemplate(rand: Random, cellsPerLevel: Int, levels: Int): Any {
        return (1..cellsPerLevel).map {
            when (rand.nextInt(10)) {
                0 -> noSpace
                1 -> newLine
                2 -> indentChildren
                else -> {
                    if (levels == 0) {
                        rand.nextInt(1000, 10000).toString()
                    } else {
                        buildRandomTemplate(rand, cellsPerLevel, levels - 1)
                    }
                }
            }
        }
    }
}
