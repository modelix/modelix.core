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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject

// status: ready to test
object ModelixNotifications {

    private const val GROUP_ID: String = "modelix"

    fun notifyWarning(title: String, message: String) {
        Notifications.Bus.notify(Notification(GROUP_ID, title, message, NotificationType.WARNING))
    }

    fun notifyError(title: String, message: String) {
        Notifications.Bus.notify(Notification(GROUP_ID, title, message, NotificationType.ERROR))
    }

    fun notifyError(title: String, message: String, mpsProject: MPSProject?) {
        Notifications.Bus.notify(
            Notification(GROUP_ID, title, message, NotificationType.ERROR),
            ProjectHelper.toIdeaProject(mpsProject),
        )
    }
}
