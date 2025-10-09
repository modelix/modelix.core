package org.modelix.mps.sync3.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.ClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.Consumer
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import org.modelix.model.lazy.CLVersion
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.sync3.IBinding
import org.modelix.mps.sync3.IModelSyncService
import org.modelix.mps.sync3.IServerConnection
import java.awt.Desktop
import java.awt.Point
import java.awt.event.MouseEvent
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

class ModelSyncStatusWidget(val project: Project) : CustomStatusBarWidget, StatusBarWidget.WidgetPresentation {
    companion object {
        private val LOG = mu.KotlinLogging.logger { }
        const val ID = "ModelixSyncStatus"
        val ICON: Icon? = runCatching { ModelixMpsApi.loadIcon("org/modelix/mps/sync3/modelix16.svg", this::class.java) }
            .onFailure { LOG.error(it) { "Failed to load icon" } }
            .getOrNull()
    }

    private val component: JLabel = JLabel("", ICON, JLabel.LEFT)
    private val timer = EdtExecutorService.getScheduledExecutorInstance()
        .scheduleWithFixedDelay({
            try {
                updateComponent()
            } catch (ex: Throwable) {
                LOG.error(ex) { "Widget update failed" }
            }
        }, 1000, 500, TimeUnit.MILLISECONDS)
    private var disposed = false
    private var highlighted = false

    init {
        object : ClickListener() {
            override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
                val urls = IModelSyncService.getInstance(project).getUsedServerConnections()
                    .mapNotNull { it.getPendingAuthRequest() }.toSet()
                if (urls.isNotEmpty()) {
                    val desktop = Desktop.getDesktop()
                    if (desktop.isSupported(Desktop.Action.BROWSE)) {
                        for (url in urls) {
                            desktop.browse(URI.create(url))
                        }
                    }
                    return true
                } else {
                    val dataContext = DataManager.getInstance().getDataContext(component)
                    val group = ActionManager.getInstance().getAction("org.modelix.mps.sync3.ui.StatusWidgetGroup") as ActionGroup
                    val popup: PopupFactoryImpl.ActionGroupPopup = JBPopupFactory.getInstance().createActionGroupPopup(
                        "Model Synchronization",
                        group,
                        dataContext,
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                        true,
                    ) as PopupFactoryImpl.ActionGroupPopup
                    val dimension = popup.content.preferredSize
                    val at = Point(0, -dimension.height)
                    popup.show(RelativePoint(component, at))
                    return true
                }
                return false
            }
        }.installOn(component, true)
    }

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {}

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    override fun dispose() {
        disposed = true
        timer.cancel(true)
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
        return IModelSyncService.getInstance(project).getUsedServerConnections().any { it.getPendingAuthRequest() != null }
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation? {
        return this
    }

    override fun getTooltipText(): @NlsContexts.Tooltip String? {
        val service = IModelSyncService.Companion.getInstance(project)

        return createHTML().apply {
            val connections = service.getServerConnections()
            if (connections.isEmpty()) {
                div {
                    +"No connections configured"
                }
            }
            for (connection in connections) {
                div {
                    div {
                        styleX = "font-weight: bold; text-decoration: underline;"
                        +"Connection"
                    }
                    div {
                        styleX = "padding-left: 20px"
                        table {
                            tr {
                                td {
                                    styleX = "font-weight: bold"
                                    +"URL: "
                                }
                                td {
                                    +connection.getUrl()
                                }
                            }
                            tr {
                                td {
                                    styleX = "font-weight: bold"
                                    +"Status: "
                                }
                                td {
                                    +connection.getStatus().toString()
                                }
                            }
                            connection.getPendingAuthRequest()?.let { url ->
                                tr {
                                    td {
                                        styleX = "font-weight: bold"
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
                                styleX = "font-weight: bold; text-decoration: underline;"
                                +"Project Binding"
                            }
                            div {
                                styleX = "padding-left: 20px"
                                table {
                                    tr {
                                        td {
                                            styleX = "font-weight: bold"
                                            +"Repository:"
                                        }
                                        td {
                                            +binding.getBranchRef().repositoryId.id
                                        }
                                    }
                                    tr {
                                        td {
                                            styleX = "font-weight: bold"
                                            +"Branch:"
                                        }
                                        td {
                                            +binding.getBranchRef().branchName
                                        }
                                    }
                                    tr {
                                        td {
                                            styleX = "font-weight: bold"
                                            +"Enabled:"
                                        }
                                        td {
                                            +if (binding.isEnabled()) "ENABLED" else "DISABLED"
                                        }
                                    }
                                    tr {
                                        td {
                                            styleX = "font-weight: bold"
                                            +"Version:"
                                        }
                                        td {
                                            val version = binding.getCurrentVersion()
                                            if (version != null) {
                                                +version.getContentHash()
                                                br()
                                                +(version as? CLVersion)
                                                    ?.getTimestamp()
                                                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
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
                    result = when (val status = binding.getStatus()) {
                        IBinding.Status.Disabled -> "Disabled"
                        IBinding.Status.Initializing -> "Initializing"
                        is IBinding.Status.Synced -> "Synchronized: ${status.versionHash.take(5)}"
                        is IBinding.Status.Syncing -> "Synchronizing: ${status.progress()}"
                        is IBinding.Status.Error -> "Synchronization failed: ${status.message}"
                        is IBinding.Status.NoPermission -> "${status.user} has no permission on ${binding.getBranchRef().repositoryId}"
                    }
                }
            }
        }
        return result ?: "Not Synchronized"
    }
}

/**
 * Workaround for fixing
 *
 * `java.lang.NoSuchMethodError: 'void kotlinx.html.Gen_attr_traitsKt.setStyle(kotlinx.html.CommonAttributeGroupFacade, java.lang.String)'`
 */
var kotlinx.html.HTMLTag.styleX: String
    get() = attributes.get("style") ?: ""
    set(value) = attributes.set("style", value)
