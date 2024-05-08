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
