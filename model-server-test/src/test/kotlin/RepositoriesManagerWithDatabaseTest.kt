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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.RepositoriesManagerTest
import org.modelix.model.server.store.IgniteStoreClient
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoriesManagerWithDatabaseTest : RepositoriesManagerTest(IgniteStoreClient()) {
    private fun getDbConnection() = DriverManager.getConnection(System.getProperty("jdbc.url"), "modelix", "modelix")

    private val existingRepo = RepositoryId("existing")
    private val repoToBeDeleted = RepositoryId("tobedeleted")

    @AfterTest
    fun deleteTestDataFromDatabase() {
        runBlocking {
            repoManager.removeRepository(existingRepo)
            repoManager.removeRepository(repoToBeDeleted)
        }
        getDbConnection().prepareStatement("DELETE FROM modelix.model WHERE repository IN (?,?)").use {
            it.setString(1, existingRepo.id)
            it.setString(2, repoToBeDeleted.id)
            it.execute()
        }
    }

    @Test
    fun `database does not contain removed data that was not part of the cache`() = runTest {
        initRepository(repoToBeDeleted)

        getDbConnection().use { connection ->
            connection.prepareStatement("INSERT INTO modelix.model (repository, key, value) VALUES (?, 'myKey', 'myValue')")
                .use {
                    it.setString(1, repoToBeDeleted.id)
                    it.execute()
                    check(it.updateCount == 1)
                }

            repoManager.removeRepository(repoToBeDeleted)

            connection.prepareStatement("SELECT * FROM modelix.model WHERE repository = ?").use {
                it.setString(1, repoToBeDeleted.id)
                val result = it.executeQuery()
                assertFalse("Database contained leftover repository data.") { result.isBeforeFirst }
            }
        }
    }

    @Test
    fun `removal does not affect other repository data in the database`() = runTest {
        initRepository(existingRepo)
        initRepository(repoToBeDeleted)

        getDbConnection().use { connection ->
            connection.prepareStatement("INSERT INTO modelix.model (repository, key, value) VALUES (?, 'myKey', 'myValue')")
                .apply {
                    setString(1, existingRepo.id)
                    execute()
                    check(updateCount == 1)
                    close()
                }

            repoManager.removeRepository(repoToBeDeleted)

            val statement = connection.prepareStatement("SELECT * FROM modelix.model WHERE repository = ?").apply {
                setString(1, existingRepo.id)
            }
            statement.use {
                val result = it.executeQuery()
                assertTrue("Other repository data was removed from the database.") { result.isBeforeFirst }
            }
        }
    }
}
