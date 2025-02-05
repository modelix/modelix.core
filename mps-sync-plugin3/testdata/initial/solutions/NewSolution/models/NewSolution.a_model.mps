<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:cd78e6ac-0e34-490a-9b49-e5643f948d6d(NewSolution.a_model)">
  <persistence version="9" />
  <languages>
    <devkit ref="fbc25dd2-5da4-483a-8b19-70928e1b62d7(jetbrains.mps.devkit.general-purpose)" />
  </languages>
  <imports />
  <registry>
    <language id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage">
      <concept id="1070534370425" name="jetbrains.mps.baseLanguage.structure.IntegerType" flags="in" index="10Oyi0" />
      <concept id="1068390468198" name="jetbrains.mps.baseLanguage.structure.ClassConcept" flags="ig" index="312cEu" />
      <concept id="1068498886296" name="jetbrains.mps.baseLanguage.structure.VariableReference" flags="nn" index="37vLTw">
        <reference id="1068581517664" name="variableDeclaration" index="3cqZAo" />
      </concept>
      <concept id="1068498886292" name="jetbrains.mps.baseLanguage.structure.ParameterDeclaration" flags="ir" index="37vLTG" />
      <concept id="4972933694980447171" name="jetbrains.mps.baseLanguage.structure.BaseVariableDeclaration" flags="ng" index="19Szcq">
        <child id="5680397130376446158" name="type" index="1tU5fm" />
      </concept>
      <concept id="1068580123132" name="jetbrains.mps.baseLanguage.structure.BaseMethodDeclaration" flags="ng" index="3clF44">
        <child id="1068580123133" name="returnType" index="3clF45" />
        <child id="1068580123134" name="parameter" index="3clF46" />
        <child id="1068580123135" name="body" index="3clF47" />
      </concept>
      <concept id="1068580123165" name="jetbrains.mps.baseLanguage.structure.InstanceMethodDeclaration" flags="ig" index="3clFb_" />
      <concept id="1068580123136" name="jetbrains.mps.baseLanguage.structure.StatementList" flags="sn" stub="5293379017992965193" index="3clFbS">
        <child id="1068581517665" name="statement" index="3cqZAp" />
      </concept>
      <concept id="1068581242875" name="jetbrains.mps.baseLanguage.structure.PlusExpression" flags="nn" index="3cpWs3" />
      <concept id="1068581242878" name="jetbrains.mps.baseLanguage.structure.ReturnStatement" flags="nn" index="3cpWs6">
        <child id="1068581517676" name="expression" index="3cqZAk" />
      </concept>
      <concept id="1107461130800" name="jetbrains.mps.baseLanguage.structure.Classifier" flags="ng" index="3pOWGL">
        <child id="5375687026011219971" name="member" index="jymVt" unordered="true" />
      </concept>
      <concept id="1081773326031" name="jetbrains.mps.baseLanguage.structure.BinaryOperation" flags="nn" index="3uHJSO">
        <child id="1081773367579" name="rightExpression" index="3uHU7w" />
        <child id="1081773367580" name="leftExpression" index="3uHU7B" />
      </concept>
      <concept id="1178549954367" name="jetbrains.mps.baseLanguage.structure.IVisible" flags="ng" index="1B3ioH">
        <child id="1178549979242" name="visibility" index="1B3o_S" />
      </concept>
      <concept id="1146644602865" name="jetbrains.mps.baseLanguage.structure.PublicVisibility" flags="nn" index="3Tm1VV" />
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ng" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
  </registry>
  <node concept="312cEu" id="7bG66aOHG9v">
    <property role="TrG5h" value="MyClass" />
    <node concept="3clFb_" id="7bG66aOHGar" role="jymVt">
      <property role="TrG5h" value="plus" />
      <node concept="37vLTG" id="1TA56gtftpq" role="3clF46">
        <property role="TrG5h" value="a" />
        <node concept="10Oyi0" id="1TA56gtftpN" role="1tU5fm" />
      </node>
      <node concept="37vLTG" id="1TA56gtftqW" role="3clF46">
        <property role="TrG5h" value="b" />
        <node concept="10Oyi0" id="1TA56gtftrm" role="1tU5fm" />
      </node>
      <node concept="10Oyi0" id="1TA56gtfumO" role="3clF45" />
      <node concept="3Tm1VV" id="7bG66aOHGau" role="1B3o_S" />
      <node concept="3clFbS" id="7bG66aOHGav" role="3clF47">
        <node concept="3cpWs6" id="1TA56gtfts3" role="3cqZAp">
          <node concept="3cpWs3" id="1TA56gtfu94" role="3cqZAk">
            <node concept="37vLTw" id="1TA56gtfu9R" role="3uHU7w">
              <ref role="3cqZAo" node="1TA56gtftqW" resolve="b" />
            </node>
            <node concept="37vLTw" id="1TA56gtftsC" role="3uHU7B">
              <ref role="3cqZAo" node="1TA56gtftpq" resolve="a" />
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="3Tm1VV" id="7bG66aOHG9w" role="1B3o_S" />
  </node>
</model>
