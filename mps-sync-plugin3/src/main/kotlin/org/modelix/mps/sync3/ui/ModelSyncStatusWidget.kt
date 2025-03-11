package org.modelix.mps.sync3.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.ClickListener
import com.intellij.ui.IconManager
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import org.jetbrains.annotations.NonNls
import org.modelix.model.lazy.CLVersion
import org.modelix.mps.sync3.IModelSyncService
import org.modelix.mps.sync3.IServerConnection
import java.awt.Desktop
import java.awt.event.MouseEvent
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel

class ModelSyncStatusWidget(val project: Project) : CustomStatusBarWidget, StatusBarWidget.WidgetPresentation {
    companion object {
        const val ID = "ModelixSyncStatus"
        val ICON = IconManager.Companion.getInstance().getIcon("org/modelix/mps/sync3/modelix16.svg", this::class.java.classLoader)
    }

    private val component: JLabel = JLabel("", ICON, JLabel.LEFT)
    private val timer = EdtExecutorService.getScheduledExecutorInstance()
        .scheduleWithFixedDelay({ updateComponent() }, 1000, 500, TimeUnit.MILLISECONDS)
    private var disposed = false
    private var highlighted = false

    init {
        object : ClickListener() {
            override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
                val urls = IModelSyncService.getInstance(project).getServerConnections()
                    .mapNotNull { it.getPendingAuthRequest() }.toSet()
                if (urls.isNotEmpty()) {
                    val desktop = Desktop.getDesktop()
                    if (desktop.isSupported(Desktop.Action.BROWSE)) {
                        for (url in urls) {
                            desktop.browse(URI.create(url))
                        }
                    }
                    return true
                }
                return false
            }
        }.installOn(component, true)
    }

    override fun ID(): @NonNls String = ID

    override fun dispose() {
        disposed = true
        timer.cancel(true)
        super.dispose()
    }

    override fun getComponent(): JComponent? {
        check(!disposed) { "Disposed" }
        return component
    }

    private fun updateComponent() {
        component.setText(getText())
        component.toolTipText = getTooltipText()

        // blink if authorization is required to get the users attention
        highlighted = authorizationRequired() && !highlighted
        if (highlighted) {
            component.background = JBColor.YELLOW
            component.isOpaque = true
        } else {
            component.background = null
            component.isOpaque = false
        }
    }

    private fun authorizationRequired(): Boolean {
        return IModelSyncService.getInstance(project).getServerConnections().any { it.getPendingAuthRequest() != null }
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation? {
        return this
    }

    override fun getTooltipText(): @NlsContexts.Tooltip String? {
        val service = IModelSyncService.Companion.getInstance(project)

        return createHTML().apply {
            for (connection in service.getServerConnections()) {
                div {
                    div {
                        style = "font-weight: bold; text-decoration: underline;"
                        +"Connection"
                    }
                    div {
                        style = "padding-left: 20px"
                        table {
                            tr {
                                td {
                                    style = "font-weight: bold"
                                    +"URL: "
                                }
                                td {
                                    +connection.getUrl()
                                }
                            }
                            tr {
                                td {
                                    style = "font-weight: bold"
                                    +"Status: "
                                }
                                td {
                                    +connection.getStatus().toString()
                                }
                            }
                            connection.getPendingAuthRequest()?.let { url ->
                                tr {
                                    td {
                                        style = "font-weight: bold"
                                        +"Authorization URL: "
                                    }
                                    td {
                                        +url
                                    }
                                }
                            }
                        }
                        for (binding in connection.getBindings()) {
                            div {
                                style = "font-weight: bold; text-decoration: underline;"
                                +"Project Binding"
                            }
                            div {
                                style = "padding-left: 20px"
                                table {
                                    tr {
                                        td {
                                            style = "font-weight: bold"
                                            +"Repository:"
                                        }
                                        td {
                                            +binding.getBranchRef().repositoryId.id
                                        }
                                    }
                                    tr {
                                        td {
                                            style = "font-weight: bold"
                                            +"Branch:"
                                        }
                                        td {
                                            +binding.getBranchRef().branchName
                                        }
                                    }
                                    tr {
                                        td {
                                            style = "font-weight: bold"
                                            +"Enabled:"
                                        }
                                        td {
                                            +if (binding.isEnabled()) "ENABLED" else "DISABLED"
                                        }
                                    }
                                    tr {
                                        td {
                                            style = "font-weight: bold"
                                            +"Version:"
                                        }
                                        td {
                                            val version = binding.getCurrentVersion()
                                            if (version != null) {
                                                +version.getContentHash()
                                                br()
                                                +(version as? CLVersion)
                                                    ?.getTimestamp()
                                                    ?.toLocalDateTime(TimeZone.Companion.currentSystemDefault())
                                                    ?.toString()
                                                    .orEmpty()
                                                br()
                                                +(version as? CLVersion)?.author.orEmpty()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.finalize()
    }

    private fun getText(): @NlsContexts.Label String {
        val service = IModelSyncService.getInstance(project)
        var result: String? = null
        for (connection in service.getServerConnections()) {
            for (binding in connection.getBindings()) {
                if (binding.isEnabled()) {
                    when (connection.getStatus()) {
                        IServerConnection.Status.CONNECTED -> {}
                        IServerConnection.Status.DISCONNECTED -> return "Disconnected"
                        IServerConnection.Status.AUTHORIZATION_REQUIRED -> {
                            return "Click to log in"
                        }
                    }
                    result = binding.getCurrentVersion()?.getContentHash()?.let { "Synchronized: ${it.take(5)}" } ?: result
                }
            }
        }
        return result ?: "Not Synchronized"
    }
}
