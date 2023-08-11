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
package org.modelix.modelql.core

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ModelQLTest {
    fun runTestWithTimeout(body: suspend TestScope.() -> Unit): TestResult {
        return runTest {
            withTimeout(3.seconds) {
                body()
            }
        }
    }

    @Test
    fun test_local() = runTestWithTimeout {
        val result: List<MyNonSerializableClass> = remoteProductDatabaseQuery { db ->
            db.products.map {
                val id = it.id
                val title = it.title
                val images = it.images.mapLocal { MyImage(it) }.toList()
                assertTrue(images.requiresSingularQueryInput())
                assertTrue(it.requiresSingularQueryInput())
                id.zip(title, images).mapLocal {
                    MyNonSerializableClass(it.first, it.second, it.third)
                }
            }.toList()
        }
        val expected = testDatabase.products.map {
            MyNonSerializableClass(it.id, it.title, it.images.map { MyImage(it) })
        }
        assertEquals(expected, result)
    }

    @Test
    fun testAutoSharedCrossQueryStep() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.map { product ->
                product.id.map {
                    product.title.zip(it)
                }
            }.toList()
        }
        assertEquals(
            testDatabase.products.map { it.title to it.id },
            result.map { it.first to it.second },
        )
    }

    @Test
    fun testLegalCrossQueryStep() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.map { product ->
                val sharedProduct: IMonoStep<Product> = product.shared()
                product.id.map {
                    sharedProduct.title.zip(it)
                }
            }.toList()
        }
        println(result)
        assertEquals(
            testDatabase.products.map { it.title to it.id },
            result.map { it.first to it.second },
        )
    }

    @Test
    fun testFilter() = runTestWithTimeout {
        val result: List<Int> = remoteProductDatabaseQuery { db ->
            db.products.filter { it.title.contains("9") }.map { it.id }.toList()
        }
        println(result)
        assertEquals(3, result.size)
    }

    @Test
    @Ignore
    fun testMapLocalToSet() = runTestWithTimeout {
        val result: Int = remoteProductDatabaseQuery { db ->
            db.products.map { it.title }.mapLocal { it.substring(0, 1) }.toSet().size()
        }
        println(result)
        assertEquals(testDatabase.products.map { it.title.substring(0, 1) }.toSet().size, result)
    }

    @Test
    fun testMapAccess() = runTestWithTimeout {
        val result: String = remoteProductDatabaseQuery { db ->
            db.products.associateBy { it.id }[5.asMono()].filterNotNull().title
        }
        println(result)
        assertEquals("Huawei P30", result)
    }

    @Test
    fun testMapAccessDeserializer() = runTestWithTimeout {
        val result: String? = remoteProductDatabaseQuery { db ->
            db.products.associate({ it.id }, { it.mapLocal { it.title } })[5.asMono()]
        }
        println(result)
        assertEquals("Huawei P30", result)
    }

    @Test
    fun testFirstOrNull() = runTestWithTimeout {
        val result: Product? = remoteProductDatabaseQuery { db ->
            db.products.filter { it.id.equalTo((-1).asMono()) }.firstOrNull()
        }
        assertEquals(null, result)
    }

    @Test
    fun testIfEmpty() = runTestWithTimeout {
        val result: String = remoteProductDatabaseQuery { db ->
            db.products.filter { it.id.equalTo(-1) }.map { it.title }.ifEmpty { "alternative".asMono() }.first()
        }
        println(result)
        assertEquals("alternative", result)
    }

    @Test
    fun test2() = runTestWithTimeout {
        val result: List<String> = remoteProductDatabaseQuery { db ->
            val products = db.products
            val products3 = products.filter { it.title.contains("3") }
            val products9 = products.filter { it.title.contains("9") }

            (products3 + products9).map { it.title }.toList()
        }
        println(result)
        assertEquals(6, result.size)
    }

    @Test
    fun remoteQuery() = runTestWithTimeout {
        val result: List<String> = remoteProductDatabaseQuery { db ->
            val products = db.products
            val products3 = products.filter { it.title.contains("3") }
            val products9 = products.filter { it.title.contains("9") }

            (products3 + products9).map { it.title }.toList()
        }
        println(result)
        assertEquals(6, result.size)
    }

    @Test
    fun countProducts() = runTestWithTimeout {
        val result: Int = remoteProductDatabaseQuery { db ->
            db.products.count()
        }
        assertEquals(30, result)
    }

    @Test
    fun count_products_containing_9() = runTestWithTimeout {
        val result: Int = remoteProductDatabaseQuery { db ->
            db.products.filter { it.title.contains("9") }.count()
        }
        assertEquals(3, result)
    }

    @Test
    fun list_in_filter_condition() = runTestWithTimeout {
        val result: Int = remoteProductDatabaseQuery { db ->
            db.products.filter { it.images.isNotEmpty() }.count()
        }
        assertEquals(30, result)
    }

    @Test
    fun repeatingZip() = runTestWithTimeout {
        val ids = remoteProductDatabaseQuery { db ->
            db.products.map { it.id }.toList()
        }
        assertEquals((1..30).toList(), ids)

        val result: List<List<Comparable<*>>> = remoteProductDatabaseQuery { db ->
            db.products.map { it.id }.allowEmpty().zip("abc".asMono()).toList()
        }.map { it.values }
        assertEquals((1..30).map { listOf(it, "abc") }, result)
    }

    @Test
    fun testMapLocal2_unusedInput() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.mapLocal2 {
                val title = "xxx".asMono().getLater()
                onSuccess {
                    "Title: " + title.get()
                }
            }.toList()
        }
        println(result)
        assertEquals((1..30).map { "Title: xxx" }, result)
    }

    @Test
    fun testMapLocal2() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.mapLocal2 {
                val title = it.title.getLater()
                onSuccess {
                    "Title: " + title.get()
                }
            }.toList()
        }
        println(result)
        assertEquals(testDatabase.products.map { "Title: " + it.title }, result)
    }

    @Test
    fun testZipOrder() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.flatMap { it.zip(it.images.assertNotEmpty()) }.mapLocal2 {
                val product = it.first.getLater()
                val image = it.second.getLater()
                onSuccess {
                    val localProduct: Product = product.get()
                    val localImage: String = image.get()
                    localProduct to localImage
                }
            }.toList()
        }
        assertEquals(132, result.size)
    }

    @Test
    fun testZipCount() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.flatMap { it.images }.map { it.zip() }.count()
        }
        assertEquals(testDatabase.products.flatMap { it.images }.count(), result)
    }

    @Test
    fun testZipFlowVsSequence() = runTestWithTimeout {
        val flowSize = (1..10).asFlow().flatMapConcat {
            combine(listOf(flowOf(it), (30..60).asFlow())) { it[0] to it[1] }
        }.fold(0) { acc, it -> acc + it.first * it.second }

        val sequenceSize = (1..10).asSequence().flatMap {
            CombiningSequence(arrayOf(sequenceOf(it), (30..60).asSequence()))
        }.fold(0) { acc, it -> acc + (it[0] as Int) * (it[1] as Int) }

        assertEquals(flowSize, sequenceSize)
    }

