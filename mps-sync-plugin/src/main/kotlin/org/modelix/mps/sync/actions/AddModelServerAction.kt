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

package org.modelix.mps.sync.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import org.modelix.mps.sync.config.PersistedBindingConfiguration
import org.modelix.mps.sync.connection.ModelServerConnection
import org.modelix.mps.sync.connection.ModelServerConnections
import javax.swing.Icon

class AddModelServerAction : AnAction {

    constructor() : super()
    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    private fun getURL(event: AnActionEvent): String {
        return Messages.showInputDialog(
            event.getData(CommonDataKeys.PROJECT),
            "URL",
            "Add Model Server",
            null,
            "http://127.0.0.1",
            null,
        ).toString()
    }

    override fun actionPerformed(event: AnActionEvent) {
        var url = getURL(event)

        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Messages.showErrorDialog(
                event.getData(CommonDataKeys.PROJECT),
                "The provided URL '$url' is not valid. (model-server URLs have to start with 'http://' or 'https://'",
                "Invalid model-server URL",
            )
            return
        }

        if (!url.endsWith("/")) {
            url = "$url/"
        }

        if (ModelServerConnections.instance.existModelServer(url)) {
            Messages.showErrorDialog(event.getData(CommonDataKeys.PROJECT), "Already present!", "Add Model Server")
            return
        }

        val token: String? = null
        val finalUrl: String = url

        // TODO: migrate Authentication classes
//        if (AuthenticationManager.getAuthenticationProcess(finalUrl).areWeUsingAuthentication(event.getData(CommonDataKeys.PROJECT))) {
//            AuthenticationManager.getAuthenticationProcess(finalUrl).getToken(event.getData(CommonDataKeys.PROJECT), object : Consumer<String?>() {
//                    fun accept(token: String?) {
//                        val modelServer: ModelServerConnection = ModelServerConnections.instance.ensureModelServerIsPresent(finalUrl)
//                        PersistedBindingConfiguration.getInstance(event.getData(CommonDataKeys.PROJECT)!!).ensureModelServerIsPresent(modelServer)
//                    }
//                })
//        } else {
        val modelServer: ModelServerConnection = ModelServerConnections.instance.ensureModelServerIsPresent(finalUrl)
        PersistedBindingConfiguration.getInstance(event.getData(CommonDataKeys.PROJECT)!!)
            .ensureModelServerIsPresent(modelServer)
//        }
    }
}
