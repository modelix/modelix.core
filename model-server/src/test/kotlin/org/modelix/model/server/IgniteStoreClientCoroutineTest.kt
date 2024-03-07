/*
 * Copyright (c) 2023.
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

package org.modelix.model.server

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.modelix.model.server.store.IgniteStoreClient
import kotlin.test.AfterTest
import kotlin.test.Test

class IgniteStoreClientCoroutineTest {

    val store = IgniteStoreClient(inmemory = true)

    @AfterTest
    fun cleanup() {
        store.close()
    }

    @Test
    fun `different coroutine can enter different transactions`() = runTest {
        var job2TransactionEntered = false

        val job1 = launch {
            println("launching job1")
            store.runTransactionSuspendable {
                println("entering job1")
                while (!job2TransactionEntered) {
                    println("waiting job2 to enter different transaction")
                    Thread.sleep(1000)
                }
                store.put("key1", "value1")
            }
        }

        val job2 = launch {
            println("launching job2")
            store.runTransactionSuspendable {
                println("entering job2")
                job2TransactionEntered = true
                store.put("key2", "value2")
            }
        }

        job1.join()
        job2.join()
        // Does not terminate, because mutex locks one transaction at a time
    }

    @Test
    fun `different coroutine can enter different transactions (suspendable body)`() = runTest {
        var job2TransactionEntered = false

        val job1 = launch {
            println("launching job1")
            store.runTransactionSuspendableWithSuspendableBody {
                println("entering job1")
                while (!job2TransactionEntered) {
                    println("waiting job2 to enter different transaction")
                    delay(1000)
                }
                store.put("key1", "value1")
            }
        }

        val job2 = launch {
            println("launching job2")
            store.runTransactionSuspendableWithSuspendableBody {
                println("entering job2")
                job2TransactionEntered = true
                store.put("key2", "value2")
            }
        }

        job1.join()
        job2.join()
        // Does not fail, but wrongly reuses transaction:
        // out: IgniteStoreClient: Reusing transaction TransactionProxyImpl [tx=GridNearTxLocal [mappings=IgniteTxMappingsImpl [], nearLocallyMapped=false, colocatedLocallyMapped=false, needCheckBackup=null, hasRemoteLocks=false, trackTimeout=false, systemTime=0, systemStartTime=0, prepareStartTime=0, prepareTime=0, commitOrRollbackStartTime=0, commitOrRollbackTime=0, lb=null, mvccOp=null, qryId=-1, crdVer=0, thread=Test worker @coroutine#4, mappings=IgniteTxMappingsImpl [], super=GridDhtTxLocalAdapter [nearOnOriginatingNode=false, span=org.apache.ignite.internal.processors.tracing.NoopSpan@14e3d439, nearNodes=KeySetView [], dhtNodes=KeySetView [], explicitLock=false, super=IgniteTxLocalAdapter [completedBase=null, sndTransformedVals=false, depEnabled=false, txState=IgniteTxStateImpl [activeCacheIds=[], recovery=null, mvccEnabled=null, mvccCachingCacheIds=[], txMap=EmptySet []], super=IgniteTxAdapter [xidVer=GridCacheVersion [topVer=321294753, order=1709814750692, nodeOrder=1, dataCenterId=0], writeVer=null, implicit=false, loc=true, threadId=1, startTime=1709814753046, nodeId=effd1a64-2433-455e-ba08-ca7948a5cfaa, isolation=REPEATABLE_READ, concurrency=PESSIMISTIC, timeout=0, sysInvalidate=false, sys=false, plc=2, commitVer=null, finalizing=NONE, invalidParts=null, state=ACTIVE, timedOut=false, topVer=AffinityTopologyVersion [topVer=-1, minorTopVer=0], mvccSnapshot=null, incSnpId=null, skipCompletedVers=false, parentTx=null, duration=13ms, onePhaseCommit=false], size=0]]], async=false, asyncRes=null]
    }
}