package org.modelix.model.server

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException

internal class SqlUtils(private val connection: Connection) {
    @Throws(SQLException::class)
    fun isSchemaExisting(schemaName: String): Boolean {
        val metadata: DatabaseMetaData = connection.metaData
        val schemasRS: ResultSet = metadata.getSchemas()
        while (schemasRS.next()) {
            if (schemasRS.getString("table_schem") == schemaName) {
                return true
            }
        }
        return false
    }

    @Throws(SQLException::class)
    fun isTableExisting(schemaName: String, tableName: String): Boolean {
        val metadata: DatabaseMetaData = connection.metaData
        val schemasRS: ResultSet = metadata.getTables(null, schemaName, tableName, null)
        while (schemasRS.next()) {
            if (schemasRS.getString("table_schem") == schemaName && schemasRS.getString("table_name") == tableName) {
                return true
            }
        }
        return false
    }

    @Throws(SQLException::class)
    fun isColumnExisting(schemaName: String, tableName: String, columnName: String): Boolean {
        val metadata: DatabaseMetaData = connection.metaData
        val schemasRS: ResultSet = metadata.getColumns(null, schemaName, tableName, columnName)
        while (schemasRS.next()) {
            if (schemasRS.getString("table_schem") == schemaName &&
                schemasRS.getString("table_name") == tableName &&
                schemasRS.getString("column_name") == columnName
            ) {
                return true
            }
        }
        return false
    }

    @Throws(SQLException::class)
    fun ensureTableIsPresent(
        schemaName: String,
        username: String,
        tableName: String,
        creationSql: String,
    ) {
        if (!isTableExisting(schemaName, tableName)) {
            val stmt = connection.createStatement()
            stmt.execute(creationSql)
        }
        try {
            val stmt = connection.createStatement()
            stmt.execute("GRANT ALL ON TABLE $schemaName.$tableName TO $username;")
        } catch (ex: SQLException) {
            LOG.error("Failed to change permissions on $schemaName.$tableName", ex)
        }
    }

    @Throws(SQLException::class)
    fun ensureColumnIsPresent(
        schemaName: String,
        tableName: String,
        columnName: String,
        creationSql: String,
    ) {
        if (!isColumnExisting(schemaName, tableName, columnName)) {
            val stmt = connection.createStatement()
            stmt.execute(creationSql)
        }
    }

    @Throws(SQLException::class)
    fun isIndexExisting(schemaName: String, tableName: String, indexName: String): Boolean {
        val metadata: DatabaseMetaData = connection.metaData
        val indexRS: ResultSet = metadata.getIndexInfo(null, schemaName, tableName, false, false)
        while (indexRS.next()) {
            if (indexRS.getString("INDEX_NAME") == indexName) {
                return true
            }
        }
        return false
    }

