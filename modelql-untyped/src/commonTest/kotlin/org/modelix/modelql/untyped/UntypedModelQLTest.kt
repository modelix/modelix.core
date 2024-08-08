/*
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
package org.modelix.modelql.untyped

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.modelix.model.api.INode
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.PBranch
import org.modelix.model.api.RoleAccessContext
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import org.modelix.modelql.core.IFluxQuery
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoQuery
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IQueryExecutor
import org.modelix.modelql.core.SimpleQueryExecutor
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.UnboundQuery
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.modelix.model.api.TreePointer
import kotlin.test.assertEquals


class UntypedModelQLTest {
    private val tree = CLTree(NonCachingObjectStore(MapBaseStore()))
    private val branch = TreePointer(tree, IdGenerator.getInstance(1))
    private val rootNode = branch.getRootNode()

    @Test
    fun resolveNodeInQuery() = runTest {
        // Option B: Must user set the scope?
        // rootNode.getArea().runWithAdditionalScopeInCoroutine {
            val resolvedNode = rootNode.query { root ->
                root.nodeReference().resolve()
            }
            assertEquals(rootNode.reference, resolvedNode.reference)
        // }

        // Fails with:
        /**
         * INodeResolutionScope not set
         * java.lang.IllegalStateException: INodeResolutionScope not set
         * 	at org.modelix.model.api.NullNodeResolutionScope.resolveNode(INodeResolutionScope.kt:101)
         * 	at org.modelix.model.api.INodeReferenceKt.resolveIn(INodeReference.kt:58)
         * 	at org.modelix.model.api.INodeReferenceKt.resolveInCurrentContext(INodeReference.kt:48)
         * 	at org.modelix.modelql.untyped.ResolveNodeStep$createFlow$$inlined$map$1$2.emit(Emitters.kt:220)
         * 	at org.modelix.modelql.core.SimpleMonoTransformingStep$createFlow$$inlined$map$1$2.emit(Emitters.kt:219)
         * 	at kotlinx.coroutines.flow.FlowKt__BuildersKt$flowOf$$inlined$unsafeFlow$2.collect(SafeCollector.common.kt:112)
         * 	at org.modelix.modelql.core.SimpleMonoTransformingStep$createFlow$$inlined$map$1.collect(SafeCollector.common.kt:112)
         * 	at org.modelix.modelql.untyped.ResolveNodeStep$createFlow$$inlined$map$1.collect(SafeCollector.common.kt:112)
         * 	at org.modelix.modelql.core.IStepOutputKt$asStepFlow$$inlined$map$1.collect(SafeCollector.common.kt:112)
         * 	at kotlinx.coroutines.flow.FlowKt__ReduceKt.single(Reduce.kt:53)
         * 	at kotlinx.coroutines.flow.FlowKt.single(Unknown Source)
         * 	at org.modelix.modelql.core.MonoBoundQuery.execute(Query.kt:77)
         * 	at org.modelix.modelql.untyped.UntypedModelQLKt.query(UntypedModelQL.kt:80)
         * 	at org.modelix.modelql.untyped.UntypedModelQLTest$resolveNodeInQuery$1.invokeSuspend(UntypedModelQLTest.kt:53)
         * 	at org.modelix.modelql.untyped.UntypedModelQLTest$resolveNodeInQuery$1.invoke(UntypedModelQLTest.kt)
         * 	at org.modelix.modelql.untyped.UntypedModelQLTest$resolveNodeInQuery$1.invoke(UntypedModelQLTest.kt)
         * 	at kotlinx.coroutines.test.TestBuildersKt__TestBuildersKt$runTest$2$1$1.invokeSuspend(TestBuilders.kt:316)
         * 	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
         * 	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
         * 	at kotlinx.coroutines.test.TestDispatcher.processEvent$kotlinx_coroutines_test(TestDispatcher.kt:24)
         * 	at kotlinx.coroutines.test.TestCoroutineScheduler.tryRunNextTaskUnless$kotlinx_coroutines_test(TestCoroutineScheduler.kt:99)
         * 	at kotlinx.coroutines.test.TestBuildersKt__TestBuildersKt$runTest$2$1$workRunner$1.invokeSuspend(TestBuilders.kt:322)
         * 	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
         * 	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
         * 	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:277)
         * 	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:95)
         * 	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:69)
         * 	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
         * 	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:48)
         * 	at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
         * 	at kotlinx.coroutines.test.TestBuildersJvmKt.createTestResult(TestBuildersJvm.kt:10)
         * 	at kotlinx.coroutines.test.TestBuildersKt__TestBuildersKt.runTest-8Mi8wO0(TestBuilders.kt:310)
         * 	at kotlinx.coroutines.test.TestBuildersKt.runTest-8Mi8wO0(Unknown Source)
         * 	at kotlinx.coroutines.test.TestBuildersKt__TestBuildersKt.runTest-8Mi8wO0(TestBuilders.kt:168)
         * 	at kotlinx.coroutines.test.TestBuildersKt.runTest-8Mi8wO0(Unknown Source)
         * 	at kotlinx.coroutines.test.TestBuildersKt__TestBuildersKt.runTest-8Mi8wO0$default(TestBuilders.kt:160)
         * 	at kotlinx.coroutines.test.TestBuildersKt.runTest-8Mi8wO0$default(Unknown Source)
         * 	at org.modelix.modelql.untyped.UntypedModelQLTest.resolveNodeInQuery(UntypedModelQLTest.kt:50)
         * 	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
         * 	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
         * 	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
         * 	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
         * 	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
         * 	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
         * 	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)
         * 	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
         * 	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
         * 	at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)
         * 	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)
         * 	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)
         * 	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)
         * 	at org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
         * 	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
         * 	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
         * 	at org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
         * 	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
         * 	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
         * 	at org.junit.runners.ParentRunner.run(ParentRunner.java:413)
         * 	at org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.runTestClass(JUnitTestClassExecutor.java:112)
         * 	at org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.execute(JUnitTestClassExecutor.java:58)
         * 	at org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.execute(JUnitTestClassExecutor.java:40)
         * 	at org.gradle.api.internal.tasks.testing.junit.AbstractJUnitTestClassProcessor.processTestClass(AbstractJUnitTestClassProcessor.java:60)
         * 	at org.gradle.api.internal.tasks.testing.SuiteTestClassProcessor.processTestClass(SuiteTestClassProcessor.java:52)
         * 	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
         * 	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
         * 	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
         * 	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
         * 	at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:36)
         * 	at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:24)
         * 	at org.gradle.internal.dispatch.ContextClassLoaderDispatch.dispatch(ContextClassLoaderDispatch.java:33)
         * 	at org.gradle.internal.dispatch.ProxyDispatchAdapter$DispatchingInvocationHandler.invoke(ProxyDispatchAdapter.java:94)
         * 	at com.sun.proxy.$Proxy2.processTestClass(Unknown Source)
         * 	at org.gradle.api.internal.tasks.testing.worker.TestWorker$2.run(TestWorker.java:176)
         * 	at org.gradle.api.internal.tasks.testing.worker.TestWorker.executeAndMaintainThreadName(TestWorker.java:129)
         * 	at org.gradle.api.internal.tasks.testing.worker.TestWorker.execute(TestWorker.java:100)
         * 	at org.gradle.api.internal.tasks.testing.worker.TestWorker.execute(TestWorker.java:60)
         * 	at org.gradle.process.internal.worker.child.ActionExecutionWorker.execute(ActionExecutionWorker.java:56)
         * 	at org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker.call(SystemApplicationClassLoaderWorker.java:113)
         * 	at org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker.call(SystemApplicationClassLoaderWorker.java:65)
         * 	at worker.org.gradle.process.internal.worker.GradleWorkerMain.run(GradleWorkerMain.java:69)
         * 	at worker.org.gradle.process.internal.worker.GradleWorkerMain.main(GradleWorkerMain.java:74)
         *
         */
    }
}