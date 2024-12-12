package org.modelix.model.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.modelix.model.server.store.TransactionLocks
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionLocksTest {

    @Test
    fun `read inside write disables writes`() {
        val locks = TransactionLocks()
        locks.runWrite {
            assertTrue(locks.canWrite())
            locks.runRead {
                assertFalse(locks.canWrite())
            }
        }
    }

    @Test
    fun `write inside read isn't allowed`() {
        val locks = TransactionLocks()
        locks.runRead {
            assertFailsWith<IllegalStateException> {
                locks.runWrite {}
            }
        }
    }

    @Test
    fun `cannot read outside transaction`() {
        val locks = TransactionLocks()
        assertFalse(locks.canRead())
    }

    @Test
    fun `cannot write outside transaction`() {
        val locks = TransactionLocks()
        assertFalse(locks.canWrite())
    }

    @Test
    fun `read transaction cannot write`() {
        val locks = TransactionLocks()
        locks.runRead {
            assertFalse(locks.canWrite())
        }
    }

    @Test
    fun `read transaction can read`() {
        val locks = TransactionLocks()
        locks.runRead {
            assertTrue(locks.canRead())
        }
    }

    @Test
    fun `write transaction can read`() {
        val locks = TransactionLocks()
        locks.runWrite {
            assertTrue(locks.canRead())
        }
    }

    @Test
    fun `write transaction can write`() {
        val locks = TransactionLocks()
        locks.runWrite {
            assertTrue(locks.canWrite())
        }
    }

    @Test
    fun `write lock is exclusive`() = runTest {
        val locks = TransactionLocks()
        val activeWrites = AtomicInteger(0)

        launch(Dispatchers.IO) {
            locks.runWrite {
                activeWrites.runWithIncremented {
                    Thread.sleep(50)
                    assertEquals(1, activeWrites.get())
                    Thread.sleep(50)
                }
            }
        }

        launch(Dispatchers.IO) {
            locks.runWrite {
                activeWrites.runWithIncremented {
                    Thread.sleep(50)
                    assertEquals(1, activeWrites.get())
                    Thread.sleep(50)
                }
            }
        }
    }

    @Test
    fun `read prevents writes`() = runTest {
        val locks = TransactionLocks()
        val activeWrites = AtomicInteger(0)
        val activeReads = AtomicInteger(0)

        launch(Dispatchers.IO) {
            locks.runRead {
                activeReads.runWithIncremented {
                    Thread.sleep(50)
                    assertEquals(1, activeReads.get())
                    assertEquals(0, activeWrites.get())
                    Thread.sleep(50)
                }
            }
        }

        launch(Dispatchers.IO) {
            Thread.sleep(50)
            locks.runWrite {
                activeWrites.runWithIncremented {
                    assertEquals(0, activeReads.get())
                    assertEquals(1, activeWrites.get())
                    Thread.sleep(50)
                }
            }
        }
    }

    @Test
    fun `multiple reads are allowed`() = runTest {
        val locks = TransactionLocks()
        val activeReads = AtomicInteger(0)

        launch(Dispatchers.IO) {
            locks.runRead {
                activeReads.runWithIncremented {
                    Thread.sleep(50)
                    assertEquals(2, activeReads.get())
                    Thread.sleep(50)
                }
            }
        }

        launch(Dispatchers.IO) {
            locks.runRead {
                activeReads.runWithIncremented {
                    Thread.sleep(50)
                    assertEquals(2, activeReads.get())
                    Thread.sleep(50)
                }
            }
        }
    }

    @Test
    fun `read inside read`() = runTest {
        val locks = TransactionLocks()

        locks.runRead {
            assertTrue(locks.canRead())
            assertFalse(locks.canWrite())
            locks.runRead {
                assertTrue(locks.canRead())
                assertFalse(locks.canWrite())
            }
        }
    }

    @Test
    fun `write inside write`() = runTest {
        val locks = TransactionLocks()

        locks.runWrite {
            assertTrue(locks.canWrite())
            locks.runWrite {
                assertTrue(locks.canWrite())
            }
        }
    }
}

private fun AtomicInteger.runWithIncremented(body: () -> Unit) {
    incrementAndGet()
    try {
        body()
    } finally {
        decrementAndGet()
    }
}
