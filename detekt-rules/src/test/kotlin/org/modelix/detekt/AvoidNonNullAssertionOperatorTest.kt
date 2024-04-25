package org.modelix.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AvoidNonNullAssertionOperatorTest {
    @Test
    fun `find double bang operator`() {
        val code = """
            fun main() {
                val x: String? = null
                val y = x!!
            }
        """

        val findings = AvoidNonNullAssertionOperator(Config.empty).compileAndLint(code)

        assertEquals(1, findings.size)
    }
}
