package org.modelix.datastructures

import kotlinx.serialization.Serializable
import org.modelix.datastructures.serialization.SplitJoinSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitJoinSerializerTest {

    private fun runTest(data: MyDataClass, expected: String, additionalChecks: (String) -> Unit = {}) {
        val serialized = SplitJoinSerializer.serialize(MyDataClass.serializer(), data)
        assertEquals(expected, serialized)
        val deserialized = SplitJoinSerializer.deserialize(MyDataClass.serializer(), serialized)
        assertEquals(data, deserialized)
        additionalChecks(serialized)
    }

    @Test
    fun `name only`() = runTest(
        MyDataClass(
            name = "nameA",
            someInt = 0xff,
            properties = mapOf(),
            children = emptyList(),
        ),
        "nameA/ff/%00///",
    )

    @Test
    fun `name and property`() = runTest(
        MyDataClass(
            name = "nameA",
            someInt = 0xff,
            properties = mapOf("propA" to "valueA"),
            children = emptyList(),
        ),
        "nameA/ff/%00/propA=valueA//",
    )

    @Test
    fun `two properties`() = runTest(
        MyDataClass(
            name = "nameA",
            someInt = 0xff,
            properties = mapOf("propA" to "valueA", "propB" to "valueB"),
            children = emptyList(),
        ),
        "nameA/ff/%00/propA=valueA,propB=valueB//",
    )

    @Test
    fun `nested maps`() = runTest(
        MyDataClass(
            name = "root",
            properties = mapOf("propA" to "valueA"),
            nestedMap = mapOf(
                "a" to MyDataClass(
                    name = "nested", properties = mapOf("propB" to "valueB"),
                ),
            ),
        ),
        "root/0/%00/propA=valueA//a=nested;0;%00;propB:valueB;;",
    )

    @Test
    fun `second level nested maps`() = runTest(
        MyDataClass(
            name = "root",
            properties = mapOf("propA" to "valueA"),
            nestedMap = mapOf(
                "a" to MyDataClass(
                    name = "nested",
                    properties = mapOf("propB" to "valueB"),
                    nestedMap = mapOf(
                        "a" to MyDataClass(
                            name = "deeply nested",
                            properties = mapOf("propC" to "valueC"),
                        ),
                    ),
                ),
            ),
        ),
        "root/0/%00/propA=valueA//a=nested;0;%00;propB:valueB;;a:deeply+nested|0|%00|propC\\valueC||",
    )

    @Test
    fun `maximum nesting`() = runTest(
        @Suppress("ktlint:standard:trailing-comma-on-call-site", "ktlint:standard:wrapping")
        MyDataClass(
            nestedMap = mapOf("a1" to null, "a2" to MyDataClass(
                nestedMap = mapOf("b" to MyDataClass(
                    nestedMap = mapOf("c" to MyDataClass(
                        nestedMap = mapOf("d" to MyDataClass(
                            nestedMap = mapOf("e" to MyDataClass(
                                nestedMap = mapOf("d" to MyDataClass(
                                    nestedMap = mapOf("e" to MyDataClass(
                                        nestedMap = mapOf("e" to MyDataClass(
                                            nestedMap = mapOf("d" to MyDataClass(
                                                nestedMap = mapOf("e" to MyDataClass(
                                                    nestedMap = mapOf("e" to MyDataClass(
                                                        nestedMap = mapOf("d" to MyDataClass(
                                                            properties = mapOf("A" to "B")
                                                        ))
                                                    ))
                                                ))
                                            ))
                                        ))
                                    ))
                                ))
                            ))
                        ))
                    ))
                ))
            ))
        ),
        """%00/0/%00///a1=%00,a2=%00;0;%00;;;b:%00|0|%00|||c\%00!0!%00!!!d#%00~0~%00~~~e^%00@0@%00@@@d&%00?0?%00???e(%00)0)%00)))e<%00>0>%00>>>d[%00]0]%00]]]e{%00}0}%00}}}e'%00`0`%00```d"%00${'$'}0${'$'}%00${'$'}A B${'$'}${'$'}""",
    ) { serialized ->
        for (separator in SplitJoinSerializer.SEPARATORS) {
            assertTrue(serialized.contains(separator), "Separator $separator not tested: $serialized")
        }
    }
}

@Serializable
private data class MyDataClass(
    val name: String? = null,
    val someInt: Int = 0,
    val type: Pair<String, Pair<String, Pair<String, String>>>? = null,
    val properties: Map<String?, String?> = emptyMap(),
    val children: List<MyDataClass> = emptyList(),
    val nestedMap: Map<String, MyDataClass?> = emptyMap(),
)
