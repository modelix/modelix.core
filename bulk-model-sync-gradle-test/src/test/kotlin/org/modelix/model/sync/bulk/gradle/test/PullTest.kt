package org.modelix.model.sync.bulk.gradle.test

import org.junit.jupiter.api.Test
import java.io.File

class PullTest {

    @Test
    fun `nodes were synced to local`() {
        val localModel = File("build/test-repo/solutions/GraphSolution/models/GraphSolution.example.mps").readText()

        val expectedNodesRegex = """
            |\s*<node concept="1DmExO" id="pSCM1J8FfW" role="1DmyQT">
            |\s*<property role="TrG5h" value="X" />
            |\s*</node>
            |\s*<node concept="1DmExO" id="pSCM1J8FfX" role="1DmyQT">
            |\s*<property role="TrG5h" value="Y" />
            |\s*</node>
            |\s*<node concept="1DmExO" id="pSCM1J8FfY" role="1DmyQT">
            |\s*<property role="TrG5h" value="Z" />
            |\s*</node>
            |\s*<node concept="1DmExO" id="pSCM1J8FfZ" role="1DmyQT">
            |\s*<property role="TrG5h" value="D" />
            |\s*</node>
            |\s*<node concept="1DmExO" id="pSCM1J8Fg0" role="1DmyQT">
            |\s*<property role="TrG5h" value="E" />
            |\s*</node>
        """.trimMargin().toRegex()

        assert(expectedNodesRegex.containsMatchIn(localModel))
    }
}
