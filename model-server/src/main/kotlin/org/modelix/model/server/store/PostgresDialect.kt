/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.modelix.model.server.store

import org.apache.ignite.cache.store.jdbc.dialect.BasicJdbcDialect
import org.apache.ignite.internal.util.typedef.F

/**
 * Ignite dialect for PostgreSQL
 *
 * Ignite doesn't have an SQL dialect implementation for PostgreSQL.
 * Implementing and using this one enables bulk updates/inserts (upserts).
 *
 * Originally added in https://github.com/modelix/modelix/commit/eee51caddd9f470307febaa01e2820266ddfc6c9
 */
class PostgresDialect : BasicJdbcDialect() {
    override fun hasMerge(): Boolean {
        return true
    }

    override fun mergeQuery(
        fullTblName: String,
        keyCols: Collection<String>,
        uniqCols: Collection<String>,
    ): String {
        // Copied over from org.apache.ignite.cache.store.jdbc.dialect.MySQLDialect.mergeQuery
        val cols = F.concat(false, keyCols, uniqCols)
        val updPart = mkString(
            uniqCols,
            { String.format("%s = excluded.%s", it, it) },
            "",
            ", ",
            "",
        )
        return String.format(
            "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
            fullTblName,
            mkString(cols, ", "),
            repeat("?", cols.size, "", ",", ""),
            mkString(keyCols, ", "),
            updPart,
        )
    }

    override fun loadQuery(
        fullTblName: String,
        keyCols: Collection<String>,
        cols: Iterable<String>,
        keyCnt: Int,
    ): String {
        assert(!keyCols.isEmpty())
        if (keyCols.size == 1 || keyCnt == 1) return super.loadQuery(fullTblName, keyCols, cols, keyCnt)

        /*
            The default implementation generates a query that looks like this:

                SELECT "repository","key","value"
                    FROM "modelix"."model"
                    WHERE
                        (repository=? AND key=?) OR
                        (repository=? AND key=?)

            Postgres creates a query plan that still uses the index, but is not optimal.

            For single column primary keys a query using the IN operator would be generated and that leads to a more
            efficient query plan.

            For the following query a plan is created that is comparable in performance to the IN operator.

                SELECT repository, key, value
                FROM modelix.model
                JOIN (
                    VALUES
                        (?, ?),
                        (?, ?)
                ) as t (k0,k1)
                ON k0 = repository AND k1 = key
         */

        val valuesList = (1..keyCnt).joinToString(", ") { "(?, ?)" }
        val subSelectColumns = keyCols.withIndex().joinToString(",") { "k${it.index}" }
        val joinCondition = keyCols.withIndex().joinToString(" AND ") { "k${it.index} = ${it.value}" }
        val joinPart = """JOIN (VALUES $valuesList) as t ($subSelectColumns) ON $joinCondition"""
        val result = """SELECT ${cols.joinToString(", ")} FROM $fullTblName $joinPart"""
        return result
    }
}
