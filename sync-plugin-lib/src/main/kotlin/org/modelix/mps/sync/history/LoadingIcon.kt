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

package org.modelix.mps.sync.history

import com.intellij.ui.Gray
import jetbrains.mps.ide.ui.tree.MPSTreeNode
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import javax.swing.Icon
import javax.swing.Timer

// status: ready to test
class LoadingIcon private constructor() : Icon {
    companion object {

        private val instance = LoadingIcon()

        fun apply(treeNode: MPSTreeNode): MPSTreeNode {
            treeNode.icon = instance
            instance.register(treeNode)
            return treeNode
        }
    }

    private val activeNodes = mutableSetOf<MPSTreeNode>()
    private var angle: Double? = null
    private var timer: Timer? = null
    private var inactivity = 0

    fun register(treeNode: MPSTreeNode) {
        activeNodes.add(treeNode)
        ensureTimerRunning()
    }

    private fun ensureTimerRunning() {
        if (timer == null || timer?.isRunning == false) {
            timer = Timer(1000 / 60) {
                rotate()
                if (activeNodes.isEmpty()) {
                    if (inactivity > 5000 / 60) {
                        activeNodes.clear()
                        timer?.stop()
                        timer = null
                    } else {
                        inactivity++
                    }
                }

                activeNodes.mapNotNull { it.getTree() }.distinct().forEach {
                    it.repaint()
                }
            }
            timer?.start()
        }
    }

    private fun rotate() {
        angle = (angle!! - 360.0 / 120.0) % 360.0
    }

    override fun paintIcon(component: Component, graphics: Graphics, x: Int, y: Int) {
        inactivity = 0
        ensureTimerRunning()

        val w = iconWidth.toDouble()
        val h = iconHeight.toDouble()
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.stroke = BasicStroke(3.0f)
            g.color = Gray._80
            g.draw(Arc2D.Double(2.0 + x, 2.0 + y, w - 4.0, h - 4.0, angle!!, 250.0, Arc2D.OPEN))
        } finally {
            g.dispose()
        }
    }

    override fun getIconWidth() = 16

    override fun getIconHeight() = 16
}
