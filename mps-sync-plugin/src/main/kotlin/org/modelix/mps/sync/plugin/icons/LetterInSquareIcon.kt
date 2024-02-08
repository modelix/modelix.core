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

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class LetterInSquareIcon(
    private val letter: String,
    private val fontSize: Int,
    private val offsetX: Float,
    private val offsetY: Float,
    private val backgroundColor: Color,
    private val foregroundColor: Color,
) : Icon {

    constructor(letter: String, fontSize: Int, offsetX: Float, offsetY: Float) : this(
        letter,
        fontSize,
        offsetX,
        offsetY,
        JBColor.BLACK,
        Gray._200,
    )

    override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.color = backgroundColor
            g.fill(
                RoundRectangle2D.Double(
                    x.toDouble(),
                    y.toDouble(),
                    iconWidth.toDouble(),
                    iconHeight.toDouble(),
                    5.0,
                    5.0,
                ),
            )
            g.font = Font("Arial", Font.BOLD, fontSize)
            g.color = foregroundColor
            g.drawString(letter, x + offsetX, y + offsetY)
        } finally {
            g.dispose()
        }
    }

    override fun getIconWidth() = 16

    override fun getIconHeight() = 16
}