//    @Test
//    fun testIndexLookup() {
//        val result = remoteProductDatabaseQuery { db ->
//            db.products.filter { it.images.size() }.toSet()
//            db.products.flatMap { it.zip(it.images.assertNotEmpty()) }.mapLocal2 {
//                val product = it.first.getLater()
//                val image = it.second.getLater()
//                onSuccess {
//                    val localProduct: Product = product.get()
//                    val localImage: String = image.get()
//                    localProduct to localImage
//                }
//            }.toList()
//        }
//        assertEquals(132, result.size)
//    }

    data class MyNonSerializableClass(val id: Int, val title: String, val images: List<MyImage>)
    data class MyImage(val url: String)
}

suspend fun <ResultT> remoteProductDatabaseQuery(body: (IMonoStep<ProductDatabase>) -> IMonoStep<ResultT>): ResultT {
    return doRemoteProductDatabaseQuery(body)
}
suspend fun <ResultT> doRemoteProductDatabaseQuery(body: (IMonoStep<ProductDatabase>) -> IMonoStep<ResultT>): ResultT {
    val query: MonoUnboundQuery<ProductDatabase, ResultT> = buildMonoQuery { body(it) }.castToInstance()
    val json = Json {
        prettyPrint = true
        serializersModule = SerializersModule {
            include(UnboundQuery.serializersModule)
            polymorphic(StepDescriptor::class) {
                subclass(ProductsTraversal.Descriptor::class)
                subclass(ProductTitleTraversal.Descriptor::class)
                subclass(ProductCategoryTraversal.Descriptor::class)
                subclass(ProductIdTraversal.Descriptor::class)
                subclass(ProductImagesTraversal.Descriptor::class)
            }
        }
    }
    val serializedQuery = json.encodeToString(query.createDescriptor())
    println(serializedQuery)
    val deserializedQuery = json.decodeFromString<QueryGraphDescriptor>(serializedQuery).createRootQuery() as MonoUnboundQuery<ProductDatabase, ResultT>
    println("original query    : $query")
    println("deserialized query: $deserializedQuery")
    val remoteResult: IStepOutput<ResultT> = deserializedQuery.execute(QueryEvaluationContext.EMPTY, testDatabase)
    val serializedResult = json.encodeToString(deserializedQuery.getAggregationOutputSerializer(json.serializersModule), remoteResult)
//    println(serializedResult)
    return json.decodeFromString(query.getAggregationOutputSerializer(json.serializersModule), serializedResult).value
}

