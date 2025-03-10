package org.modelix.mps.sync3.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.IconManager
import com.intellij.util.Consumer
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
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.Icon

class ModelSyncStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): @NonNls String = ModelSyncStatusWidget.ID

    override fun getDisplayName(): @NlsContexts.ConfigurableName String = "Model Synchronization Status"

    override fun createWidget(project: Project): StatusBarWidget {
        return ModelSyncStatusWidget(project)
    }
}

class ModelSyncStatusWidget(val project: Project) : StatusBarWidget, StatusBarWidget.WidgetPresentation, StatusBarWidget.TextPresentation, StatusBarWidget.IconPresentation {
    companion object {
        const val ID = "ModelixSyncStatus"
        val ICON = IconManager.getInstance().getIcon("org/modelix/mps/sync3/modelix16.svg", this::class.java.classLoader)
    }

    override fun ID(): @NonNls String = ID

    override fun dispose() {
        super.dispose()
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation? {
        return this
    }

    override fun getTooltipText(): @NlsContexts.Tooltip String? {
        val service = IModelSyncService.getInstance(project)

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

    override fun getIcon(): Icon? {
        return ICON
    }

    override fun getAlignment(): Float {
        return Component.LEFT_ALIGNMENT
    }

    override fun getText(): @NlsContexts.Label String {
        val service = IModelSyncService.getInstance(project)
        var result: String? = null
        for (connection in service.getServerConnections()) {
            for (binding in connection.getBindings()) {
                if (binding.isEnabled()) {
                    if (connection.getStatus() != IServerConnection.Status.CONNECTED) {
                        return "Disconnected"
                    }
                    result = binding.getCurrentVersion()?.getContentHash()?.let { "Synchronized: ${it.take(5)}" } ?: result
                }
            }
        }
        return result ?: "Not Synchronized"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return null
    }
}
