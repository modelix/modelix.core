package org.modelix.model.server

import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.PermissionSchemaBase
import org.modelix.authorization.permissions.buildPermissionSchema
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId

object ModelServerPermissionSchema {
    private const val MODEL_SERVER = "model-server"
    private const val ADMIN = "admin"
    private const val WRITE = "write"
    private const val READ = "read"
    private const val LEGACY_USER_DEFINED_ENTRIES = "legacy-user-defined-entries"
    private const val LEGACY_GLOBAL_OBJECTS = "legacy-global-objects"
    private const val ADD = "add"
    private const val REPOSITORY = "repository"
    private const val NAME = "name"
    private const val REWRITE = "rewrite"
    private const val DELETE = "delete"
    private const val CREATE = "create"
    private const val LIST = "list"
    private const val BRANCH = "branch"
    private const val ANY_BRANCH = "any-branch"
    private const val OBJECTS = "objects"
    private const val FORCE_PUSH = "force-push"
    private const val PUSH = "push"
    private const val PULL = "pull"
    private const val QUERY = "query"

    val SCHEMA = buildPermissionSchema {
        resource(MODEL_SERVER) {
            permission(ADMIN) {
                includedIn(PermissionSchemaBase.cluster.admin.parts[0], PermissionSchemaBase.cluster.admin.parts[1])
            }
        }

        resource(LEGACY_USER_DEFINED_ENTRIES) {
            permission(WRITE) {
                includedIn(MODEL_SERVER, ADMIN)
                permission(READ)
            }
        }

        resource(LEGACY_GLOBAL_OBJECTS) {
            permission(ADD) {
                includedIn(MODEL_SERVER, ADMIN)
                permission(READ)
            }
        }

        resource(REPOSITORY) {
            parameter(NAME)

            permission(ADMIN) {
                includedIn(MODEL_SERVER, ADMIN)
                permission(REWRITE) {
                    permission(DELETE)
                    permission(WRITE) {
                        permission(CREATE)
                        permission(READ) {
                            permission(LIST) {
                                includedIn(BRANCH, LIST)
                            }
                        }
                    }
                }
            }

            resource(OBJECTS) {
                permission(READ) {
                    includedIn(REPOSITORY, READ)
                    includes(LEGACY_GLOBAL_OBJECTS, READ)
                }
                permission(ADD) {
                    includedIn(REPOSITORY, WRITE)
                    includes(LEGACY_GLOBAL_OBJECTS, ADD)
                    description("Can add new objects, but not modify existing ones.")
                }
            }

            resource(BRANCH) {
                parameter(NAME)

                permission(ADMIN) {
                    includedIn(REPOSITORY, ADMIN)
                    includedIn(ANY_BRANCH, ADMIN)
                    permission(REWRITE) {
                        includedIn(REPOSITORY, REWRITE)
                        includedIn(ANY_BRANCH, REWRITE)
                        description("Destructive write operations that change the history and loses previously pushed changes.")

                        permission(DELETE) {
                            includedIn(ANY_BRANCH, DELETE)
                        }
                        permission(WRITE) {
                            description("Non-destructive write operations that preserve the history.")
                            includedIn(REPOSITORY, WRITE)
                            includedIn(ANY_BRANCH, WRITE)
                            includes(LEGACY_USER_DEFINED_ENTRIES, WRITE)
                            permission(CREATE) {
                                description("Can create a branch with this name, if it doesn't exist yet.")
                                includedIn(ANY_BRANCH, CREATE)
                            }
                            permission(PUSH) {
                                description("Add changes to a branch and merge it with the current version.")
                                includes(OBJECTS, ADD)
                                includedIn(ANY_BRANCH, PUSH)
                            }
                            permission(READ) {
                                includes(LEGACY_USER_DEFINED_ENTRIES, READ)
                                includedIn(REPOSITORY, READ)
                                includedIn(ANY_BRANCH, READ)
                                permission(LIST) {
                                    includes(REPOSITORY, LIST)
                                    description("Allows to know its existence and name, but not the content.")
                                    includedIn(ANY_BRANCH, LIST)
                                }
                                permission(PULL) {
                                    description("Allows reading the version hash. Permissions on objects are checked on repository level, which mean if a client knows the hash it can still read the content.")
                                    includes(OBJECTS, READ)
                                    includedIn(ANY_BRANCH, PULL)
                                }
                                permission(QUERY) {
                                    description("Allows the execution of ModelQL queries.")
                                    includedIn(ANY_BRANCH, QUERY)
                                }
                            }
                        }

                        permission(FORCE_PUSH) {
                            description("Overwrite the current version. Don't do any merges and don't prevent losing history.")
                            includes(PUSH)
                            includedIn(ANY_BRANCH, FORCE_PUSH)
                        }
                    }
                }
            }

            resource(ANY_BRANCH) {
                copyPermissionsFrom(REPOSITORY, BRANCH)
            }
        }
    }

    fun repository(id: RepositoryId) = Repository(id)
    fun repository(id: String) = Repository(RepositoryId(id))
    fun branch(branchRef: BranchReference) = Branch(branchRef)

    @Suppress("ClassName")
    object modelServer {
        val resource: PermissionParts get() = PermissionParts(MODEL_SERVER)
        val admin = resource + ADMIN
    }

    @Suppress("ClassName")
    object legacyGlobalObjects {
        val resource: PermissionParts get() = PermissionParts(LEGACY_GLOBAL_OBJECTS)
        val read = resource + READ
        val add = resource + ADD
    }

    @Suppress("ClassName")
    object legacyUserDefinedObjects {
        val resource: PermissionParts get() = PermissionParts(LEGACY_USER_DEFINED_ENTRIES)
        val read = resource + READ
        val write = resource + WRITE
    }

    class Repository(private val repositoryId: RepositoryId) {
        val resource: PermissionParts get() = PermissionParts(REPOSITORY, repositoryId.id)
        fun branch(branchName: String) = Branch(repositoryId.getBranchReference(branchName))
        val create: PermissionParts get() = resource + CREATE
        val list: PermissionParts get() = resource + LIST
        val read: PermissionParts get() = resource + READ
        val write: PermissionParts get() = resource + WRITE
        val rewrite: PermissionParts get() = resource + REWRITE
        val delete: PermissionParts get() = resource + DELETE
        val objects: Objects get() = Objects()

        inner class Objects {
            val resource: PermissionParts get() = this@Repository.resource + OBJECTS
            val read = resource + READ
            val add = resource + ADD
        }
    }

    class Branch(private val branchRef: BranchReference) {
        val resource: PermissionParts get() = Repository(branchRef.repositoryId).resource + BRANCH + branchRef.branchName
        val read: PermissionParts get() = resource + READ
        val write: PermissionParts get() = resource + WRITE
        val rewrite: PermissionParts get() = resource + REWRITE
        val push: PermissionParts get() = resource + PUSH
        val pull: PermissionParts get() = resource + PULL
        val forcePush: PermissionParts get() = resource + FORCE_PUSH
        val delete: PermissionParts get() = resource + DELETE
        val list: PermissionParts get() = resource + LIST
        val query: PermissionParts get() = resource + QUERY
    }
}
