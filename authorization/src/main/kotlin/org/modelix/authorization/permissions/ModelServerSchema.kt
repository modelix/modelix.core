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

val baseSchema = buildSchema {
    definition("group") {
    }
    definition("user") {
    }
}

val modelServerSchema = buildSchema {
    extends(baseSchema)

    definition("model-server") {

        permission("admin") {

        }

    }

    definition("permission-schema") {
        permission("write") {
            includedIn("model-server", "admin")
            permission("read")
        }
    }

    definition("repository") {
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


        definition("objects") {
            permission("read") {
            }
        }

        definition("branch") {
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
                            description("Add changes to a branch and merge it with the current version ")
                        }
                        permission("read") {
                            permission("list") {
                                includes("repository", "list")
                                description("Allowed to know its existence and name, but not the content.")
                            }
                            permission("pull") {
                                description("Allowed reading the version hash. Permissions on objects is checked on repository level, which mean if a client knows the hash it can still read the content.")
                                includes("objects", "read")
                            }
                        }
                    }
                }
            }
        }
    }

}

val workspacesSchema = buildSchema {
    extends(modelServerSchema)
    val repositoryNamePrefix = "workspace-"

    definition("workspaces") {
        permission("admin") {
            permission("manage") {
                permission("create")
            }
        }
    }

    definition("workspace") {
        parameter("id")

        relation("model-repository") {
            targetDefinition("repository")
            targetParameterValue("name", sourceParameterValue("id").withPrefix("workspace-"))
        }

        definition("config") {
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