class ProductsTraversal() : FluxTransformingStep<ProductDatabase, Product>() {
    override fun createFlow(input: StepFlow<ProductDatabase>, context: IFlowInstantiationContext): StepFlow<Product> {
        return input.flatMapConcat { it.value.products.asFlow() }.asStepFlow(this)
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<Product> {
        return getProducer().createSequence(evaluationContext, queryInput).flatMap { it.products }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Product>> = serializersModule.serializer<Product>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()
    override fun toString(): String {
        return "${getProducer()}.products"
    }

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductsTraversal()
        }
    }
}

class ProductTitleTraversal : MonoTransformingStep<Product, String>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: Product): String {
        return input.title
    }

    override fun toString(): String {
        return getProducers().single().toString() + ".title"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String>> = serializersModule.serializer<String>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductTitleTraversal()
        }
    }
}
class ProductCategoryTraversal : MonoTransformingStep<Product, String>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: Product): String {
        return input.category
    }

    override fun toString(): String {
        return getProducers().single().toString() + ".category"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String>> = serializersModule.serializer<String>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductCategoryTraversal()
        }
    }
}
class ProductIdTraversal : MonoTransformingStep<Product, Int>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: Product): Int {
        return input.id
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> = serializersModule.serializer<Int>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()
    override fun toString(): String {
        return "${getProducer()}.id"
    }

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductIdTraversal()
        }
    }
}
class ProductImagesTraversal : FluxTransformingStep<Product, String>() {
    override fun createFlow(input: StepFlow<Product>, context: IFlowInstantiationContext): StepFlow<String> {
        return input.flatMapConcat { it.value.images.asFlow() }.asStepFlow(this)
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<String> {
        return getProducer().createSequence(evaluationContext, queryInput).flatMap { it.images }
    }

    override fun toString(): String {
        return "${getProducer()}.images"
    }
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String>> = serializersModule.serializer<String>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductImagesTraversal()
        }
    }
}

val IMonoStep<ProductDatabase>.products: IFluxStep<Product> get() = ProductsTraversal().also { connect(it) }
val IMonoStep<Product>.title: IMonoStep<String> get() = ProductTitleTraversal().also { connect(it) }
val IMonoStep<Product>.category: IMonoStep<String> get() = ProductCategoryTraversal().also { connect(it) }
val IMonoStep<Product>.id: IMonoStep<Int> get() = ProductIdTraversal().also { connect(it) }
val IMonoStep<Product>.images: IFluxStep<String> get() = ProductImagesTraversal().also { connect(it) }
