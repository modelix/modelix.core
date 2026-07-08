package org.modelix.mps.sync3.ui

import com.intellij.ide.BrowserUtil
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

class ModelSyncStatusWidget(val project: Project) : CustomStatusBarWidget, StatusBarWidget.WidgetPresentation {
    companion object {
        private val LOG = mu.KotlinLogging.logger { }
        const val ID = "ModelixSyncStatus"

        /** Number of leading characters of a version hash shown in the status text and tooltip. */
        private const val VERSION_HASH_DISPLAY_LENGTH = 5
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
                val authRequests = IModelSyncService.getInstance(project).getUsedServerConnections()
                    .flatMap { it.getPendingAuthRequests() }.toSet()
                if (authRequests.isNotEmpty()) {
                    // java.awt.Desktop.BROWSE is unavailable on many Linux setups; BrowserUtil uses it on
                    // Windows/macOS where it works, and falls back to xdg-open etc. on Linux
                    for (authRequest in authRequests) {
                        BrowserUtil.browse(authRequest.getUrl())
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
        return IModelSyncService.getInstance(project)
            .getUsedServerConnections()
            .any { it.getPendingAuthRequests().isNotEmpty() }
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
                    table {
                        tr {
                            td {
                                styleX = "font-weight: bold"
                                +connection.getUrl()
                            }
                            td {
                                styleX = "padding-left: 20px"
                                +connection.getStatus().toString()
                            }
                        }
                        for (binding in connection.getBindings()) {
                            tr {
                                td {
                                    styleX = "padding-left: 20px"
                                    +"${binding.getBranchRef().repositoryId.id} / ${binding.getBranchRef().branchName}"
                                }
                                td {
                                    styleX = "padding-left: 20px"
                                    +bindingStatusText(binding)
                                }
                            }
                        }
                    }
                }
            }
        }.finalize()
    }

    /**
     * A compact, single-line sync state for a binding. Extra details are folded into a
     * parenthesized, comma-joined suffix: the last-change timestamp (only for the `Synced` status,
     * when available) and `readonly` for read-only bindings. Writable bindings without a timestamp
     * show no suffix (writable is the implicit default).
     */
    private fun bindingStatusText(binding: IBinding): String {
        val details = mutableListOf<String>()
        val status = when (val status = binding.getStatus()) {
            IBinding.Status.Disabled -> "Disabled"
            IBinding.Status.Initializing -> "Initializing"
            is IBinding.Status.Synced -> {
                formatLastChange(binding)?.let { details.add("last change: $it") }
                "Synced ${status.versionHash.take(VERSION_HASH_DISPLAY_LENGTH)}"
            }
            is IBinding.Status.Syncing -> "Syncing ${status.progress().orEmpty()}"
            is IBinding.Status.Error -> "Error: ${status.message.orEmpty()}"
            is IBinding.Status.NoPermission -> "No permission for ${status.user.orEmpty()}"
        }
        if (binding.isReadonly()) details.add("readonly")
        return if (details.isEmpty()) status else "$status (${details.joinToString(", ")})"
    }

    /**
     * Formats the current version's timestamp in the system time zone. If the change happened
     * today, only the time is shown (`HH:mm`), otherwise the full date and time (`dd.MM.yyyy HH:mm`).
     * Returns `null` when no timestamp is available.
     */
    private fun formatLastChange(binding: IBinding): String? {
        val timestamp = (binding.getCurrentVersion() as? CLVersion)?.getTimestamp() ?: return null
        val zone = TimeZone.currentSystemDefault()
        val dateTime = timestamp.toLocalDateTime(zone)
        val today = Clock.System.now().toLocalDateTime(zone).date
        val hour = dateTime.hour.toString().padStart(2, '0')
        val minute = dateTime.minute.toString().padStart(2, '0')
        val time = "$hour:$minute"
        if (dateTime.date == today) {
            return time
        }
        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        val month = dateTime.monthNumber.toString().padStart(2, '0')
        return "$day.$month.${dateTime.year} $time"
    }

    private fun getText(): @NlsContexts.Label String {
        val service = IModelSyncService.getInstance(project)
        var result: String? = null
        service.getUsedServerConnections()
            .flatMap { it.getBindings() }
            // readonly comes first so that the result version hash is reliably the one from the modifiable binding
            .sortedByDescending { service.isReadonly(it) }
            .forEach { binding ->
                when (binding.getConnection().getStatus()) {
                    IServerConnection.Status.CONNECTED -> {}
                    IServerConnection.Status.DISCONNECTED -> return "Disconnected"
                    IServerConnection.Status.AUTHORIZATION_REQUIRED -> {
                        return "Click to log in"
                    }
                }
                result = when (val status = binding.getStatus()) {
                    IBinding.Status.Disabled -> "Disabled"
                    IBinding.Status.Initializing -> "Initializing"
                    is IBinding.Status.Synced -> "Synchronized: ${status.versionHash.take(VERSION_HASH_DISPLAY_LENGTH)}"
                    is IBinding.Status.Syncing -> "Synchronizing: ${status.progress()}"
                    is IBinding.Status.Error -> "Synchronization failed: ${status.message}"
                    is IBinding.Status.NoPermission -> "${status.user} has no permission on ${binding.getBranchRef().repositoryId}"
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
