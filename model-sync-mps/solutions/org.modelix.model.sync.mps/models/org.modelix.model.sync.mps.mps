<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:8dd3b0c9-6287-4dab-beae-32c415fb38ec(org.modelix.model.sync.mps)">
  <persistence version="9" />
  <languages>
    <use id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage" version="11" />
    <use id="83888646-71ce-4f1c-9c53-c54016f6ad4f" name="jetbrains.mps.baseLanguage.collections" version="1" />
    <use id="fd392034-7849-419d-9071-12563d152375" name="jetbrains.mps.baseLanguage.closures" version="0" />
  </languages>
  <imports>
    <import index="2iw0" ref="ac6b4971-2a89-49fb-9c30-c2f0e85de741/java:org.modelix.model.sync(org.modelix.model.sync.mps/)" />
    <import index="zxfz" ref="ac6b4971-2a89-49fb-9c30-c2f0e85de741/java:org.modelix.model.mpsadapters(org.modelix.model.sync.mps/)" />
    <import index="alof" ref="742f6602-5a2f-4313-aa6e-ae1cd4ffdc61/java:jetbrains.mps.ide.project(MPS.Platform/)" />
    <import index="4nm9" ref="498d89d2-c2e9-11e2-ad49-6cf049e62fe5/java:com.intellij.openapi.project(MPS.IDEA/)" />
    <import index="guwi" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.io(JDK/)" />
    <import index="lui2" ref="8865b7a8-5271-43d3-884c-6fd1d9cfdd34/java:org.jetbrains.mps.openapi.module(MPS.OpenAPI/)" />
    <import index="lui3" ref="ac6b4971-2a89-49fb-9c30-c2f0e85de741/java:org.jetbrains.mps.openapi.module(org.modelix.model.sync.mps/)" />
    <import index="wyt6" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.lang(JDK/)" implicit="true" />
  </imports>
  <registry>
    <language id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage">
      <concept id="1215693861676" name="jetbrains.mps.baseLanguage.structure.BaseAssignmentExpression" flags="nn" index="d038R">
        <child id="1068498886297" name="rValue" index="37vLTx" />
        <child id="1068498886295" name="lValue" index="37vLTJ" />
      </concept>
      <concept id="1202948039474" name="jetbrains.mps.baseLanguage.structure.InstanceMethodCallOperation" flags="nn" index="liA8E" />
      <concept id="8118189177080264853" name="jetbrains.mps.baseLanguage.structure.AlternativeType" flags="ig" index="nSUau">
        <child id="8118189177080264854" name="alternative" index="nSUat" />
      </concept>
      <concept id="1465982738277781862" name="jetbrains.mps.baseLanguage.structure.PlaceholderMember" flags="nn" index="2tJIrI" />
      <concept id="1154032098014" name="jetbrains.mps.baseLanguage.structure.AbstractLoopStatement" flags="nn" index="2LF5Ji">
        <child id="1154032183016" name="body" index="2LFqv$" />
      </concept>
      <concept id="1197027756228" name="jetbrains.mps.baseLanguage.structure.DotExpression" flags="nn" index="2OqwBi">
        <child id="1197027771414" name="operand" index="2Oq$k0" />
        <child id="1197027833540" name="operation" index="2OqNvi" />
      </concept>
      <concept id="1145552977093" name="jetbrains.mps.baseLanguage.structure.GenericNewExpression" flags="nn" index="2ShNRf">
        <child id="1145553007750" name="creator" index="2ShVmc" />
      </concept>
      <concept id="1070475926800" name="jetbrains.mps.baseLanguage.structure.StringLiteral" flags="nn" index="Xl_RD">
        <property id="1070475926801" name="value" index="Xl_RC" />
      </concept>
      <concept id="4952749571008284462" name="jetbrains.mps.baseLanguage.structure.CatchVariable" flags="ng" index="XOnhg" />
      <concept id="1081236700938" name="jetbrains.mps.baseLanguage.structure.StaticMethodDeclaration" flags="ig" index="2YIFZL" />
      <concept id="1081236700937" name="jetbrains.mps.baseLanguage.structure.StaticMethodCall" flags="nn" index="2YIFZM">
        <reference id="1144433194310" name="classConcept" index="1Pybhc" />
      </concept>
      <concept id="1164991038168" name="jetbrains.mps.baseLanguage.structure.ThrowStatement" flags="nn" index="YS8fn">
        <child id="1164991057263" name="throwable" index="YScLw" />
      </concept>
      <concept id="1070533707846" name="jetbrains.mps.baseLanguage.structure.StaticFieldReference" flags="nn" index="10M0yZ">
        <reference id="1144433057691" name="classifier" index="1PxDUh" />
      </concept>
      <concept id="1070534058343" name="jetbrains.mps.baseLanguage.structure.NullLiteral" flags="nn" index="10Nm6u" />
      <concept id="1068390468198" name="jetbrains.mps.baseLanguage.structure.ClassConcept" flags="ig" index="312cEu" />
      <concept id="1068431474542" name="jetbrains.mps.baseLanguage.structure.VariableDeclaration" flags="ng" index="33uBYm">
        <child id="1068431790190" name="initializer" index="33vP2m" />
      </concept>
      <concept id="1068498886296" name="jetbrains.mps.baseLanguage.structure.VariableReference" flags="nn" index="37vLTw">
        <reference id="1068581517664" name="variableDeclaration" index="3cqZAo" />
      </concept>
      <concept id="1068498886292" name="jetbrains.mps.baseLanguage.structure.ParameterDeclaration" flags="ir" index="37vLTG" />
      <concept id="1068498886294" name="jetbrains.mps.baseLanguage.structure.AssignmentExpression" flags="nn" index="37vLTI" />
      <concept id="4972933694980447171" name="jetbrains.mps.baseLanguage.structure.BaseVariableDeclaration" flags="ng" index="19Szcq">
        <child id="5680397130376446158" name="type" index="1tU5fm" />
      </concept>
      <concept id="1068580123132" name="jetbrains.mps.baseLanguage.structure.BaseMethodDeclaration" flags="ng" index="3clF44">
        <child id="1164879685961" name="throwsItem" index="Sfmx6" />
        <child id="1068580123133" name="returnType" index="3clF45" />
        <child id="1068580123135" name="body" index="3clF47" />
      </concept>
      <concept id="1068580123155" name="jetbrains.mps.baseLanguage.structure.ExpressionStatement" flags="nn" index="3clFbF">
        <child id="1068580123156" name="expression" index="3clFbG" />
      </concept>
      <concept id="1068580123157" name="jetbrains.mps.baseLanguage.structure.Statement" flags="nn" index="3clFbH" />
      <concept id="1068580123159" name="jetbrains.mps.baseLanguage.structure.IfStatement" flags="nn" index="3clFbJ">
        <child id="1068580123160" name="condition" index="3clFbw" />
        <child id="1068580123161" name="ifTrue" index="3clFbx" />
      </concept>
      <concept id="1068580123136" name="jetbrains.mps.baseLanguage.structure.StatementList" flags="sn" stub="5293379017992965193" index="3clFbS">
        <child id="1068581517665" name="statement" index="3cqZAp" />
      </concept>
      <concept id="1068581242875" name="jetbrains.mps.baseLanguage.structure.PlusExpression" flags="nn" index="3cpWs3" />
      <concept id="1068581242878" name="jetbrains.mps.baseLanguage.structure.ReturnStatement" flags="nn" index="3cpWs6">
        <child id="1068581517676" name="expression" index="3cqZAk" />
      </concept>
      <concept id="1068581242864" name="jetbrains.mps.baseLanguage.structure.LocalVariableDeclarationStatement" flags="nn" index="3cpWs8">
        <child id="1068581242865" name="localVariableDeclaration" index="3cpWs9" />
      </concept>
      <concept id="1068581242863" name="jetbrains.mps.baseLanguage.structure.LocalVariableDeclaration" flags="nr" index="3cpWsn" />
      <concept id="1068581517677" name="jetbrains.mps.baseLanguage.structure.VoidType" flags="in" index="3cqZAl" />
      <concept id="1204053956946" name="jetbrains.mps.baseLanguage.structure.IMethodCall" flags="ng" index="1ndlxa">
        <reference id="1068499141037" name="baseMethodDeclaration" index="37wK5l" />
        <child id="1068499141038" name="actualArgument" index="37wK5m" />
      </concept>
      <concept id="1212685548494" name="jetbrains.mps.baseLanguage.structure.ClassCreator" flags="nn" index="1pGfFk" />
      <concept id="1107461130800" name="jetbrains.mps.baseLanguage.structure.Classifier" flags="ng" index="3pOWGL">
        <child id="5375687026011219971" name="member" index="jymVt" unordered="true" />
      </concept>
      <concept id="7812454656619025412" name="jetbrains.mps.baseLanguage.structure.LocalMethodCall" flags="nn" index="1rXfSq" />
      <concept id="1107535904670" name="jetbrains.mps.baseLanguage.structure.ClassifierType" flags="in" index="3uibUv">
        <reference id="1107535924139" name="classifier" index="3uigEE" />
      </concept>
      <concept id="1081773326031" name="jetbrains.mps.baseLanguage.structure.BinaryOperation" flags="nn" index="3uHJSO">
        <child id="1081773367579" name="rightExpression" index="3uHU7w" />
        <child id="1081773367580" name="leftExpression" index="3uHU7B" />
      </concept>
      <concept id="3093926081414150598" name="jetbrains.mps.baseLanguage.structure.MultipleCatchClause" flags="ng" index="3uVAMA">
        <child id="8276990574895933173" name="catchBody" index="1zc67A" />
        <child id="8276990574895933172" name="throwable" index="1zc67B" />
      </concept>
      <concept id="1178549954367" name="jetbrains.mps.baseLanguage.structure.IVisible" flags="ng" index="1B3ioH">
        <child id="1178549979242" name="visibility" index="1B3o_S" />
      </concept>
      <concept id="5351203823916750322" name="jetbrains.mps.baseLanguage.structure.TryUniversalStatement" flags="nn" index="3J1_TO">
        <child id="8276990574886367510" name="catchClause" index="1zxBo5" />
        <child id="8276990574886367508" name="body" index="1zxBo7" />
      </concept>
      <concept id="1146644602865" name="jetbrains.mps.baseLanguage.structure.PublicVisibility" flags="nn" index="3Tm1VV" />
      <concept id="1146644623116" name="jetbrains.mps.baseLanguage.structure.PrivateVisibility" flags="nn" index="3Tm6S6" />
      <concept id="1080120340718" name="jetbrains.mps.baseLanguage.structure.AndExpression" flags="nn" index="1Wc70l" />
    </language>
    <language id="fd392034-7849-419d-9071-12563d152375" name="jetbrains.mps.baseLanguage.closures">
      <concept id="1199569711397" name="jetbrains.mps.baseLanguage.closures.structure.ClosureLiteral" flags="nn" index="1bVj0M">
        <child id="1199569906740" name="parameter" index="1bW2Oz" />
        <child id="1199569916463" name="body" index="1bW5cS" />
      </concept>
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ng" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
    <language id="83888646-71ce-4f1c-9c53-c54016f6ad4f" name="jetbrains.mps.baseLanguage.collections">
      <concept id="540871147943773365" name="jetbrains.mps.baseLanguage.collections.structure.SingleArgumentSequenceOperation" flags="nn" index="25WWJ4">
        <child id="540871147943773366" name="argument" index="25WWJ7" />
      </concept>
      <concept id="1226511727824" name="jetbrains.mps.baseLanguage.collections.structure.SetType" flags="in" index="2hMVRd">
        <child id="1226511765987" name="elementType" index="2hN53Y" />
      </concept>
      <concept id="1226516258405" name="jetbrains.mps.baseLanguage.collections.structure.HashSetCreator" flags="nn" index="2i4dXS" />
      <concept id="1153943597977" name="jetbrains.mps.baseLanguage.collections.structure.ForEachStatement" flags="nn" index="2Gpval">
        <child id="1153944400369" name="variable" index="2Gsz3X" />
        <child id="1153944424730" name="inputSequence" index="2GsD0m" />
      </concept>
      <concept id="1153944193378" name="jetbrains.mps.baseLanguage.collections.structure.ForEachVariable" flags="nr" index="2GrKxI" />
      <concept id="1153944233411" name="jetbrains.mps.baseLanguage.collections.structure.ForEachVariableReference" flags="nn" index="2GrUjf">
        <reference id="1153944258490" name="variable" index="2Gs0qQ" />
      </concept>
      <concept id="1237721394592" name="jetbrains.mps.baseLanguage.collections.structure.AbstractContainerCreator" flags="nn" index="HWqM0">
        <child id="1237721435807" name="elementType" index="HW$YZ" />
        <child id="1237731803878" name="copyFrom" index="I$8f6" />
      </concept>
      <concept id="1172254888721" name="jetbrains.mps.baseLanguage.collections.structure.ContainsOperation" flags="nn" index="3JPx81" />
    </language>
  </registry>
  <node concept="312cEu" id="5s5xg7sH9bO">
    <property role="TrG5h" value="MPSRepositorySynchronizer" />
    <node concept="2YIFZL" id="5s5xg7sH9dV" role="jymVt">
      <property role="TrG5h" value="exportRepository" />
      <node concept="3clFbS" id="5s5xg7sH9d_" role="3clF47">
        <node concept="3cpWs8" id="7gc0jqT0lrx" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT0lry" role="3cpWs9">
            <property role="TrG5h" value="repoAsNode" />
            <node concept="3uibUv" id="7gc0jqT0lrz" role="1tU5fm">
              <ref role="3uigEE" to="zxfz:~MPSRepositoryAsNode" resolve="MPSRepositoryAsNode" />
            </node>
            <node concept="1rXfSq" id="7gc0jqT1_WJ" role="33vP2m">
              <ref role="37wK5l" node="7gc0jqT1yfO" resolve="getRepositoryAsNode" />
            </node>
          </node>
        </node>
        <node concept="3clFbH" id="7gc0jqT4mbW" role="3cqZAp" />
        <node concept="3cpWs8" id="7gc0jqT4mj_" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT4mjC" role="3cpWs9">
            <property role="TrG5h" value="includedModules" />
            <node concept="2ShNRf" id="7gc0jqT4yPh" role="33vP2m">
              <node concept="2i4dXS" id="7gc0jqT4_HB" role="2ShVmc">
                <node concept="3uibUv" id="7gc0jqT4_HD" role="HW$YZ">
                  <ref role="3uigEE" to="wyt6:~String" resolve="String" />
                </node>
                <node concept="2OqwBi" id="7gc0jqT4Hsf" role="I$8f6">
                  <node concept="2YIFZM" id="7gc0jqT4GSp" role="2Oq$k0">
                    <ref role="37wK5l" to="wyt6:~System.getProperty(java.lang.String)" resolve="getProperty" />
                    <ref role="1Pybhc" to="wyt6:~System" resolve="System" />
                    <node concept="Xl_RD" id="7gc0jqT4H9m" role="37wK5m">
                      <property role="Xl_RC" value="modelix.model.sync.output.modules" />
                    </node>
                  </node>
                  <node concept="liA8E" id="7gc0jqT4Iha" role="2OqNvi">
                    <ref role="37wK5l" to="wyt6:~String.split(java.lang.String)" resolve="split" />
                    <node concept="Xl_RD" id="7gc0jqT4InL" role="37wK5m">
                      <property role="Xl_RC" value="," />
                    </node>
                  </node>
                </node>
              </node>
            </node>
            <node concept="2hMVRd" id="7gc0jqT4uB6" role="1tU5fm">
              <node concept="3uibUv" id="7gc0jqT4uTS" role="2hN53Y">
                <ref role="3uigEE" to="wyt6:~String" resolve="String" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbH" id="7gc0jqT1Aky" role="3cqZAp" />
        <node concept="3clFbF" id="7gc0jqT4Z6Q" role="3cqZAp">
          <node concept="2OqwBi" id="7gc0jqT51iJ" role="3clFbG">
            <node concept="2OqwBi" id="7gc0jqT50CT" role="2Oq$k0">
              <node concept="2OqwBi" id="7gc0jqT4ZxN" role="2Oq$k0">
                <node concept="37vLTw" id="7gc0jqT4Z6O" role="2Oq$k0">
                  <ref role="3cqZAo" node="7gc0jqT0lry" resolve="repoAsNode" />
                </node>
                <node concept="liA8E" id="7gc0jqT50ld" role="2OqNvi">
                  <ref role="37wK5l" to="zxfz:~MPSRepositoryAsNode.getRepository()" resolve="getRepository" />
                </node>
              </node>
              <node concept="liA8E" id="7gc0jqT50Zl" role="2OqNvi">
                <ref role="37wK5l" to="lui3:~SRepository.getModelAccess()" resolve="getModelAccess" />
              </node>
            </node>
            <node concept="liA8E" id="7gc0jqT51ES" role="2OqNvi">
              <ref role="37wK5l" to="lui3:~ModelAccess.runReadAction(java.lang.Runnable)" resolve="runReadAction" />
              <node concept="1bVj0M" id="7gc0jqT52FG" role="37wK5m">
                <node concept="3clFbS" id="7gc0jqT52FH" role="1bW5cS">
                  <node concept="2Gpval" id="7gc0jqT4L4n" role="3cqZAp">
                    <node concept="2GrKxI" id="7gc0jqT4L4p" role="2Gsz3X">
                      <property role="TrG5h" value="module" />
                    </node>
                    <node concept="3clFbS" id="7gc0jqT4L4t" role="2LFqv$">
                      <node concept="3clFbJ" id="7gc0jqT4LBA" role="3cqZAp">
                        <node concept="3clFbS" id="7gc0jqT4LBC" role="3clFbx">
                          <node concept="3cpWs8" id="7gc0jqT0qOh" role="3cqZAp">
                            <node concept="3cpWsn" id="7gc0jqT0qOi" role="3cpWs9">
                              <property role="TrG5h" value="exporter" />
                              <node concept="3uibUv" id="7gc0jqT0qOj" role="1tU5fm">
                                <ref role="3uigEE" to="2iw0:~ModelExporter" resolve="ModelExporter" />
                              </node>
                              <node concept="2ShNRf" id="7gc0jqT0qU_" role="33vP2m">
                                <node concept="1pGfFk" id="7gc0jqT0qUs" role="2ShVmc">
                                  <ref role="37wK5l" to="2iw0:~ModelExporter.&lt;init&gt;(org.modelix.model.api.INode)" resolve="ModelExporter" />
                                  <node concept="2ShNRf" id="7gc0jqT4R8M" role="37wK5m">
                                    <node concept="1pGfFk" id="7gc0jqT4R$7" role="2ShVmc">
                                      <ref role="37wK5l" to="zxfz:~MPSModuleAsNode.&lt;init&gt;(org.jetbrains.mps.openapi.module.SModule)" resolve="MPSModuleAsNode" />
                                      <node concept="2GrUjf" id="7gc0jqT4RL8" role="37wK5m">
                                        <ref role="2Gs0qQ" node="7gc0jqT4L4p" resolve="module" />
                                      </node>
                                    </node>
                                  </node>
                                </node>
                              </node>
                            </node>
                          </node>
                          <node concept="3cpWs8" id="7gc0jqT0r$G" role="3cqZAp">
                            <node concept="3cpWsn" id="7gc0jqT0r$H" role="3cpWs9">
                              <property role="TrG5h" value="outputPath" />
                              <node concept="3uibUv" id="7gc0jqT0r$I" role="1tU5fm">
                                <ref role="3uigEE" to="wyt6:~String" resolve="String" />
                              </node>
                              <node concept="2YIFZM" id="7gc0jqT0rV3" role="33vP2m">
                                <ref role="1Pybhc" to="wyt6:~System" resolve="System" />
                                <ref role="37wK5l" to="wyt6:~System.getProperty(java.lang.String)" resolve="getProperty" />
                                <node concept="Xl_RD" id="7gc0jqT0rXJ" role="37wK5m">
                                  <property role="Xl_RC" value="modelix.model.sync.output.path" />
                                </node>
                              </node>
                            </node>
                          </node>
                          <node concept="3cpWs8" id="7gc0jqT0t13" role="3cqZAp">
                            <node concept="3cpWsn" id="7gc0jqT0t14" role="3cpWs9">
                              <property role="TrG5h" value="outputFile" />
                              <node concept="3uibUv" id="7gc0jqT0t15" role="1tU5fm">
                                <ref role="3uigEE" to="guwi:~File" resolve="File" />
                              </node>
                              <node concept="2ShNRf" id="7gc0jqT0tfq" role="33vP2m">
                                <node concept="1pGfFk" id="7gc0jqT0txy" role="2ShVmc">
                                  <ref role="37wK5l" to="guwi:~File.&lt;init&gt;(java.lang.String)" resolve="File" />
                                  <node concept="3cpWs3" id="7gc0jqT4WdB" role="37wK5m">
                                    <node concept="Xl_RD" id="7gc0jqT4WuS" role="3uHU7w">
                                      <property role="Xl_RC" value=".json" />
                                    </node>
                                    <node concept="3cpWs3" id="7gc0jqT5f_w" role="3uHU7B">
                                      <node concept="3cpWs3" id="7gc0jqT5guJ" role="3uHU7B">
                                        <node concept="10M0yZ" id="7gc0jqT5h44" role="3uHU7w">
                                          <ref role="3cqZAo" to="guwi:~File.separator" resolve="separator" />
                                          <ref role="1PxDUh" to="guwi:~File" resolve="File" />
                                        </node>
                                        <node concept="37vLTw" id="7gc0jqT5fQq" role="3uHU7B">
                                          <ref role="3cqZAo" node="7gc0jqT0r$H" resolve="outputPath" />
                                        </node>
                                      </node>
                                      <node concept="2OqwBi" id="7gc0jqT4V48" role="3uHU7w">
                                        <node concept="2GrUjf" id="7gc0jqT4UyV" role="2Oq$k0">
                                          <ref role="2Gs0qQ" node="7gc0jqT4L4p" resolve="module" />
                                        </node>
                                        <node concept="liA8E" id="7gc0jqT4VGG" role="2OqNvi">
                                          <ref role="37wK5l" to="lui3:~SModule.getModuleName()" resolve="getModuleName" />
                                        </node>
                                      </node>
                                    </node>
                                  </node>
                                </node>
                              </node>
                            </node>
                          </node>
                          <node concept="3clFbF" id="7gc0jqT53_d" role="3cqZAp">
                            <node concept="2OqwBi" id="7gc0jqT547j" role="3clFbG">
                              <node concept="37vLTw" id="7gc0jqT53_b" role="2Oq$k0">
                                <ref role="3cqZAo" node="7gc0jqT0qOi" resolve="exporter" />
                              </node>
                              <node concept="liA8E" id="7gc0jqT54ro" role="2OqNvi">
                                <ref role="37wK5l" to="2iw0:~ModelExporter.export(java.io.File)" resolve="export" />
                                <node concept="37vLTw" id="7gc0jqT54DL" role="37wK5m">
                                  <ref role="3cqZAo" node="7gc0jqT0t14" resolve="outputFile" />
                                </node>
                              </node>
                            </node>
                          </node>
                        </node>
                        <node concept="2OqwBi" id="7gc0jqT4NFB" role="3clFbw">
                          <node concept="37vLTw" id="7gc0jqT4MYk" role="2Oq$k0">
                            <ref role="3cqZAo" node="7gc0jqT4mjC" resolve="includedModules" />
                          </node>
                          <node concept="3JPx81" id="7gc0jqT4Ou1" role="2OqNvi">
                            <node concept="2OqwBi" id="7gc0jqT4ORx" role="25WWJ7">
                              <node concept="2GrUjf" id="7gc0jqT4OCK" role="2Oq$k0">
                                <ref role="2Gs0qQ" node="7gc0jqT4L4p" resolve="module" />
                              </node>
                              <node concept="liA8E" id="7gc0jqT4Pxy" role="2OqNvi">
                                <ref role="37wK5l" to="lui3:~SModule.getModuleName()" resolve="getModuleName" />
                              </node>
                            </node>
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="2OqwBi" id="2rlXDkQHjx1" role="2GsD0m">
                      <node concept="2OqwBi" id="2rlXDkQHiXs" role="2Oq$k0">
                        <node concept="37vLTw" id="2rlXDkQHiDg" role="2Oq$k0">
                          <ref role="3cqZAo" node="7gc0jqT0lry" resolve="repoAsNode" />
                        </node>
                        <node concept="liA8E" id="2rlXDkQHjbj" role="2OqNvi">
                          <ref role="37wK5l" to="zxfz:~MPSRepositoryAsNode.getRepository()" resolve="getRepository" />
                        </node>
                      </node>
                      <node concept="liA8E" id="2rlXDkQHjK2" role="2OqNvi">
                        <ref role="37wK5l" to="lui3:~SRepository.getModules()" resolve="getModules" />
                      </node>
                    </node>
                  </node>
                </node>
              </node>
            </node>
          </node>
        </node>
      </node>
      <node concept="3cqZAl" id="5s5xg7sH9dz" role="3clF45" />
      <node concept="3Tm1VV" id="7gc0jqSYWIa" role="1B3o_S" />
      <node concept="3uibUv" id="7gc0jqT1st7" role="Sfmx6">
        <ref role="3uigEE" to="wyt6:~Exception" resolve="Exception" />
      </node>
    </node>
    <node concept="2tJIrI" id="7gc0jqT1xCO" role="jymVt" />
    <node concept="2YIFZL" id="7gc0jqT1xF_" role="jymVt">
      <property role="TrG5h" value="importRepository" />
      <node concept="3clFbS" id="7gc0jqT1xFA" role="3clF47">
        <node concept="3cpWs8" id="7gc0jqT1ApB" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT1ApC" role="3cpWs9">
            <property role="TrG5h" value="repoAsNode" />
            <node concept="3uibUv" id="7gc0jqT1ApD" role="1tU5fm">
              <ref role="3uigEE" to="zxfz:~MPSRepositoryAsNode" resolve="MPSRepositoryAsNode" />
            </node>
            <node concept="1rXfSq" id="7gc0jqT1ADn" role="33vP2m">
              <ref role="37wK5l" node="7gc0jqT1yfO" resolve="getRepositoryAsNode" />
            </node>
          </node>
        </node>
        <node concept="3clFbH" id="7gc0jqT1Ain" role="3cqZAp" />
        <node concept="3cpWs8" id="7gc0jqT1xFT" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT1xFU" role="3cpWs9">
            <property role="TrG5h" value="importer" />
            <node concept="3uibUv" id="7gc0jqT1xFV" role="1tU5fm">
              <ref role="3uigEE" to="2iw0:~ModelImporter" resolve="ModelImporter" />
            </node>
            <node concept="2ShNRf" id="7gc0jqT1xFW" role="33vP2m">
              <node concept="1pGfFk" id="7gc0jqT1xFX" role="2ShVmc">
                <ref role="37wK5l" to="2iw0:~ModelImporter.&lt;init&gt;(org.modelix.model.api.INode)" resolve="ModelImporter" />
                <node concept="37vLTw" id="7gc0jqT1xFY" role="37wK5m">
                  <ref role="3cqZAo" node="7gc0jqT1ApC" resolve="repoAsNode" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="7gc0jqT1xFZ" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT1xG0" role="3cpWs9">
            <property role="TrG5h" value="inputPath" />
            <node concept="3uibUv" id="7gc0jqT1xG1" role="1tU5fm">
              <ref role="3uigEE" to="wyt6:~String" resolve="String" />
            </node>
            <node concept="2YIFZM" id="7gc0jqT1xG2" role="33vP2m">
              <ref role="1Pybhc" to="wyt6:~System" resolve="System" />
              <ref role="37wK5l" to="wyt6:~System.getProperty(java.lang.String)" resolve="getProperty" />
              <node concept="Xl_RD" id="7gc0jqT1xG3" role="37wK5m">
                <property role="Xl_RC" value="modelix.model.sync.input.path" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="7gc0jqT1xG4" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT1xG5" role="3cpWs9">
            <property role="TrG5h" value="inputDir" />
            <node concept="3uibUv" id="7gc0jqT1xG6" role="1tU5fm">
              <ref role="3uigEE" to="guwi:~File" resolve="File" />
            </node>
            <node concept="2ShNRf" id="7gc0jqT1xG7" role="33vP2m">
              <node concept="1pGfFk" id="7gc0jqT1xG8" role="2ShVmc">
                <ref role="37wK5l" to="guwi:~File.&lt;init&gt;(java.lang.String)" resolve="File" />
                <node concept="37vLTw" id="7gc0jqT1xG9" role="37wK5m">
                  <ref role="3cqZAo" node="7gc0jqT1xG0" resolve="inputPath" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="7gc0jqT4cDu" role="3cqZAp">
          <node concept="2OqwBi" id="7gc0jqT4duu" role="3clFbG">
            <node concept="2OqwBi" id="7gc0jqT4dcW" role="2Oq$k0">
              <node concept="2OqwBi" id="7gc0jqT4cQv" role="2Oq$k0">
                <node concept="37vLTw" id="7gc0jqT4cDs" role="2Oq$k0">
                  <ref role="3cqZAo" node="7gc0jqT1ApC" resolve="repoAsNode" />
                </node>
                <node concept="liA8E" id="7gc0jqT4d64" role="2OqNvi">
                  <ref role="37wK5l" to="zxfz:~MPSRepositoryAsNode.getRepository()" resolve="getRepository" />
                </node>
              </node>
              <node concept="liA8E" id="7gc0jqT4dne" role="2OqNvi">
                <ref role="37wK5l" to="lui3:~SRepository.getModelAccess()" resolve="getModelAccess" />
              </node>
            </node>
            <node concept="liA8E" id="7gc0jqT4dK5" role="2OqNvi">
              <ref role="37wK5l" to="lui3:~ModelAccess.runWriteAction(java.lang.Runnable)" resolve="runWriteAction" />
              <node concept="1bVj0M" id="7gc0jqT4dNT" role="37wK5m">
                <node concept="3clFbS" id="7gc0jqT4dNU" role="1bW5cS">
                  <node concept="3clFbF" id="2rlXDkQH5ke" role="3cqZAp">
                    <node concept="2YIFZM" id="2rlXDkQHKkS" role="3clFbG">
                      <ref role="37wK5l" to="2iw0:~PlatformSpecificKt.importFilesAsRootChildren(org.modelix.model.sync.ModelImporter,java.io.File...)" resolve="importFilesAsRootChildren" />
                      <ref role="1Pybhc" to="2iw0:~PlatformSpecificKt" resolve="PlatformSpecificKt" />
                      <node concept="37vLTw" id="2rlXDkQHKkT" role="37wK5m">
                        <ref role="3cqZAo" node="7gc0jqT1xFU" resolve="importer" />
                      </node>
                      <node concept="2OqwBi" id="2rlXDkQHKkU" role="37wK5m">
                        <node concept="37vLTw" id="2rlXDkQHKkV" role="2Oq$k0">
                          <ref role="3cqZAo" node="7gc0jqT1xG5" resolve="inputDir" />
                        </node>
                        <node concept="liA8E" id="2rlXDkQHKkW" role="2OqNvi">
                          <ref role="37wK5l" to="guwi:~File.listFiles(java.io.FileFilter)" resolve="listFiles" />
                          <node concept="1bVj0M" id="2rlXDkQHUeo" role="37wK5m">
                            <node concept="37vLTG" id="2rlXDkQHUls" role="1bW2Oz">
                              <property role="TrG5h" value="file" />
                              <node concept="3uibUv" id="2rlXDkQHUtF" role="1tU5fm">
                                <ref role="3uigEE" to="guwi:~File" resolve="File" />
                              </node>
                            </node>
                            <node concept="3clFbS" id="2rlXDkQHUep" role="1bW5cS">
                              <node concept="3clFbF" id="2rlXDkQHUVn" role="3cqZAp">
                                <node concept="1Wc70l" id="2rlXDkQI0id" role="3clFbG">
                                  <node concept="2OqwBi" id="2rlXDkQI1uX" role="3uHU7w">
                                    <node concept="2OqwBi" id="2rlXDkQI0N$" role="2Oq$k0">
                                      <node concept="37vLTw" id="2rlXDkQI0rY" role="2Oq$k0">
                                        <ref role="3cqZAo" node="2rlXDkQHUls" resolve="file" />
                                      </node>
                                      <node concept="liA8E" id="2rlXDkQI0Zg" role="2OqNvi">
                                        <ref role="37wK5l" to="guwi:~File.getName()" resolve="getName" />
                                      </node>
                                    </node>
                                    <node concept="liA8E" id="2rlXDkQI1Nc" role="2OqNvi">
                                      <ref role="37wK5l" to="wyt6:~String.endsWith(java.lang.String)" resolve="endsWith" />
                                      <node concept="Xl_RD" id="2rlXDkQI1VF" role="37wK5m">
                                        <property role="Xl_RC" value=".json" />
                                      </node>
                                    </node>
                                  </node>
                                  <node concept="2OqwBi" id="2rlXDkQHV7H" role="3uHU7B">
                                    <node concept="37vLTw" id="2rlXDkQHUVm" role="2Oq$k0">
                                      <ref role="3cqZAo" node="2rlXDkQHUls" resolve="file" />
                                    </node>
                                    <node concept="liA8E" id="2rlXDkQHVh5" role="2OqNvi">
                                      <ref role="37wK5l" to="guwi:~File.isFile()" resolve="isFile" />
                                    </node>
                                  </node>
                                </node>
                              </node>
                            </node>
                          </node>
                        </node>
                      </node>
                    </node>
                  </node>
                </node>
              </node>
            </node>
          </node>
        </node>
      </node>
      <node concept="3cqZAl" id="7gc0jqT1xGe" role="3clF45" />
      <node concept="3Tm1VV" id="7gc0jqT1xGf" role="1B3o_S" />
    </node>
    <node concept="2tJIrI" id="7gc0jqT1xEc" role="jymVt" />
    <node concept="2YIFZL" id="7gc0jqT1yfO" role="jymVt">
      <property role="TrG5h" value="getRepositoryAsNode" />
      <node concept="3uibUv" id="7gc0jqT1yj8" role="3clF45">
        <ref role="3uigEE" to="zxfz:~MPSRepositoryAsNode" resolve="MPSRepositoryAsNode" />
      </node>
      <node concept="3Tm6S6" id="7gc0jqT1yiq" role="1B3o_S" />
      <node concept="3clFbS" id="7gc0jqT1yfS" role="3clF47">
        <node concept="3cpWs8" id="7gc0jqT1yAa" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT1yAb" role="3cpWs9">
            <property role="TrG5h" value="repoPath" />
            <node concept="3uibUv" id="7gc0jqT1yAc" role="1tU5fm">
              <ref role="3uigEE" to="wyt6:~String" resolve="String" />
            </node>
            <node concept="2YIFZM" id="7gc0jqT1yAd" role="33vP2m">
              <ref role="37wK5l" to="wyt6:~System.getProperty(java.lang.String)" resolve="getProperty" />
              <ref role="1Pybhc" to="wyt6:~System" resolve="System" />
              <node concept="Xl_RD" id="7gc0jqT1yAe" role="37wK5m">
                <property role="Xl_RC" value="modelix.model.sync.repo.path" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="7gc0jqT1$nE" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT1$nF" role="3cpWs9">
            <property role="TrG5h" value="project" />
            <node concept="3uibUv" id="7gc0jqT1$nG" role="1tU5fm">
              <ref role="3uigEE" to="4nm9:~Project" resolve="Project" />
            </node>
            <node concept="10Nm6u" id="7gc0jqT1$tF" role="33vP2m" />
          </node>
        </node>
        <node concept="3J1_TO" id="7gc0jqT1yES" role="3cqZAp">
          <node concept="3uVAMA" id="7gc0jqT1yRr" role="1zxBo5">
            <node concept="XOnhg" id="7gc0jqT1yRs" role="1zc67B">
              <property role="TrG5h" value="e" />
              <node concept="nSUau" id="7gc0jqT1yRt" role="1tU5fm">
                <node concept="3uibUv" id="7gc0jqT1yTo" role="nSUat">
                  <ref role="3uigEE" to="wyt6:~Exception" resolve="Exception" />
                </node>
              </node>
            </node>
            <node concept="3clFbS" id="7gc0jqT1yRu" role="1zc67A">
              <node concept="YS8fn" id="7gc0jqT1yY$" role="3cqZAp">
                <node concept="2ShNRf" id="7gc0jqT1z0U" role="YScLw">
                  <node concept="1pGfFk" id="7gc0jqT1ziM" role="2ShVmc">
                    <ref role="37wK5l" to="wyt6:~RuntimeException.&lt;init&gt;(java.lang.String)" resolve="RuntimeException" />
                    <node concept="Xl_RD" id="7gc0jqT1zn6" role="37wK5m">
                      <property role="Xl_RC" value="project not found" />
                    </node>
                  </node>
                </node>
              </node>
            </node>
          </node>
          <node concept="3clFbS" id="7gc0jqT1yET" role="1zxBo7">
            <node concept="3clFbF" id="7gc0jqT1$Bm" role="3cqZAp">
              <node concept="37vLTI" id="7gc0jqT1$QX" role="3clFbG">
                <node concept="2OqwBi" id="7gc0jqT1_8K" role="37vLTx">
                  <node concept="2YIFZM" id="7gc0jqT1$Xa" role="2Oq$k0">
                    <ref role="37wK5l" to="4nm9:~ProjectManager.getInstance()" resolve="getInstance" />
                    <ref role="1Pybhc" to="4nm9:~ProjectManager" resolve="ProjectManager" />
                  </node>
                  <node concept="liA8E" id="7gc0jqT1_in" role="2OqNvi">
                    <ref role="37wK5l" to="4nm9:~ProjectManager.loadAndOpenProject(java.lang.String)" resolve="loadAndOpenProject" />
                    <node concept="37vLTw" id="7gc0jqT1_mg" role="37wK5m">
                      <ref role="3cqZAo" node="7gc0jqT1yAb" resolve="repoPath" />
                    </node>
                  </node>
                </node>
                <node concept="37vLTw" id="7gc0jqT1$Bk" role="37vLTJ">
                  <ref role="3cqZAo" node="7gc0jqT1$nF" resolve="project" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="7gc0jqT1yAm" role="3cqZAp">
          <node concept="3cpWsn" id="7gc0jqT1yAn" role="3cpWs9">
            <property role="TrG5h" value="repo" />
            <node concept="3uibUv" id="7gc0jqT1yAo" role="1tU5fm">
              <ref role="3uigEE" to="lui2:~SRepository" resolve="SRepository" />
            </node>
            <node concept="2YIFZM" id="7gc0jqT1yAp" role="33vP2m">
              <ref role="1Pybhc" to="alof:~ProjectHelper" resolve="ProjectHelper" />
              <ref role="37wK5l" to="alof:~ProjectHelper.getProjectRepository(com.intellij.openapi.project.Project)" resolve="getProjectRepository" />
              <node concept="37vLTw" id="7gc0jqT1yAq" role="37wK5m">
                <ref role="3cqZAo" node="7gc0jqT1$nF" resolve="project" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs6" id="7gc0jqT1zIc" role="3cqZAp">
          <node concept="2ShNRf" id="7gc0jqT1zPp" role="3cqZAk">
            <node concept="1pGfFk" id="7gc0jqT1$7x" role="2ShVmc">
              <ref role="37wK5l" to="zxfz:~MPSRepositoryAsNode.&lt;init&gt;(org.jetbrains.mps.openapi.module.SRepository)" resolve="MPSRepositoryAsNode" />
              <node concept="37vLTw" id="7gc0jqT1$aw" role="37wK5m">
                <ref role="3cqZAo" node="7gc0jqT1yAn" resolve="repo" />
              </node>
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="3Tm1VV" id="5s5xg7sH9bP" role="1B3o_S" />
  </node>
</model>
