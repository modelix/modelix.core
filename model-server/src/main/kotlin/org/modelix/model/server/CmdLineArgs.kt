/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.server

import com.beust.jcommander.Parameter
import com.beust.jcommander.converters.BooleanConverter
import com.beust.jcommander.converters.IntegerConverter
import com.beust.jcommander.converters.StringConverter
import java.io.File
import java.util.LinkedList

internal class CmdLineArgs {
    @Parameter(names = ["-secret"], description = "Path to the secretfile", converter = FileConverter::class)
    var secretFile = File("/secrets/modelsecret/modelsecret.txt")

    @Parameter(
        names = ["-jdbcconf"],
        description = "Path to the JDBC configuration file",
        converter = FileConverter::class,
    )
    var jdbcConfFile: File? = null

    @Parameter(names = ["-inmemory"], description = "Use in-memory storage", converter = BooleanConverter::class)
    var inmemory = false

    @Parameter(names = ["-dumpout"], description = "Dump in memory storage", converter = StringConverter::class)
    var dumpOutName: String? = null

    @Parameter(names = ["-dumpin"], description = "Read dump in memory storage", converter = StringConverter::class)
    var dumpInName: String? = null

    @Parameter(names = ["-port"], description = "Set port", converter = IntegerConverter::class)
    var port: Int? = null

    @Parameter(names = ["-set"], description = "Set values", arity = 2)
    var setValues: List<String> = LinkedList<String>()

    @Parameter(
        names = ["-schemainit"],
        description = "Initialize the schema, if necessary",
        converter = BooleanConverter::class,
    )
    var schemaInit = false

    @Parameter(names = ["-h", "--help"], help = true)
    var help = false
}
