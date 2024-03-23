/*
 * Copyright (c) 2024.
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

package org.modelix.authorization.permissions

val modelServerSchema = buildPermissionSchema {
    resource("model-server") {

        permission("admin") {
        }

        permission("generate-client-id")
    }

    resource("permission-schema") {
        permission("write") {
            includedIn("model-server", "admin")
            permission("read")
        }
    }

    resource("legacy-global-objects") {
        permission("read")
    }

    resource("repository") {
        parameter("name")

        permission("admin") {
            includedIn("model-server", "admin")
            permission("rewrite") {
                permission("delete")
                permission("write") {
                    permission("create")
                    permission("read") {
                        permission("list") {
                            includedIn("branch", "list")
                        }
                    }
                }
            }
        }

        resource("objects") {
            permission("read") {
                includedIn("repository", "read")
            }
            permission("add") {
                includedIn("repository", "write")
                description("Can add new objects, but not modify existing ones.")
            }
        }

        resource("branch") {
            parameter("name")

            permission("admin") {
                includedIn("repository", "admin")
                permission("rewrite") {
                    includedIn("repository", "rewrite")
                    description("Destructive write operations that change the history and loses previously pushed changes.")

                    permission("force-push") {
                        description("Overwrite the current version. Don't do any merges and don't prevent losing history.")
                    }
                    permission("delete")
                    permission("write") {
                        description("Non-destructive write operations that preserve the history.")
                        includedIn("repository", "write")
                        permission("create") {
                            description("Can create a branch with this name, if it doesn't exist yet.")
                        }
                        permission("push") {
                            description("Add changes to a branch and merge it with the current version.")
                        }
                        permission("read") {
                            permission("list") {
                                includes("repository", "list")
                                description("Allows to know its existence and name, but not the content.")
                            }
                            permission("pull") {
                                description("Allows reading the version hash. Permissions on objects are checked on repository level, which mean if a client knows the hash it can still read the content.")
                                includes("objects", "read")
                            }
                            permission("query") {
                                description("Allows the execution of ModelQL queries.")
                            }
                        }
                    }
                }
            }
        }
    }
}

val workspacesSchema = buildPermissionSchema {
    extends(modelServerSchema)

    resource("workspaces") {
        permission("admin") {
            permission("manage") {
                permission("create")
            }
        }
    }

    resource("workspace") {
        parameter("id")

        relation("model-repository") {
            targetResource("repository")
            targetParameterValue("name", sourceParameterValue("id").withPrefix("workspace-"))
        }

        resource("config") {
            permission("write") {
                permission("read")
            }
        }

        permission("manage") {
            includes("config", "write")
            permission("create")
            permission("edit") {
                permission("write-model") {
                    includes("repository", "write")
                }
                permission("view") {
                    permission("start")
                    permission("read-model") {
                        includes("repository", "read")
                    }
                }
            }
        }
    }
}
