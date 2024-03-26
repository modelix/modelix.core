package org.modelix.model.server

import com.beust.jcommander.Parameter
import com.beust.jcommander.converters.BooleanConverter
import com.beust.jcommander.converters.IntegerConverter
import com.beust.jcommander.converters.StringConverter
import java.io.File
import java.util.LinkedList

internal class CmdLineArgs {

    companion object {
        // When this option was implemented, the default value was 10s
        // in [NettyApplicationEngine.Configuration.responseWriteTimeoutSeconds].
        // A higher default makes sense because an initial pull of the repository might take longer.
        // Two minutes is a default to accommodate most use cases.
        private const val DEFAULT_RESPONSE_WRITE_TIMEOUT_SECONDS: Int = 2 * 60
    }

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

    @Parameter(
        names = ["-response-write-timeout-seconds", "--response-write-timeout-seconds"],
        description = "Timeout in seconds for sending responses to clients. Values smaller or equal to 0 disable the timeout. Defaults to $DEFAULT_RESPONSE_WRITE_TIMEOUT_SECONDS seconds if unset.",
        converter = IntegerConverter::class,
    )
    var responseWriteTimeoutSeconds: Int = DEFAULT_RESPONSE_WRITE_TIMEOUT_SECONDS

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
