package org.modelix.model.sync.gradle.test

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class PullTest {

    @Test
    fun `nodes were synced to local`() {
        val localModelFile = File("build/test-repo/solutions/GraphSolution/models/GraphSolution.example.mps")
        val expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <model ref="r:64fb7a52-94bd-43f1-a6e6-395dcf17eaae(GraphSolution.example)">
              <persistence version="9" />
              <languages>
                <use id="d5dabe27-fe41-4a5d-92bb-aede71707287" name="GraphLang" version="0" />
              </languages>
              <imports />
              <registry>
                <language id="d5dabe27-fe41-4a5d-92bb-aede71707287" name="GraphLang">
                  <concept id="466301921131629394" name="GraphLang.structure.Graph" flags="ng" index="1DmExM">
                    <child id="466301921131663001" name="nodes" index="1DmyQT" />
                    <child id="466301921131630381" name="edges" index="1DmEKd" />
                  </concept>
                  <concept id="466301921131629396" name="GraphLang.structure.Node" flags="ng" index="1DmExO" />
                  <concept id="466301921131629399" name="GraphLang.structure.Edge" flags="ng" index="1DmExR">
                    <reference id="466301921131663029" name="source" index="1DmyQl" />
                    <reference id="466301921131663030" name="target" index="1DmyQm" />
                  </concept>
                </language>
                <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
                  <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ng" index="TrEIO">
                    <property id="1169194664001" name="name" index="TrG5h" />
                  </concept>
                </language>
              </registry>
              <node concept="1DmExM" id="pSCM1J8y9y">
                <node concept="1DmExR" id="pSCM1J8Fg1" role="1DmEKd">
                  <ref role="1DmyQl" node="pSCM1J8FfW" resolve="X" />
                  <ref role="1DmyQm" node="pSCM1J8FfX" resolve="Y" />
                </node>
                <node concept="1DmExR" id="pSCM1J8Fg2" role="1DmEKd">
                  <ref role="1DmyQl" node="pSCM1J8FfX" resolve="Y" />
                  <ref role="1DmyQm" node="pSCM1J8FfY" resolve="Y" />
                </node>
                <node concept="1DmExR" id="pSCM1J8Fg3" role="1DmEKd">
                  <ref role="1DmyQl" node="pSCM1J8FfZ" resolve="D" />
                  <ref role="1DmyQm" node="pSCM1J8FfY" resolve="Y" />
                </node>
                <node concept="1DmExR" id="pSCM1J8Fg4" role="1DmEKd">
                  <ref role="1DmyQl" node="pSCM1J8Fg0" resolve="E" />
                  <ref role="1DmyQm" node="pSCM1J8FfZ" resolve="D" />
                </node>
                <node concept="1DmExO" id="pSCM1J8FfW" role="1DmyQT">
                  <property role="TrG5h" value="X" />
                </node>
                <node concept="1DmExO" id="pSCM1J8FfX" role="1DmyQT">
                  <property role="TrG5h" value="Y" />
                </node>
                <node concept="1DmExO" id="pSCM1J8FfY" role="1DmyQT">
                  <property role="TrG5h" value="Z" />
                </node>
                <node concept="1DmExO" id="pSCM1J8FfZ" role="1DmyQT">
                  <property role="TrG5h" value="D" />
                </node>
                <node concept="1DmExO" id="pSCM1J8Fg0" role="1DmyQT">
                  <property role="TrG5h" value="E" />
                </node>
              </node>
            </model>
        """.trimIndent()

        assertEquals(expected, localModelFile.readText())
    }
}
