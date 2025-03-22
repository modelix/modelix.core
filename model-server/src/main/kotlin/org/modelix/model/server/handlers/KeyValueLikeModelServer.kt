package org.modelix.model.server.handlers

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.request.receiveText
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.span
import org.json.JSONArray
import org.json.JSONObject
import org.modelix.authorization.EPermissionType
import org.modelix.authorization.NoPermissionException
import org.modelix.authorization.checkPermission
import org.modelix.authorization.getUserName
import org.modelix.authorization.requiresLogin
import org.modelix.model.lazy.BranchReference
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.ModelServerPermissionSchema
import org.modelix.model.server.store.ObjectInRepository
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.StoreManager
import org.modelix.model.server.store.pollEntry
import org.modelix.model.server.store.runReadIO
import org.modelix.model.server.store.runWriteIO
import org.modelix.model.server.templates.PageWithMenuBar
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

private class NotFoundException(description: String?) : RuntimeException(description)

typealias CallContext = PipelineContext<Unit, ApplicationCall>

class KeyValueLikeModelServer(
    private val repositoriesManager: IRepositoriesManager,
) {

    companion object {
        private val HASH_PATTERN: Pattern = Pattern.compile("[a-zA-Z0-9\\-_]{5}\\*[a-zA-Z0-9\\-_]{38}")
        const val PROTECTED_PREFIX = "$$$"
    }

    private val stores: StoreManager get() = repositoriesManager.getStoreManager()

    fun init(application: Application) {
        // Functionally, it does not matter if the server ID
        // is created eagerly on startup or lazily on the first request,
        // as long as the same server ID is returned from the same server.
        //
        // The server ID is initialized eagerly because adding special conditions in the affected
        // request to initialize it lazily, would make the code less robust.
        // Each change in the logic of RepositoriesManager#maybeInitAndGetSeverId would need
        // the special conditions in the affected requests to be updated.
        @OptIn(RequiresTransaction::class)
        repositoriesManager.getTransactionManager().runWrite { repositoriesManager.maybeInitAndGetSeverId() }
        application.apply {
            modelServerModule()
        }
    }

    private fun Application.modelServerModule() {
        routing {
            get<Paths.getHeaders> {
                val headers = call.request.headers.entries().flatMap { e -> e.value.map { e.key to it } }
                call.respondHtmlTemplate(PageWithMenuBar("headers", ".")) {
                    bodyContent {
                        h1 { +"HTTP Headers" }
                        div {
                            headers.forEach {
                                span {
                                    +"${it.first}: ${it.second}"
                                }
                                br { }
                            }
                        }
                    }
                }
            }
            requiresLogin {
                get<Paths.getKeyGet> {
                    val key = call.parameters["key"]!!
                    checkKeyPermission(key, EPermissionType.READ)
                    @OptIn(RequiresTransaction::class)
                    val value = runRead { stores.getGlobalStoreClient()[key] }
                    respondValue(key, value)
                }
                get<Paths.pollKeyGet> {
                    val key: String = call.parameters["key"]!!
                    val lastKnownValue = call.request.queryParameters["lastKnownValue"]
                    checkKeyPermission(key, EPermissionType.READ)
                    val newValue = pollEntry(stores.genericStore, ObjectInRepository.global(key), lastKnownValue)
                    respondValue(key, newValue)
                }

                get<Paths.getEmailGet> {
                    call.respondText(call.getUserName() ?: "<no email>")
                }
                post<Paths.counterKeyPost> {
                    val key = call.parameters["key"]!!
                    checkKeyPermission(key, EPermissionType.WRITE)
                    val value = stores.getGlobalStoreClient(false).generateId(key)
                    call.respondText(text = value.toString())
                }

                get<Paths.getRecursivelyKeyGet> {
                    val key = call.parameters["key"]!!
                    checkKeyPermission(key, EPermissionType.READ)
                    @OptIn(RequiresTransaction::class)
                    call.respondText(runRead { collect(key, this) }.toString(2), contentType = ContentType.Application.Json)
                }

                put<Paths.putKeyPut> {
                    val key = call.parameters["key"]!!
                    val value = call.receiveText()
                    try {
                        @OptIn(RequiresTransaction::class)
                        runWrite {
                            putEntries(mapOf(key to value))
                        }
                        call.respondText("OK")
                    } catch (e: NotFoundException) {
                        throw HttpException(HttpStatusCode.NotFound, title = "Not found", details = e.message, cause = e)
                    }
                }

                put<Paths.putAllPut> {
                    val jsonStr = call.receiveText()
                    val json = JSONArray(jsonStr)
                    var entries: MutableMap<String, String?> = LinkedHashMap()
                    for (entry_ in json) {
                        val entry = entry_ as JSONObject
                        val key = entry.getString("key")
                        val value = entry.optString("value", null)
                        entries[key] = value
                    }
                    entries = sortByDependency(entries)
                    try {
                        @OptIn(RequiresTransaction::class)
                        runWrite {
                            putEntries(entries)
                        }
                        call.respondText(entries.size.toString() + " entries written")
                    } catch (e: NotFoundException) {
                        throw HttpException(HttpStatusCode.NotFound, title = "Not found", details = e.message, cause = e)
                    }
                }

                put<Paths.getAllPut> {
                    // PUT is used, because a GET is not allowed to have a request body that changes the result of the
                    // request. It would be legal for an HTTP proxy to cache all /getAll requests and ignore the body.
                    val reqJsonStr = call.receiveText()
                    val reqJson = JSONArray(reqJsonStr)
                    val respJson = JSONArray()
                    val keys: MutableList<String> = ArrayList(reqJson.length())
                    for (entry_ in reqJson) {
                        val key = entry_ as String
                        checkKeyPermission(key, EPermissionType.READ)
                        keys.add(key)
                    }
                    @OptIn(RequiresTransaction::class)
                    val values = runRead { stores.getGlobalStoreClient(false).getAll(keys) }
                    for (i in keys.indices) {
                        val respEntry = JSONObject()
                        respEntry.put("key", keys[i])
                        respEntry.put("value", values[i])
                        respJson.put(respEntry)
                    }
                    call.respondText(respJson.toString(), contentType = ContentType.Application.Json)
                }
            }
        }
    }

    private var sharedSecret: String? = null
    fun setSharedSecret(sharedSecret: String?) {
        this.sharedSecret = sharedSecret
    }

    private fun sortByDependency(unsorted: Map<String, String?>): MutableMap<String, String?> {
        val sorted: MutableMap<String, String?> = LinkedHashMap()
        val processed: MutableSet<String> = HashSet()
        object : Any() {
            fun fill(key: String) {
                if (processed.contains(key)) return
                processed.add(key)
                val value = unsorted[key]
                for (referencedKey in extractHashes(value)) {
                    if (unsorted.containsKey(referencedKey)) fill(referencedKey)
                }
                sorted[key] = value
            }

            fun fill() {
                for (key in unsorted.keys) {
                    fill(key)
                }
            }
        }.fill()
        return sorted
    }

    @RequiresTransaction
    fun collect(rootKey: String, routingContext: RoutingContext?): JSONArray {
        val result = JSONArray()
        val processed: MutableSet<String> = HashSet()
        val pending: MutableSet<String> = HashSet()
        pending.add(rootKey)
        while (pending.isNotEmpty()) {
            val keys: List<String> = ArrayList(pending)
            pending.clear()
            if (routingContext != null) {
                keys.forEach { routingContext.checkKeyPermission(it, EPermissionType.READ) }
            }
            val values = stores.getGlobalStoreClient(false).getAll(keys)
            for (i in keys.indices) {
                val key = keys[i]
                val value = values[i]
                processed.add(key)
                val entry = JSONObject()
                entry.put("key", key)
                entry.put("value", value)
                result.put(entry)
                for (foundKey in extractHashes(value)) {
                    if (!processed.contains(foundKey)) {
                        pending.add(foundKey)
                    }
                }
            }
        }
        return result
    }

    private fun extractHashes(input: String?): List<String> {
        val result: MutableList<String> = ArrayList()
        if (input != null) {
            val matcher = HASH_PATTERN.matcher(input)
            while (matcher.find()) {
                result.add(matcher.group())
            }
        }
        return result
    }

    @RequiresTransaction
    private fun RoutingContext.putEntries(newEntries: Map<String, String?>) {
        val referencedKeys: MutableSet<String> = HashSet()
        for ((key, value) in newEntries) {
            checkKeyPermission(key, EPermissionType.WRITE)
            if (value != null) {
                val matcher = HASH_PATTERN.matcher(value)
                while (matcher.find()) {
                    val foundKey = matcher.group()
                    if (!newEntries.containsKey(foundKey)) {
                        referencedKeys.add(foundKey)
                    }
                }
            }
        }

        // Entries were previously written directly to the store.
        // Now we use the RepositoriesManager to merge changes instead of just overwriting a branch.

        val hashedObjects = LinkedHashMap<String, String>()
        val branchChanges = LinkedHashMap<BranchReference, String?>()
        val userDefinedEntries = LinkedHashMap<String, String?>()

        for ((key, value) in newEntries) {
            switchKeyType(
                key = key,
                immutableObject = {
                    hashedObjects[key] = value ?: throw IllegalArgumentException("No value provided for $key")
                },
                branch = {
                    branchChanges[it] = value
                },
                serverId = {
                    throw NoPermissionException("'$key' is read-only.")
                },
                legacyClientId = {
                    throw NoPermissionException("Directly writing to 'clientId' is not allowed")
                },
                unknown = {
                    userDefinedEntries[key] = value
                },
            )
        }

        HashUtil.checkObjectHashes(hashedObjects)

        for ((branch, value) in branchChanges) {
            require(repositoriesManager.isIsolated(branch.repositoryId) != true) {
                "Writing to repository ${branch.repositoryId} is not supported by this API"
            }
            // We cannot reliably know in which repository to store the objects, because the objects may be uploaded
            // in multiple request.
            // We could try to move the objects later, but since this API is deprecated, it's not worth the effort.
        }

        stores.genericStore.putAll(hashedObjects.mapKeys { ObjectInRepository.global(it.key) })
        stores.genericStore.putAll(userDefinedEntries.mapKeys { ObjectInRepository.global(it.key) })
        for ((branch, value) in branchChanges) {
            if (value == null) {
                checkPermission(ModelServerPermissionSchema.branch(branch).delete)
                repositoriesManager.removeBranches(branch.repositoryId, setOf(branch.branchName))
            } else {
                checkPermission(ModelServerPermissionSchema.branch(branch).push)
                repositoriesManager.mergeChanges(branch, value)
            }
        }
    }

    private suspend fun RoutingContext.respondValue(key: String, value: String?) {
        if (value == null) {
            throw HttpException(HttpStatusCode.NotFound, details = "key '$key' not found")
        } else {
            call.respondText(text = value, contentType = ContentType.Text.Plain)
        }
    }

    @Throws(IOException::class)
    private fun RoutingContext.checkKeyPermission(key: String, type: EPermissionType) {
        val isWrite = type == EPermissionType.WRITE
        switchKeyType(
            key = key,
            immutableObject = {
                call.checkPermission(ModelServerPermissionSchema.legacyGlobalObjects.run { if (isWrite) add else read })
                return
            },
            branch = {
                call.checkPermission(ModelServerPermissionSchema.branch(it).run { if (isWrite) push else pull })
            },
            serverId = {
                if (isWrite) throw NoPermissionException("'$key' is read-only.")
            },
            legacyClientId = {},
            unknown = {
                call.checkPermission(ModelServerPermissionSchema.legacyUserDefinedObjects.run { if (isWrite) write else read })
            },
        )
    }

    private inline fun <R> switchKeyType(
        key: String,
        immutableObject: () -> R,
        branch: (branch: BranchReference) -> R,
        serverId: () -> R,
        legacyClientId: () -> R,
        unknown: () -> R,
    ): R {
        return when {
            HashUtil.isSha256(key) -> immutableObject()
            BranchReference.tryParseBranch(key) != null -> branch(BranchReference.tryParseBranch(key)!!)
            key.startsWith(PROTECTED_PREFIX) -> throw NoPermissionException("Access to keys starting with '$PROTECTED_PREFIX' is only permitted to the model server itself.")
            key.startsWith(RepositoriesManager.KEY_PREFIX) -> throw NoPermissionException("Access to keys starting with '${RepositoriesManager.KEY_PREFIX}' is only permitted to the model server itself.")
            key == RepositoriesManager.LEGACY_SERVER_ID_KEY || key == RepositoriesManager.LEGACY_SERVER_ID_KEY2 -> serverId()
            key == "clientId" -> legacyClientId()
            else -> unknown()
        }
    }

    private suspend fun <R> runRead(body: () -> R): R {
        return repositoriesManager.getTransactionManager().runReadIO(body)
    }

    private suspend fun <R> runWrite(body: () -> R): R {
        return repositoriesManager.getTransactionManager().runWriteIO(body)
    }
}