    /**
     * Checks whether an index with the given name exists but is marked INVALID in the catalog.
     *
     * An index built with `CREATE INDEX CONCURRENTLY` that is interrupted (e.g. the connection or
     * pod dies mid-build) is left behind in an INVALID state: it is unusable by the query planner,
     * yet a later `CREATE INDEX IF NOT EXISTS` — and [isIndexExisting], which via `getIndexInfo`
     * also reports invalid indexes — would treat the name as taken and never rebuild it. Detecting
     * the invalid state lets the caller drop and recreate the index instead of skipping it forever.
     *
     * Returns `false` when no such index exists (`to_regclass` yields NULL, matching no row).
     */
    @Throws(SQLException::class)
    fun isIndexInvalid(schemaName: String, indexName: String): Boolean {
        connection.prepareStatement(
            """
                SELECT NOT i.indisvalid AS is_invalid
                FROM pg_index i
                WHERE i.indexrelid = to_regclass(?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, "$schemaName.$indexName")
            stmt.executeQuery().use { rs ->
                return rs.next() && rs.getBoolean("is_invalid")
            }
        }
    }

    /**
     * Ensures the index exists and is valid, (re)building it via the supplied [creationSql].
     *
     * The caller is expected to supply a `CREATE INDEX CONCURRENTLY` statement so schema
     * initialization does not take a table-level `ShareLock` that would block every writer for the
     * whole (potentially minutes-long, multi-GB) build. `CREATE`/`DROP INDEX CONCURRENTLY` may not
     * run inside a transaction block, so the connection must be in autocommit mode. The
     * model-server's `PGPoolingDataSource` hands out autocommit connections and the other `ensure*`
     * DDL here already relies on that; should autocommit somehow be off we enable it for these
     * statements and restore the previous value afterwards.
     *
     * If a leftover INVALID index (see [isIndexInvalid]) is present it is dropped — also
     * CONCURRENTLY — before recreating. The operation is idempotent: valid index → no-op, absent →
     * create, invalid → drop and recreate.
     */
    @Throws(SQLException::class)
    fun ensureIndexIsPresent(
        schemaName: String,
        tableName: String,
        indexName: String,
        creationSql: String,
    ) {
        val previousAutoCommit = connection.autoCommit
        if (!previousAutoCommit) {
            connection.autoCommit = true
        }
        try {
            if (isIndexInvalid(schemaName, indexName)) {
                LOG.warn("Dropping leftover invalid index $schemaName.$indexName before rebuilding it.")
                val dropStmt = connection.createStatement()
                dropStmt.execute("DROP INDEX CONCURRENTLY IF EXISTS $schemaName.$indexName;")
            }
            if (!isIndexExisting(schemaName, tableName, indexName)) {
                val stmt = connection.createStatement()
                stmt.execute(creationSql)
            }
        } finally {
            if (!previousAutoCommit) {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    @Throws(SQLException::class)
    fun ensureSchemaIsPresent(schemaName: String, username: String) {
        if (!isSchemaExisting(schemaName)) {
            val stmt = connection.createStatement()
            stmt.execute("CREATE SCHEMA $schemaName;")
        }
        try {
            val stmt = connection.createStatement()
            stmt.execute("GRANT ALL ON SCHEMA $schemaName TO $username;")
        } catch (ex: SQLException) {
            LOG.error("Failed to change permissions on $schemaName", ex)
        }
    }

    fun ensureSchemaInitialization() {
        var userName = System.getProperty("jdbc.user")
        if (userName == null) {
            userName = DEFAULT_DB_USER_NAME
        }
        var schemaName = System.getProperty("jdbc.schema")
        if (schemaName == null) {
            schemaName = DEFAULT_SCHEMA_NAME
        }
        LOG.info("ensuring schema initialization")
        LOG.info("  schema: $schemaName")
        LOG.info("  db username: $userName")
        try {
            ensureSchemaIsPresent(schemaName, userName)
            ensureTableIsPresent(
                schemaName,
                userName,
                "model",
                """
                    CREATE TABLE $schemaName.model (
                        key VARCHAR NOT NULL,
                        value VARCHAR,
                        CONSTRAINT kv_pkey PRIMARY KEY (key)
                    );
                """,
            )
            ensureColumnIsPresent(
                schemaName,
                "model",
                "repository",
                """
                    alter table $schemaName.model
                        add repository VARCHAR default '' not null;

                    alter table $schemaName.model
                        drop constraint kv_pkey;

                    alter table $schemaName.model
                        add constraint model_pkey
                            primary key (key, repository);
                """,
            )
            // The primary key `(key, repository)` is key-leading, so an equality filter on the
            // trailing `repository` column cannot use it. Repository deletion runs
            // `DELETE FROM model WHERE repository = ?`, which would otherwise degrade to a
            // sequential scan over the entire table (all repositories) and time out for large
            // deployments. A `repository`-leading index makes that DELETE an index scan.
            //
            // Build the index CONCURRENTLY so this schema-init step does not take a table-level
            // ShareLock that blocks all writers for the whole build (on large, multi-GB tables that
            // build takes long enough to stall a rolling deploy or trip a pod's startup/liveness
            // probe). CONCURRENTLY requires autocommit and cannot run in a transaction block; the
            // datasource connection is autocommit, which `ensureIndexIsPresent` also verifies.
            ensureIndexIsPresent(
                schemaName,
                "model",
                "model_repository_idx",
                "CREATE INDEX CONCURRENTLY IF NOT EXISTS model_repository_idx ON $schemaName.model (repository);",
            )
        } catch (e: SQLException) {
            LOG.error("Failed to initialize the database schema", e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SqlUtils::class.java)
        private const val DEFAULT_DB_USER_NAME = "modelix"
        private const val DEFAULT_SCHEMA_NAME = "modelix"
    }
}
