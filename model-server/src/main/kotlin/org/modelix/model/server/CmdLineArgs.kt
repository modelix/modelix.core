package org.modelix.model.server

import com.beust.jcommander.Parameter
import com.beust.jcommander.converters.BooleanConverter
import com.beust.jcommander.converters.IntegerConverter
import com.beust.jcommander.converters.StringConverter
import java.io.File
import java.util.LinkedList

internal class CmdLineArgs {
    @Parameter(names = ["-secret", "--secret"], description = "Path to the secretfile", converter = FileConverter::class)
    var secretFile = File("/secrets/modelsecret/modelsecret.txt")

    @Parameter(
        names = ["-jdbcconf", "--jdbcconf"],
        description = "Path to the JDBC configuration file",
        converter = FileConverter::class,
    )
    var jdbcConfFile: File? = null

    @Parameter(names = ["-inmemory", "--inmemory"], description = "Use in-memory storage", converter = BooleanConverter::class)
    var inmemory = false

    @Parameter(names = ["--local-persistence"], description = "Use ignite with local disk persistence", converter = BooleanConverter::class)
    var localPersistence = false

    @Parameter(names = ["-dumpout", "--dumpout"], description = "Dump in memory storage", converter = StringConverter::class)
    var dumpOutName: String? = null

    @Parameter(names = ["-dumpin", "--dumpin"], description = "Read dump in memory storage", converter = StringConverter::class)
    var dumpInName: String? = null

    @Parameter(names = ["-port", "--port"], description = "Set port", converter = IntegerConverter::class)
    var port: Int? = null

    // 10 seconds was the default value in NettyApplicationEngine when this option was implemented.
    @Parameter(
        names = ["-response-write-timeout-seconds", "--response-write-timeout-seconds"],
        description = "Timeout in seconds for sending responses to client. Values smaller or equal to 0 disable the timeout. Defaults to 10 seconds if unset.",
        converter = IntegerConverter::class,
    )
    var responseWriteTimeoutSeconds: Int = 10

    @Parameter(names = ["-set", "--set"], description = "Set values", arity = 2)
    var setValues: List<String> = LinkedList<String>()

    @Parameter(
        names = ["-schemainit", "--schemainit"],
        description = "Initialize the schema, if necessary",
        converter = BooleanConverter::class,
    )
    var schemaInit = false

    @Parameter(names = ["-noswagger", "--noswagger"], description = "Disables SwaggerUI at endpoint '/swagger'", converter = BooleanConverter::class)
    var noSwaggerUi: Boolean = false

    @Parameter(names = ["-h", "--help"], help = true)
    var help = false
}
