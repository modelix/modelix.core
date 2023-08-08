<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:593da0c5-f572-49a5-be63-5cd519050309(org.modelix.metamodel.export)">
  <persistence version="9" />
  <languages>
    <use id="7866978e-a0f0-4cc7-81bc-4d213d9375e1" name="jetbrains.mps.lang.smodel" version="18" />
    <use id="760a0a8c-eabb-4521-8bfd-65db761a9ba3" name="jetbrains.mps.baseLanguage.logging" version="0" />
    <use id="83888646-71ce-4f1c-9c53-c54016f6ad4f" name="jetbrains.mps.baseLanguage.collections" version="1" />
    <use id="446c26eb-2b7b-4bf0-9b35-f83fa582753e" name="jetbrains.mps.lang.modelapi" version="0" />
    <use id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage" version="11" />
    <use id="fd392034-7849-419d-9071-12563d152375" name="jetbrains.mps.baseLanguage.closures" version="0" />
    <use id="774bf8a0-62e5-41e1-af63-f4812e60e48b" name="jetbrains.mps.baseLanguage.checkedDots" version="0" />
    <use id="c72da2b9-7cce-4447-8389-f407dc1158b7" name="jetbrains.mps.lang.structure" version="9" />
  </languages>
  <imports>
    <import index="guwi" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.io(JDK/)" />
    <import index="79ha" ref="r:2876f1ee-0b45-4db5-8c09-0682cdee5c67(jetbrains.mps.tool.environment)" />
    <import index="mhbf" ref="8865b7a8-5271-43d3-884c-6fd1d9cfdd34/java:org.jetbrains.mps.openapi.model(MPS.OpenAPI/)" />
    <import index="tpck" ref="r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)" />
    <import index="tpcn" ref="r:00000000-0000-4000-0000-011c8959028b(jetbrains.mps.lang.structure.behavior)" />
    <import index="z1c3" ref="6ed54515-acc8-4d1e-a16c-9fd6cfe951ea/java:jetbrains.mps.project(MPS.Core/)" />
    <import index="lui2" ref="8865b7a8-5271-43d3-884c-6fd1d9cfdd34/java:org.jetbrains.mps.openapi.module(MPS.OpenAPI/)" />
    <import index="e8bb" ref="6ed54515-acc8-4d1e-a16c-9fd6cfe951ea/java:jetbrains.mps.smodel.adapter.ids(MPS.Core/)" />
    <import index="tpce" ref="r:00000000-0000-4000-0000-011c89590292(jetbrains.mps.lang.structure.structure)" />
    <import index="w1kc" ref="6ed54515-acc8-4d1e-a16c-9fd6cfe951ea/java:jetbrains.mps.smodel(MPS.Core/)" />
    <import index="4nxv" ref="e52a4421-48a2-4de1-8327-d9414e799c67/java:kotlin.io(org.modelix.metamodel.export/)" />
    <import index="7x5y" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.nio.charset(JDK/)" />
    <import index="sgfj" ref="e52a4421-48a2-4de1-8327-d9414e799c67/java:org.modelix.model.data(org.modelix.metamodel.export/)" />
    <import index="c17a" ref="8865b7a8-5271-43d3-884c-6fd1d9cfdd34/java:org.jetbrains.mps.openapi.language(MPS.OpenAPI/)" />
    <import index="33ny" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.util(JDK/)" />
    <import index="mhfm" ref="3f233e7f-b8a6-46d2-a57f-795d56775243/java:org.jetbrains.annotations(Annotations/)" />
    <import index="wyt6" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.lang(JDK/)" />
    <import index="tpcu" ref="r:00000000-0000-4000-0000-011c89590282(jetbrains.mps.lang.core.behavior)" implicit="true" />
  </imports>
  <registry>
    <language id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage">
      <concept id="1224071154655" name="jetbrains.mps.baseLanguage.structure.AsExpression" flags="nn" index="0kSF2">
        <child id="1224071154657" name="classifierType" index="0kSFW" />
        <child id="1224071154656" name="expression" index="0kSFX" />
      </concept>
      <concept id="1080223426719" name="jetbrains.mps.baseLanguage.structure.OrExpression" flags="nn" index="22lmx$" />
      <concept id="1215693861676" name="jetbrains.mps.baseLanguage.structure.BaseAssignmentExpression" flags="nn" index="d038R">
        <child id="1068498886297" name="rValue" index="37vLTx" />
        <child id="1068498886295" name="lValue" index="37vLTJ" />
      </concept>
      <concept id="4836112446988635817" name="jetbrains.mps.baseLanguage.structure.UndefinedType" flags="in" index="2jxLKc" />
      <concept id="1202948039474" name="jetbrains.mps.baseLanguage.structure.InstanceMethodCallOperation" flags="nn" index="liA8E" />
      <concept id="1465982738277781862" name="jetbrains.mps.baseLanguage.structure.PlaceholderMember" flags="nn" index="2tJIrI" />
      <concept id="1188207840427" name="jetbrains.mps.baseLanguage.structure.AnnotationInstance" flags="nn" index="2AHcQZ">
        <reference id="1188208074048" name="annotation" index="2AI5Lk" />
      </concept>
      <concept id="1188208481402" name="jetbrains.mps.baseLanguage.structure.HasAnnotation" flags="ng" index="2AJDlI">
        <child id="1188208488637" name="annotation" index="2AJF6D" />
      </concept>
      <concept id="1154032098014" name="jetbrains.mps.baseLanguage.structure.AbstractLoopStatement" flags="nn" index="2LF5Ji">
        <child id="1154032183016" name="body" index="2LFqv$" />
      </concept>
      <concept id="1197027756228" name="jetbrains.mps.baseLanguage.structure.DotExpression" flags="nn" index="2OqwBi">
        <child id="1197027771414" name="operand" index="2Oq$k0" />
        <child id="1197027833540" name="operation" index="2OqNvi" />
      </concept>
      <concept id="1197029447546" name="jetbrains.mps.baseLanguage.structure.FieldReferenceOperation" flags="nn" index="2OwXpG">
        <reference id="1197029500499" name="fieldDeclaration" index="2Oxat5" />
      </concept>
      <concept id="1083260308424" name="jetbrains.mps.baseLanguage.structure.EnumConstantReference" flags="nn" index="Rm8GO">
        <reference id="1083260308426" name="enumConstantDeclaration" index="Rm8GQ" />
        <reference id="1144432896254" name="enumClass" index="1Px2BO" />
      </concept>
      <concept id="1145552977093" name="jetbrains.mps.baseLanguage.structure.GenericNewExpression" flags="nn" index="2ShNRf">
        <child id="1145553007750" name="creator" index="2ShVmc" />
      </concept>
      <concept id="1070475354124" name="jetbrains.mps.baseLanguage.structure.ThisExpression" flags="nn" index="Xjq3P" />
      <concept id="1070475926800" name="jetbrains.mps.baseLanguage.structure.StringLiteral" flags="nn" index="Xl_RD">
        <property id="1070475926801" name="value" index="Xl_RC" />
      </concept>
      <concept id="1081236700938" name="jetbrains.mps.baseLanguage.structure.StaticMethodDeclaration" flags="ig" index="2YIFZL" />
      <concept id="1081236700937" name="jetbrains.mps.baseLanguage.structure.StaticMethodCall" flags="nn" index="2YIFZM">
        <reference id="1144433194310" name="classConcept" index="1Pybhc" />
      </concept>
      <concept id="1070533707846" name="jetbrains.mps.baseLanguage.structure.StaticFieldReference" flags="nn" index="10M0yZ">
        <reference id="1144433057691" name="classifier" index="1PxDUh" />
      </concept>
      <concept id="1070534058343" name="jetbrains.mps.baseLanguage.structure.NullLiteral" flags="nn" index="10Nm6u" />
      <concept id="1070534370425" name="jetbrains.mps.baseLanguage.structure.IntegerType" flags="in" index="10Oyi0" />
      <concept id="1070534644030" name="jetbrains.mps.baseLanguage.structure.BooleanType" flags="in" index="10P_77" />
      <concept id="1068390468200" name="jetbrains.mps.baseLanguage.structure.FieldDeclaration" flags="ig" index="312cEg" />
      <concept id="1068390468198" name="jetbrains.mps.baseLanguage.structure.ClassConcept" flags="ig" index="312cEu" />
      <concept id="1068431474542" name="jetbrains.mps.baseLanguage.structure.VariableDeclaration" flags="ng" index="33uBYm">
        <child id="1068431790190" name="initializer" index="33vP2m" />
      </concept>
      <concept id="1068498886296" name="jetbrains.mps.baseLanguage.structure.VariableReference" flags="nn" index="37vLTw">
        <reference id="1068581517664" name="variableDeclaration" index="3cqZAo" />
      </concept>
      <concept id="1068498886292" name="jetbrains.mps.baseLanguage.structure.ParameterDeclaration" flags="ir" index="37vLTG" />
      <concept id="1068498886294" name="jetbrains.mps.baseLanguage.structure.AssignmentExpression" flags="nn" index="37vLTI" />
      <concept id="1225271177708" name="jetbrains.mps.baseLanguage.structure.StringType" flags="in" index="17QB3L" />
      <concept id="1225271283259" name="jetbrains.mps.baseLanguage.structure.NPEEqualsExpression" flags="nn" index="17R0WA" />
      <concept id="1225271408483" name="jetbrains.mps.baseLanguage.structure.IsNotEmptyOperation" flags="nn" index="17RvpY" />
      <concept id="4972933694980447171" name="jetbrains.mps.baseLanguage.structure.BaseVariableDeclaration" flags="ng" index="19Szcq">
        <child id="5680397130376446158" name="type" index="1tU5fm" />
      </concept>
      <concept id="1068580123132" name="jetbrains.mps.baseLanguage.structure.BaseMethodDeclaration" flags="ng" index="3clF44">
        <child id="1068580123133" name="returnType" index="3clF45" />
        <child id="1068580123134" name="parameter" index="3clF46" />
        <child id="1068580123135" name="body" index="3clF47" />
      </concept>
      <concept id="1068580123165" name="jetbrains.mps.baseLanguage.structure.InstanceMethodDeclaration" flags="ig" index="3clFb_" />
      <concept id="1068580123152" name="jetbrains.mps.baseLanguage.structure.EqualsExpression" flags="nn" index="3clFbC" />
      <concept id="1068580123155" name="jetbrains.mps.baseLanguage.structure.ExpressionStatement" flags="nn" index="3clFbF">
        <child id="1068580123156" name="expression" index="3clFbG" />
      </concept>
      <concept id="1068580123157" name="jetbrains.mps.baseLanguage.structure.Statement" flags="nn" index="3clFbH" />
      <concept id="1068580123159" name="jetbrains.mps.baseLanguage.structure.IfStatement" flags="nn" index="3clFbJ">
        <child id="1068580123160" name="condition" index="3clFbw" />
        <child id="1068580123161" name="ifTrue" index="3clFbx" />
        <child id="1206060520071" name="elsifClauses" index="3eNLev" />
      </concept>
      <concept id="1068580123136" name="jetbrains.mps.baseLanguage.structure.StatementList" flags="sn" stub="5293379017992965193" index="3clFbS">
        <child id="1068581517665" name="statement" index="3cqZAp" />
      </concept>
      <concept id="1068580123137" name="jetbrains.mps.baseLanguage.structure.BooleanConstant" flags="nn" index="3clFbT">
        <property id="1068580123138" name="value" index="3clFbU" />
      </concept>
      <concept id="1068580123140" name="jetbrains.mps.baseLanguage.structure.ConstructorDeclaration" flags="ig" index="3clFbW" />
      <concept id="1068580320020" name="jetbrains.mps.baseLanguage.structure.IntegerConstant" flags="nn" index="3cmrfG">
        <property id="1068580320021" name="value" index="3cmrfH" />
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
      <concept id="1206060495898" name="jetbrains.mps.baseLanguage.structure.ElsifClause" flags="ng" index="3eNFk2">
        <child id="1206060619838" name="condition" index="3eO9$A" />
        <child id="1206060644605" name="statementList" index="3eOfB_" />
      </concept>
      <concept id="1081516740877" name="jetbrains.mps.baseLanguage.structure.NotExpression" flags="nn" index="3fqX7Q">
        <child id="1081516765348" name="expression" index="3fr31v" />
      </concept>
      <concept id="1204053956946" name="jetbrains.mps.baseLanguage.structure.IMethodCall" flags="ng" index="1ndlxa">
        <reference id="1068499141037" name="baseMethodDeclaration" index="37wK5l" />
        <child id="1068499141038" name="actualArgument" index="37wK5m" />
        <child id="4972241301747169160" name="typeArgument" index="3PaCim" />
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
      <concept id="1073239437375" name="jetbrains.mps.baseLanguage.structure.NotEqualsExpression" flags="nn" index="3y3z36" />
      <concept id="1178549954367" name="jetbrains.mps.baseLanguage.structure.IVisible" flags="ng" index="1B3ioH">
        <child id="1178549979242" name="visibility" index="1B3o_S" />
      </concept>
      <concept id="1163668896201" name="jetbrains.mps.baseLanguage.structure.TernaryOperatorExpression" flags="nn" index="3K4zz7">
        <child id="1163668914799" name="condition" index="3K4Cdx" />
        <child id="1163668922816" name="ifTrue" index="3K4E3e" />
        <child id="1163668934364" name="ifFalse" index="3K4GZi" />
      </concept>
      <concept id="1146644602865" name="jetbrains.mps.baseLanguage.structure.PublicVisibility" flags="nn" index="3Tm1VV" />
      <concept id="1146644623116" name="jetbrains.mps.baseLanguage.structure.PrivateVisibility" flags="nn" index="3Tm6S6" />
    </language>
    <language id="774bf8a0-62e5-41e1-af63-f4812e60e48b" name="jetbrains.mps.baseLanguage.checkedDots">
      <concept id="4079382982702596667" name="jetbrains.mps.baseLanguage.checkedDots.structure.CheckedDotExpression" flags="nn" index="2EnYce" />
    </language>
    <language id="fd392034-7849-419d-9071-12563d152375" name="jetbrains.mps.baseLanguage.closures">
      <concept id="1199569711397" name="jetbrains.mps.baseLanguage.closures.structure.ClosureLiteral" flags="nn" index="1bVj0M">
        <child id="1199569906740" name="parameter" index="1bW2Oz" />
        <child id="1199569916463" name="body" index="1bW5cS" />
      </concept>
    </language>
    <language id="446c26eb-2b7b-4bf0-9b35-f83fa582753e" name="jetbrains.mps.lang.modelapi">
      <concept id="4733039728785194814" name="jetbrains.mps.lang.modelapi.structure.NamedNodeReference" flags="ng" index="ZC_QK">
        <reference id="7256306938026143658" name="target" index="2aWVGs" />
      </concept>
    </language>
    <language id="7866978e-a0f0-4cc7-81bc-4d213d9375e1" name="jetbrains.mps.lang.smodel">
      <concept id="4705942098322609812" name="jetbrains.mps.lang.smodel.structure.EnumMember_IsOperation" flags="ng" index="21noJN">
        <child id="4705942098322609813" name="member" index="21noJM" />
      </concept>
      <concept id="4705942098322467729" name="jetbrains.mps.lang.smodel.structure.EnumMemberReference" flags="ng" index="21nZrQ">
        <reference id="4705942098322467736" name="decl" index="21nZrZ" />
      </concept>
      <concept id="1179168000618" name="jetbrains.mps.lang.smodel.structure.Node_GetIndexInParentOperation" flags="nn" index="2bSWHS" />
      <concept id="1177026924588" name="jetbrains.mps.lang.smodel.structure.RefConcept_Reference" flags="nn" index="chp4Y">
        <reference id="1177026940964" name="conceptDeclaration" index="cht4Q" />
      </concept>
      <concept id="1138411891628" name="jetbrains.mps.lang.smodel.structure.SNodeOperation" flags="nn" index="eCIE_">
        <child id="1144104376918" name="parameter" index="1xVPHs" />
      </concept>
      <concept id="5045161044515397667" name="jetbrains.mps.lang.smodel.structure.Node_PointerOperation" flags="ng" index="iZEcu" />
      <concept id="1179409122411" name="jetbrains.mps.lang.smodel.structure.Node_ConceptMethodCall" flags="nn" index="2qgKlT" />
      <concept id="7400021826774799413" name="jetbrains.mps.lang.smodel.structure.NodePointerExpression" flags="ng" index="2tJFMh">
        <child id="7400021826774799510" name="ref" index="2tJFKM" />
      </concept>
      <concept id="4693937538533521280" name="jetbrains.mps.lang.smodel.structure.OfConceptOperation" flags="ng" index="v3k3i">
        <child id="4693937538533538124" name="requestedConcept" index="v3oSu" />
      </concept>
      <concept id="4065387505485742749" name="jetbrains.mps.lang.smodel.structure.AbstractPointerResolveOperation" flags="ng" index="2yCiFS">
        <child id="3648723375513868575" name="repositoryArg" index="Vysub" />
      </concept>
      <concept id="7453996997717780434" name="jetbrains.mps.lang.smodel.structure.Node_GetSConceptOperation" flags="nn" index="2yIwOk" />
      <concept id="8758390115028452779" name="jetbrains.mps.lang.smodel.structure.Node_GetReferencesOperation" flags="nn" index="2z74zc" />
      <concept id="2396822768958367367" name="jetbrains.mps.lang.smodel.structure.AbstractTypeCastExpression" flags="nn" index="$5XWr">
        <child id="6733348108486823193" name="leftExpression" index="1m5AlR" />
        <child id="3906496115198199033" name="conceptArgument" index="3oSUPX" />
      </concept>
      <concept id="1143234257716" name="jetbrains.mps.lang.smodel.structure.Node_GetModelOperation" flags="nn" index="I4A8Y" />
      <concept id="1145404486709" name="jetbrains.mps.lang.smodel.structure.SemanticDowncastExpression" flags="nn" index="2JrnkZ">
        <child id="1145404616321" name="leftExpression" index="2JrQYb" />
      </concept>
      <concept id="1171305280644" name="jetbrains.mps.lang.smodel.structure.Node_GetDescendantsOperation" flags="nn" index="2Rf3mk" />
      <concept id="3648723375513868532" name="jetbrains.mps.lang.smodel.structure.NodePointer_ResolveOperation" flags="ng" index="Vyspw" />
      <concept id="1171500988903" name="jetbrains.mps.lang.smodel.structure.Node_GetChildrenOperation" flags="nn" index="32TBzR" />
      <concept id="1139613262185" name="jetbrains.mps.lang.smodel.structure.Node_GetParentOperation" flags="nn" index="1mfA1w" />
      <concept id="1139621453865" name="jetbrains.mps.lang.smodel.structure.Node_IsInstanceOfOperation" flags="nn" index="1mIQ4w">
        <child id="1177027386292" name="conceptArgument" index="cj9EA" />
      </concept>
      <concept id="1171999116870" name="jetbrains.mps.lang.smodel.structure.Node_IsNullOperation" flags="nn" index="3w_OXm" />
      <concept id="1172008320231" name="jetbrains.mps.lang.smodel.structure.Node_IsNotNullOperation" flags="nn" index="3x8VRR" />
      <concept id="1144101972840" name="jetbrains.mps.lang.smodel.structure.OperationParm_Concept" flags="ng" index="1xMEDy">
        <child id="1207343664468" name="conceptArgument" index="ri$Ld" />
      </concept>
      <concept id="1140137987495" name="jetbrains.mps.lang.smodel.structure.SNodeTypeCastExpression" flags="nn" index="1PxgMI">
        <property id="1238684351431" name="asCast" index="1BlNFB" />
      </concept>
      <concept id="1138055754698" name="jetbrains.mps.lang.smodel.structure.SNodeType" flags="in" index="3Tqbb2">
        <reference id="1138405853777" name="concept" index="ehGHo" />
      </concept>
      <concept id="1138056022639" name="jetbrains.mps.lang.smodel.structure.SPropertyAccess" flags="nn" index="3TrcHB">
        <reference id="1138056395725" name="property" index="3TsBF5" />
      </concept>
      <concept id="1138056143562" name="jetbrains.mps.lang.smodel.structure.SLinkAccess" flags="nn" index="3TrEf2">
        <reference id="1138056516764" name="link" index="3Tt5mk" />
      </concept>
      <concept id="1138056282393" name="jetbrains.mps.lang.smodel.structure.SLinkListAccess" flags="nn" index="3Tsc0h">
        <reference id="1138056546658" name="link" index="3TtcxE" />
      </concept>
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ng" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
    <language id="83888646-71ce-4f1c-9c53-c54016f6ad4f" name="jetbrains.mps.baseLanguage.collections">
      <concept id="1204796164442" name="jetbrains.mps.baseLanguage.collections.structure.InternalSequenceOperation" flags="nn" index="23sCx2">
        <child id="1204796294226" name="closure" index="23t8la" />
      </concept>
      <concept id="540871147943773365" name="jetbrains.mps.baseLanguage.collections.structure.SingleArgumentSequenceOperation" flags="nn" index="25WWJ4">
        <child id="540871147943773366" name="argument" index="25WWJ7" />
      </concept>
      <concept id="1226511727824" name="jetbrains.mps.baseLanguage.collections.structure.SetType" flags="in" index="2hMVRd">
        <child id="1226511765987" name="elementType" index="2hN53Y" />
      </concept>
      <concept id="1226516258405" name="jetbrains.mps.baseLanguage.collections.structure.HashSetCreator" flags="nn" index="2i4dXS" />
      <concept id="1151688443754" name="jetbrains.mps.baseLanguage.collections.structure.ListType" flags="in" index="_YKpA">
        <child id="1151688676805" name="elementType" index="_ZDj9" />
      </concept>
      <concept id="1151689724996" name="jetbrains.mps.baseLanguage.collections.structure.SequenceType" flags="in" index="A3Dl8">
        <child id="1151689745422" name="elementType" index="A3Ik2" />
      </concept>
      <concept id="1151702311717" name="jetbrains.mps.baseLanguage.collections.structure.ToListOperation" flags="nn" index="ANE8D" />
      <concept id="1153943597977" name="jetbrains.mps.baseLanguage.collections.structure.ForEachStatement" flags="nn" index="2Gpval">
        <child id="1153944400369" name="variable" index="2Gsz3X" />
        <child id="1153944424730" name="inputSequence" index="2GsD0m" />
      </concept>
      <concept id="1153944193378" name="jetbrains.mps.baseLanguage.collections.structure.ForEachVariable" flags="nr" index="2GrKxI" />
      <concept id="1153944233411" name="jetbrains.mps.baseLanguage.collections.structure.ForEachVariableReference" flags="nn" index="2GrUjf">
        <reference id="1153944258490" name="variable" index="2Gs0qQ" />
      </concept>
      <concept id="1235566554328" name="jetbrains.mps.baseLanguage.collections.structure.AnyOperation" flags="nn" index="2HwmR7" />
      <concept id="1237721394592" name="jetbrains.mps.baseLanguage.collections.structure.AbstractContainerCreator" flags="nn" index="HWqM0">
        <child id="1237721435808" name="initValue" index="HW$Y0" />
        <child id="1237721435807" name="elementType" index="HW$YZ" />
        <child id="1237731803878" name="copyFrom" index="I$8f6" />
      </concept>
      <concept id="1203518072036" name="jetbrains.mps.baseLanguage.collections.structure.SmartClosureParameterDeclaration" flags="ig" index="Rh6nW" />
      <concept id="1237909114519" name="jetbrains.mps.baseLanguage.collections.structure.GetValuesOperation" flags="nn" index="T8wYR" />
      <concept id="1160600644654" name="jetbrains.mps.baseLanguage.collections.structure.ListCreatorWithInit" flags="nn" index="Tc6Ow" />
      <concept id="1160612413312" name="jetbrains.mps.baseLanguage.collections.structure.AddElementOperation" flags="nn" index="TSZUe" />
      <concept id="4611582986551314327" name="jetbrains.mps.baseLanguage.collections.structure.OfTypeOperation" flags="nn" index="UnYns">
        <child id="4611582986551314344" name="requestedType" index="UnYnz" />
      </concept>
      <concept id="1240216724530" name="jetbrains.mps.baseLanguage.collections.structure.LinkedHashMapCreator" flags="nn" index="32Fmki" />
      <concept id="1162935959151" name="jetbrains.mps.baseLanguage.collections.structure.GetSizeOperation" flags="nn" index="34oBXx" />
      <concept id="1240325842691" name="jetbrains.mps.baseLanguage.collections.structure.AsSequenceOperation" flags="nn" index="39bAoz" />
      <concept id="1201792049884" name="jetbrains.mps.baseLanguage.collections.structure.TranslateOperation" flags="nn" index="3goQfb" />
      <concept id="1197683403723" name="jetbrains.mps.baseLanguage.collections.structure.MapType" flags="in" index="3rvAFt">
        <child id="1197683466920" name="keyType" index="3rvQeY" />
        <child id="1197683475734" name="valueType" index="3rvSg0" />
      </concept>
      <concept id="1197686869805" name="jetbrains.mps.baseLanguage.collections.structure.HashMapCreator" flags="nn" index="3rGOSV">
        <child id="1197687026896" name="keyType" index="3rHrn6" />
        <child id="1197687035757" name="valueType" index="3rHtpV" />
      </concept>
      <concept id="1165530316231" name="jetbrains.mps.baseLanguage.collections.structure.IsEmptyOperation" flags="nn" index="1v1jN8" />
      <concept id="1202120902084" name="jetbrains.mps.baseLanguage.collections.structure.WhereOperation" flags="nn" index="3zZkjj" />
      <concept id="1202128969694" name="jetbrains.mps.baseLanguage.collections.structure.SelectOperation" flags="nn" index="3$u5V9" />
      <concept id="1197932370469" name="jetbrains.mps.baseLanguage.collections.structure.MapElement" flags="nn" index="3EllGN">
        <child id="1197932505799" name="map" index="3ElQJh" />
        <child id="1197932525128" name="key" index="3ElVtu" />
      </concept>
      <concept id="1172254888721" name="jetbrains.mps.baseLanguage.collections.structure.ContainsOperation" flags="nn" index="3JPx81" />
      <concept id="31378964227347002" name="jetbrains.mps.baseLanguage.collections.structure.SelectNotNullOperation" flags="ng" index="1KnU$U" />
      <concept id="1178894719932" name="jetbrains.mps.baseLanguage.collections.structure.DistinctOperation" flags="nn" index="1VAtEI" />
    </language>
  </registry>
  <node concept="312cEu" id="3b5oxbT8uGz">
    <property role="TrG5h" value="CommandlineExporter" />
    <node concept="2YIFZL" id="3b5oxbTadzA" role="jymVt">
      <property role="TrG5h" value="exportLanguages" />
      <node concept="3clFbS" id="3b5oxbT8uLj" role="3clF47">
        <node concept="3cpWs8" id="cGlNZN3gwu" role="3cqZAp">
          <node concept="3cpWsn" id="cGlNZN3gwv" role="3cpWs9">
            <property role="TrG5h" value="repo" />
            <node concept="3uibUv" id="cGlNZN3giP" role="1tU5fm">
              <ref role="3uigEE" to="w1kc:~MPSModuleRepository" resolve="MPSModuleRepository" />
            </node>
            <node concept="2YIFZM" id="cGlNZN3gww" role="33vP2m">
              <ref role="37wK5l" to="w1kc:~MPSModuleRepository.getInstance()" resolve="getInstance" />
              <ref role="1Pybhc" to="w1kc:~MPSModuleRepository" resolve="MPSModuleRepository" />
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="cGlNZN3gZu" role="3cqZAp">
          <node concept="2OqwBi" id="cGlNZN3is_" role="3clFbG">
            <node concept="2OqwBi" id="cGlNZN3hqo" role="2Oq$k0">
              <node concept="37vLTw" id="cGlNZN3gZs" role="2Oq$k0">
                <ref role="3cqZAo" node="cGlNZN3gwv" resolve="repo" />
              </node>
              <node concept="liA8E" id="cGlNZN3imP" role="2OqNvi">
                <ref role="37wK5l" to="w1kc:~MPSModuleRepository.getModelAccess()" resolve="getModelAccess" />
              </node>
            </node>
            <node concept="liA8E" id="cGlNZN3iLd" role="2OqNvi">
              <ref role="37wK5l" to="lui2:~ModelAccess.runReadAction(java.lang.Runnable)" resolve="runReadAction" />
              <node concept="1bVj0M" id="cGlNZN3iMY" role="37wK5m">
                <node concept="3clFbS" id="cGlNZN3iMZ" role="1bW5cS">
                  <node concept="3cpWs8" id="3b5oxbT8$Ao" role="3cqZAp">
                    <node concept="3cpWsn" id="3b5oxbT8$Ap" role="3cpWs9">
                      <property role="TrG5h" value="modules" />
                      <node concept="A3Dl8" id="3b5oxbT8$M0" role="1tU5fm">
                        <node concept="3uibUv" id="3b5oxbT8$M2" role="A3Ik2">
                          <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
                        </node>
                      </node>
                      <node concept="2OqwBi" id="3b5oxbT8$Aq" role="33vP2m">
                        <node concept="37vLTw" id="cGlNZN3gwx" role="2Oq$k0">
                          <ref role="3cqZAo" node="cGlNZN3gwv" resolve="repo" />
                        </node>
                        <node concept="liA8E" id="3b5oxbT8$As" role="2OqNvi">
                          <ref role="37wK5l" to="w1kc:~MPSModuleRepository.getModules()" resolve="getModules" />
                        </node>
                      </node>
                    </node>
                  </node>
                  <node concept="3cpWs8" id="3b5oxbT8_tt" role="3cqZAp">
                    <node concept="3cpWsn" id="3b5oxbT8_tu" role="3cpWs9">
                      <property role="TrG5h" value="languages" />
                      <node concept="A3Dl8" id="3b5oxbT8_td" role="1tU5fm">
                        <node concept="3uibUv" id="3b5oxbT8_tg" role="A3Ik2">
                          <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
                        </node>
                      </node>
                      <node concept="2OqwBi" id="3b5oxbT8_tv" role="33vP2m">
                        <node concept="37vLTw" id="3b5oxbT8_tw" role="2Oq$k0">
                          <ref role="3cqZAo" node="3b5oxbT8$Ap" resolve="modules" />
                        </node>
                        <node concept="UnYns" id="3b5oxbT8_tx" role="2OqNvi">
                          <node concept="3uibUv" id="3b5oxbT8_ty" role="UnYnz">
                            <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
                          </node>
                        </node>
                      </node>
                    </node>
                  </node>
                  <node concept="3cpWs8" id="3b5oxbT9cW$" role="3cqZAp">
                    <node concept="3cpWsn" id="3b5oxbT9cW_" role="3cpWs9">
                      <property role="TrG5h" value="outputDir" />
                      <node concept="3uibUv" id="3b5oxbT8P_l" role="1tU5fm">
                        <ref role="3uigEE" to="guwi:~File" resolve="File" />
                      </node>
                      <node concept="2ShNRf" id="3b5oxbT9cWA" role="33vP2m">
                        <node concept="1pGfFk" id="3b5oxbT9cWB" role="2ShVmc">
                          <ref role="37wK5l" to="guwi:~File.&lt;init&gt;(java.lang.String)" resolve="File" />
                          <node concept="Xl_RD" id="3b5oxbT9cWC" role="37wK5m">
                            <property role="Xl_RC" value="exported-languages" />
                          </node>
                        </node>
                      </node>
                    </node>
                  </node>
                  <node concept="3clFbF" id="TKTYk$fJpJ" role="3cqZAp">
                    <node concept="2OqwBi" id="TKTYk$fJFF" role="3clFbG">
                      <node concept="37vLTw" id="TKTYk$fJpH" role="2Oq$k0">
                        <ref role="3cqZAo" node="3b5oxbT9cW_" resolve="outputDir" />
                      </node>
                      <node concept="liA8E" id="TKTYk$fJZ3" role="2OqNvi">
                        <ref role="37wK5l" to="guwi:~File.mkdirs()" resolve="mkdirs" />
                      </node>
                    </node>
                  </node>
                  <node concept="3cpWs8" id="3b5oxbT8Pzi" role="3cqZAp">
                    <node concept="3cpWsn" id="3b5oxbT8Pzj" role="3cpWs9">
                      <property role="TrG5h" value="exporter" />
                      <node concept="3uibUv" id="3b5oxbT8Pum" role="1tU5fm">
                        <ref role="3uigEE" node="3Fg0S50gerF" resolve="MPSMetaModelExporter" />
                      </node>
                      <node concept="2ShNRf" id="3b5oxbT8Pzk" role="33vP2m">
                        <node concept="1pGfFk" id="3b5oxbT8Pzl" role="2ShVmc">
                          <ref role="37wK5l" node="3Fg0S50hc1U" resolve="MPSMetaModelExporter" />
                          <node concept="37vLTw" id="3b5oxbT9cWD" role="37wK5m">
                            <ref role="3cqZAo" node="3b5oxbT9cW_" resolve="outputDir" />
                          </node>
                        </node>
                      </node>
                    </node>
                  </node>
                  <node concept="2Gpval" id="3b5oxbT8PLJ" role="3cqZAp">
                    <node concept="2GrKxI" id="3b5oxbT8PLL" role="2Gsz3X">
                      <property role="TrG5h" value="language" />
                    </node>
                    <node concept="37vLTw" id="3b5oxbT8PRF" role="2GsD0m">
                      <ref role="3cqZAo" node="3b5oxbT8_tu" resolve="languages" />
                    </node>
                    <node concept="3clFbS" id="3b5oxbT8PLP" role="2LFqv$">
                      <node concept="3clFbF" id="3b5oxbT8Q16" role="3cqZAp">
                        <node concept="2OqwBi" id="3b5oxbT8Q72" role="3clFbG">
                          <node concept="37vLTw" id="3b5oxbT8Q15" role="2Oq$k0">
                            <ref role="3cqZAo" node="3b5oxbT8Pzj" resolve="exporter" />
                          </node>
                          <node concept="liA8E" id="3b5oxbT8Qfm" role="2OqNvi">
                            <ref role="37wK5l" node="3Fg0S50ge_5" resolve="exportLanguage" />
                            <node concept="2GrUjf" id="3b5oxbT8QpI" role="37wK5m">
                              <ref role="2Gs0qQ" node="3b5oxbT8PLL" resolve="language" />
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
      <node concept="37vLTG" id="4ZsvGZutXBI" role="3clF46">
        <property role="TrG5h" value="ideaEnvironment" />
        <node concept="3uibUv" id="4ZsvGZuHVCd" role="1tU5fm">
          <ref role="3uigEE" to="79ha:HKKzfMjqRV" resolve="Environment" />
        </node>
      </node>
      <node concept="3cqZAl" id="3b5oxbT8uLh" role="3clF45" />
      <node concept="3Tm1VV" id="3b5oxbT8uLi" role="1B3o_S" />
    </node>
    <node concept="2YIFZL" id="3HNwGUL6LeT" role="jymVt">
      <property role="TrG5h" value="exportBoth" />
      <node concept="3clFbS" id="3HNwGUL6LeU" role="3clF47">
        <node concept="3clFbF" id="3HNwGUL6NOK" role="3cqZAp">
          <node concept="1rXfSq" id="3HNwGUL6NOJ" role="3clFbG">
            <ref role="37wK5l" node="3b5oxbTadzA" resolve="exportLanguages" />
            <node concept="37vLTw" id="3HNwGUL6NQK" role="37wK5m">
              <ref role="3cqZAo" node="3HNwGUL6LfJ" resolve="ideaEnvironment" />
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="3HNwGUL6NR_" role="3cqZAp">
          <node concept="1rXfSq" id="3HNwGUL6NRA" role="3clFbG">
            <ref role="37wK5l" node="7jUShhopd_G" resolve="exportModules" />
            <node concept="37vLTw" id="3HNwGUL6NRB" role="37wK5m">
              <ref role="3cqZAo" node="3HNwGUL6LfJ" resolve="ideaEnvironment" />
            </node>
          </node>
        </node>
      </node>
      <node concept="37vLTG" id="3HNwGUL6LfJ" role="3clF46">
        <property role="TrG5h" value="ideaEnvironment" />
        <node concept="3uibUv" id="3HNwGUL6LfK" role="1tU5fm">
          <ref role="3uigEE" to="79ha:HKKzfMjqRV" resolve="Environment" />
        </node>
      </node>
      <node concept="3cqZAl" id="3HNwGUL6LfL" role="3clF45" />
      <node concept="3Tm1VV" id="3HNwGUL6LfM" role="1B3o_S" />
    </node>
    <node concept="2YIFZL" id="7jUShhopd_G" role="jymVt">
      <property role="TrG5h" value="exportModules" />
      <node concept="3clFbS" id="7jUShhopd_H" role="3clF47">
        <node concept="3cpWs8" id="3HNwGULbf4f" role="3cqZAp">
          <node concept="3cpWsn" id="3HNwGULbf4g" role="3cpWs9">
            <property role="TrG5h" value="filter" />
            <node concept="17QB3L" id="3HNwGULbfhe" role="1tU5fm" />
            <node concept="2YIFZM" id="3HNwGULbf4h" role="33vP2m">
              <ref role="37wK5l" to="wyt6:~System.getProperty(java.lang.String)" resolve="getProperty" />
              <ref role="1Pybhc" to="wyt6:~System" resolve="System" />
              <node concept="Xl_RD" id="3HNwGULbf4i" role="37wK5m">
                <property role="Xl_RC" value="modelix.export.includedModules" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="3HNwGULc70J" role="3cqZAp">
          <node concept="2OqwBi" id="3HNwGULc70G" role="3clFbG">
            <node concept="10M0yZ" id="3HNwGULc70H" role="2Oq$k0">
              <ref role="1PxDUh" to="wyt6:~System" resolve="System" />
              <ref role="3cqZAo" to="wyt6:~System.out" resolve="out" />
            </node>
            <node concept="liA8E" id="3HNwGULc70I" role="2OqNvi">
              <ref role="37wK5l" to="guwi:~PrintStream.println(java.lang.String)" resolve="println" />
              <node concept="3cpWs3" id="3HNwGULc7OK" role="37wK5m">
                <node concept="37vLTw" id="3HNwGULc7P5" role="3uHU7w">
                  <ref role="3cqZAo" node="3HNwGULbf4g" resolve="filter" />
                </node>
                <node concept="Xl_RD" id="3HNwGULc7fX" role="3uHU7B">
                  <property role="Xl_RC" value="modules filter: " />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbJ" id="3HNwGULboF3" role="3cqZAp">
          <node concept="3clFbS" id="3HNwGULboF5" role="3clFbx">
            <node concept="3cpWs6" id="3HNwGULbpEC" role="3cqZAp" />
          </node>
          <node concept="3clFbC" id="3HNwGULbpj2" role="3clFbw">
            <node concept="10Nm6u" id="3HNwGULbpE2" role="3uHU7w" />
            <node concept="37vLTw" id="3HNwGULboRg" role="3uHU7B">
              <ref role="3cqZAo" node="3HNwGULbf4g" resolve="filter" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="3HNwGULbhis" role="3cqZAp">
          <node concept="3cpWsn" id="3HNwGULbhit" role="3cpWs9">
            <property role="TrG5h" value="filters" />
            <node concept="_YKpA" id="3HNwGULbhgU" role="1tU5fm">
              <node concept="3uibUv" id="3HNwGULbkQh" role="_ZDj9">
                <ref role="3uigEE" to="wyt6:~String" resolve="String" />
              </node>
            </node>
            <node concept="2OqwBi" id="3HNwGULbhiu" role="33vP2m">
              <node concept="2OqwBi" id="3HNwGULbi5_" role="2Oq$k0">
                <node concept="2OqwBi" id="3HNwGULbhiv" role="2Oq$k0">
                  <node concept="2OqwBi" id="3HNwGULbhiw" role="2Oq$k0">
                    <node concept="37vLTw" id="3HNwGULbhix" role="2Oq$k0">
                      <ref role="3cqZAo" node="3HNwGULbf4g" resolve="filter" />
                    </node>
                    <node concept="liA8E" id="3HNwGULbhiy" role="2OqNvi">
                      <ref role="37wK5l" to="wyt6:~String.split(java.lang.String)" resolve="split" />
                      <node concept="Xl_RD" id="3HNwGULbhiz" role="37wK5m">
                        <property role="Xl_RC" value="," />
                      </node>
                    </node>
                  </node>
                  <node concept="39bAoz" id="3HNwGULbhi$" role="2OqNvi" />
                </node>
                <node concept="3zZkjj" id="3HNwGULbiMO" role="2OqNvi">
                  <node concept="1bVj0M" id="3HNwGULbiMQ" role="23t8la">
                    <node concept="3clFbS" id="3HNwGULbiMR" role="1bW5cS">
                      <node concept="3clFbF" id="3HNwGULbiS8" role="3cqZAp">
                        <node concept="2OqwBi" id="3HNwGULbjig" role="3clFbG">
                          <node concept="37vLTw" id="3HNwGULbiS7" role="2Oq$k0">
                            <ref role="3cqZAo" node="3HNwGULbiMS" resolve="it" />
                          </node>
                          <node concept="17RvpY" id="3HNwGULbjTI" role="2OqNvi" />
                        </node>
                      </node>
                    </node>
                    <node concept="Rh6nW" id="3HNwGULbiMS" role="1bW2Oz">
                      <property role="TrG5h" value="it" />
                      <node concept="2jxLKc" id="3HNwGULbiMT" role="1tU5fm" />
                    </node>
                  </node>
                </node>
              </node>
              <node concept="ANE8D" id="3HNwGULbhi_" role="2OqNvi" />
            </node>
          </node>
        </node>
        <node concept="3clFbJ" id="3HNwGULbpTo" role="3cqZAp">
          <node concept="3clFbS" id="3HNwGULbpTq" role="3clFbx">
            <node concept="3cpWs6" id="3HNwGULbrNG" role="3cqZAp" />
          </node>
          <node concept="2OqwBi" id="3HNwGULbqWm" role="3clFbw">
            <node concept="37vLTw" id="3HNwGULbq5U" role="2Oq$k0">
              <ref role="3cqZAo" node="3HNwGULbhit" resolve="filters" />
            </node>
            <node concept="1v1jN8" id="3HNwGULbrN5" role="2OqNvi" />
          </node>
        </node>
        <node concept="3clFbH" id="3HNwGULbeK3" role="3cqZAp" />
        <node concept="3cpWs8" id="7jUShhopd_I" role="3cqZAp">
          <node concept="3cpWsn" id="7jUShhopd_J" role="3cpWs9">
            <property role="TrG5h" value="repo" />
            <node concept="3uibUv" id="7jUShhopd_K" role="1tU5fm">
              <ref role="3uigEE" to="w1kc:~MPSModuleRepository" resolve="MPSModuleRepository" />
            </node>
            <node concept="2YIFZM" id="7jUShhopd_L" role="33vP2m">
              <ref role="37wK5l" to="w1kc:~MPSModuleRepository.getInstance()" resolve="getInstance" />
              <ref role="1Pybhc" to="w1kc:~MPSModuleRepository" resolve="MPSModuleRepository" />
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="7jUShhopd_M" role="3cqZAp">
          <node concept="2OqwBi" id="7jUShhopd_N" role="3clFbG">
            <node concept="2OqwBi" id="7jUShhopd_O" role="2Oq$k0">
              <node concept="37vLTw" id="7jUShhopd_P" role="2Oq$k0">
                <ref role="3cqZAo" node="7jUShhopd_J" resolve="repo" />
              </node>
              <node concept="liA8E" id="7jUShhopd_Q" role="2OqNvi">
                <ref role="37wK5l" to="w1kc:~MPSModuleRepository.getModelAccess()" resolve="getModelAccess" />
              </node>
            </node>
            <node concept="liA8E" id="7jUShhopd_R" role="2OqNvi">
              <ref role="37wK5l" to="lui2:~ModelAccess.runReadAction(java.lang.Runnable)" resolve="runReadAction" />
              <node concept="1bVj0M" id="7jUShhopd_S" role="37wK5m">
                <node concept="3clFbS" id="7jUShhopd_T" role="1bW5cS">
                  <node concept="3cpWs8" id="7jUShhopd_U" role="3cqZAp">
                    <node concept="3cpWsn" id="7jUShhopd_V" role="3cpWs9">
                      <property role="TrG5h" value="modules" />
                      <node concept="A3Dl8" id="7jUShhopd_W" role="1tU5fm">
                        <node concept="3uibUv" id="7jUShhopd_X" role="A3Ik2">
                          <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
                        </node>
                      </node>
                      <node concept="2OqwBi" id="7jUShhopd_Y" role="33vP2m">
                        <node concept="37vLTw" id="7jUShhopd_Z" role="2Oq$k0">
                          <ref role="3cqZAo" node="7jUShhopd_J" resolve="repo" />
                        </node>
                        <node concept="liA8E" id="7jUShhopdA0" role="2OqNvi">
                          <ref role="37wK5l" to="w1kc:~MPSModuleRepository.getModules()" resolve="getModules" />
                        </node>
                      </node>
                    </node>
                  </node>
                  <node concept="3cpWs8" id="7jUShhopdA9" role="3cqZAp">
                    <node concept="3cpWsn" id="7jUShhopdAa" role="3cpWs9">
                      <property role="TrG5h" value="outputDir" />
                      <node concept="3uibUv" id="7jUShhopdAb" role="1tU5fm">
                        <ref role="3uigEE" to="guwi:~File" resolve="File" />
                      </node>
                      <node concept="2ShNRf" id="7jUShhopdAc" role="33vP2m">
                        <node concept="1pGfFk" id="7jUShhopdAd" role="2ShVmc">
                          <ref role="37wK5l" to="guwi:~File.&lt;init&gt;(java.lang.String)" resolve="File" />
                          <node concept="Xl_RD" id="7jUShhopdAe" role="37wK5m">
                            <property role="Xl_RC" value="exported-modules" />
                          </node>
                        </node>
                      </node>
                    </node>
                  </node>
                  <node concept="3clFbF" id="7jUShhopdAf" role="3cqZAp">
                    <node concept="2OqwBi" id="7jUShhopdAg" role="3clFbG">
                      <node concept="37vLTw" id="7jUShhopdAh" role="2Oq$k0">
                        <ref role="3cqZAo" node="7jUShhopdAa" resolve="outputDir" />
                      </node>
                      <node concept="liA8E" id="7jUShhopdAi" role="2OqNvi">
                        <ref role="37wK5l" to="guwi:~File.mkdirs()" resolve="mkdirs" />
                      </node>
                    </node>
                  </node>
                  <node concept="3cpWs8" id="7jUShhopdAj" role="3cqZAp">
                    <node concept="3cpWsn" id="7jUShhopdAk" role="3cpWs9">
                      <property role="TrG5h" value="exporter" />
                      <node concept="3uibUv" id="7jUShhopdAl" role="1tU5fm">
                        <ref role="3uigEE" node="6bQHiZUll2y" resolve="MPSModelExporter" />
                      </node>
                      <node concept="2ShNRf" id="7jUShhopdAm" role="33vP2m">
                        <node concept="1pGfFk" id="7jUShhopdAn" role="2ShVmc">
                          <ref role="37wK5l" node="6bQHiZUll2R" resolve="MPSModelExporter" />
                          <node concept="37vLTw" id="7jUShhopdAo" role="37wK5m">
                            <ref role="3cqZAo" node="7jUShhopdAa" resolve="outputDir" />
                          </node>
                        </node>
                      </node>
                    </node>
                  </node>
                  <node concept="2Gpval" id="7jUShhopdAp" role="3cqZAp">
                    <node concept="2GrKxI" id="7jUShhopdAq" role="2Gsz3X">
                      <property role="TrG5h" value="module" />
                    </node>
                    <node concept="37vLTw" id="7jUShhopdAr" role="2GsD0m">
                      <ref role="3cqZAo" node="7jUShhopd_V" resolve="modules" />
                    </node>
                    <node concept="3clFbS" id="7jUShhopdAs" role="2LFqv$">
                      <node concept="3cpWs8" id="3HNwGULblG_" role="3cqZAp">
                        <node concept="3cpWsn" id="3HNwGULblGA" role="3cpWs9">
                          <property role="TrG5h" value="moduleName" />
                          <node concept="17QB3L" id="3HNwGULblQI" role="1tU5fm" />
                          <node concept="2OqwBi" id="3HNwGULblGB" role="33vP2m">
                            <node concept="2GrUjf" id="3HNwGULblGC" role="2Oq$k0">
                              <ref role="2Gs0qQ" node="7jUShhopdAq" resolve="module" />
                            </node>
                            <node concept="liA8E" id="3HNwGULblGD" role="2OqNvi">
                              <ref role="37wK5l" to="lui2:~SModule.getModuleName()" resolve="getModuleName" />
                            </node>
                          </node>
                        </node>
                      </node>
                      <node concept="3clFbJ" id="3HNwGULbl5H" role="3cqZAp">
                        <node concept="3clFbS" id="3HNwGULbl5J" role="3clFbx">
                          <node concept="3clFbF" id="7jUShhopdAt" role="3cqZAp">
                            <node concept="2OqwBi" id="7jUShhopdAu" role="3clFbG">
                              <node concept="37vLTw" id="7jUShhopdAv" role="2Oq$k0">
                                <ref role="3cqZAo" node="7jUShhopdAk" resolve="exporter" />
                              </node>
                              <node concept="liA8E" id="7jUShhopdAw" role="2OqNvi">
                                <ref role="37wK5l" node="7jUShhor5dr" resolve="exportModule" />
                                <node concept="2GrUjf" id="7jUShhorCXk" role="37wK5m">
                                  <ref role="2Gs0qQ" node="7jUShhopdAq" resolve="module" />
                                </node>
                              </node>
                            </node>
                          </node>
                        </node>
                        <node concept="2OqwBi" id="3HNwGULbnz9" role="3clFbw">
                          <node concept="37vLTw" id="3HNwGULblGE" role="2Oq$k0">
                            <ref role="3cqZAo" node="3HNwGULbhit" resolve="filters" />
                          </node>
                          <node concept="2HwmR7" id="3HNwGULbol3" role="2OqNvi">
                            <node concept="1bVj0M" id="3HNwGULbol5" role="23t8la">
                              <node concept="3clFbS" id="3HNwGULbol6" role="1bW5cS">
                                <node concept="3clFbF" id="3HNwGULbrOm" role="3cqZAp">
                                  <node concept="22lmx$" id="3HNwGULbub4" role="3clFbG">
                                    <node concept="2OqwBi" id="3HNwGULbu$5" role="3uHU7w">
                                      <node concept="37vLTw" id="3HNwGULbuk$" role="2Oq$k0">
                                        <ref role="3cqZAo" node="3HNwGULblGA" resolve="moduleName" />
                                      </node>
                                      <node concept="liA8E" id="3HNwGULbuZs" role="2OqNvi">
                                        <ref role="37wK5l" to="wyt6:~String.startsWith(java.lang.String)" resolve="startsWith" />
                                        <node concept="3K4zz7" id="3HNwGULbxzG" role="37wK5m">
                                          <node concept="37vLTw" id="3HNwGULbxGt" role="3K4E3e">
                                            <ref role="3cqZAo" node="3HNwGULbol7" resolve="it" />
                                          </node>
                                          <node concept="2OqwBi" id="3HNwGULbwkB" role="3K4Cdx">
                                            <node concept="37vLTw" id="3HNwGULbvZA" role="2Oq$k0">
                                              <ref role="3cqZAo" node="3HNwGULbol7" resolve="it" />
                                            </node>
                                            <node concept="liA8E" id="3HNwGULbwSI" role="2OqNvi">
                                              <ref role="37wK5l" to="wyt6:~String.endsWith(java.lang.String)" resolve="endsWith" />
                                              <node concept="Xl_RD" id="3HNwGULbx4s" role="37wK5m">
                                                <property role="Xl_RC" value="." />
                                              </node>
                                            </node>
                                          </node>
                                          <node concept="3cpWs3" id="3HNwGULbvul" role="3K4GZi">
                                            <node concept="Xl_RD" id="3HNwGULbvuA" role="3uHU7w">
                                              <property role="Xl_RC" value="." />
                                            </node>
                                            <node concept="37vLTw" id="3HNwGULbv71" role="3uHU7B">
                                              <ref role="3cqZAo" node="3HNwGULbol7" resolve="it" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                    <node concept="17R0WA" id="3HNwGULbtGw" role="3uHU7B">
                                      <node concept="37vLTw" id="3HNwGULbrOl" role="3uHU7B">
                                        <ref role="3cqZAo" node="3HNwGULblGA" resolve="moduleName" />
                                      </node>
                                      <node concept="37vLTw" id="3HNwGULbtNj" role="3uHU7w">
                                        <ref role="3cqZAo" node="3HNwGULbol7" resolve="it" />
                                      </node>
                                    </node>
                                  </node>
                                </node>
                              </node>
                              <node concept="Rh6nW" id="3HNwGULbol7" role="1bW2Oz">
                                <property role="TrG5h" value="it" />
                                <node concept="2jxLKc" id="3HNwGULbol8" role="1tU5fm" />
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
      <node concept="37vLTG" id="7jUShhopdAy" role="3clF46">
        <property role="TrG5h" value="ideaEnvironment" />
        <node concept="3uibUv" id="7jUShhopdAz" role="1tU5fm">
          <ref role="3uigEE" to="79ha:HKKzfMjqRV" resolve="Environment" />
        </node>
      </node>
      <node concept="3cqZAl" id="7jUShhopdA$" role="3clF45" />
      <node concept="3Tm1VV" id="7jUShhopdA_" role="1B3o_S" />
    </node>
    <node concept="3Tm1VV" id="3b5oxbT8uG$" role="1B3o_S" />
  </node>
  <node concept="312cEu" id="3Fg0S50gerF">
    <property role="TrG5h" value="MPSMetaModelExporter" />
    <node concept="312cEg" id="3Fg0S50geDN" role="jymVt">
      <property role="TrG5h" value="outputFolder" />
      <node concept="3Tm6S6" id="3Fg0S50geDO" role="1B3o_S" />
      <node concept="3uibUv" id="3Fg0S50geNL" role="1tU5fm">
        <ref role="3uigEE" to="guwi:~File" resolve="File" />
      </node>
    </node>
    <node concept="312cEg" id="3Fg0S50geRS" role="jymVt">
      <property role="TrG5h" value="processedLanguages" />
      <node concept="3Tm6S6" id="3Fg0S50geRT" role="1B3o_S" />
      <node concept="2hMVRd" id="3Fg0S50geVz" role="1tU5fm">
        <node concept="3uibUv" id="3Fg0S50gf63" role="2hN53Y">
          <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
        </node>
      </node>
      <node concept="2ShNRf" id="3Fg0S50gq1F" role="33vP2m">
        <node concept="2i4dXS" id="3Fg0S50gpUc" role="2ShVmc">
          <node concept="3uibUv" id="3Fg0S50gpUd" role="HW$YZ">
            <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
          </node>
        </node>
      </node>
    </node>
    <node concept="312cEg" id="18fUb1nwRKI" role="jymVt">
      <property role="TrG5h" value="producedData" />
      <node concept="3Tm6S6" id="18fUb1nwRKJ" role="1B3o_S" />
      <node concept="3rvAFt" id="18fUb1nwSIY" role="1tU5fm">
        <node concept="3uibUv" id="18fUb1nwSQc" role="3rvQeY">
          <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
        </node>
        <node concept="3uibUv" id="18fUb1nwSXD" role="3rvSg0">
          <ref role="3uigEE" to="sgfj:~LanguageData" resolve="LanguageData" />
        </node>
      </node>
      <node concept="2ShNRf" id="18fUb1nwT2t" role="33vP2m">
        <node concept="3rGOSV" id="18fUb1nwT20" role="2ShVmc">
          <node concept="3uibUv" id="18fUb1nwT21" role="3rHrn6">
            <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
          </node>
          <node concept="3uibUv" id="18fUb1nwT22" role="3rHtpV">
            <ref role="3uigEE" to="sgfj:~LanguageData" resolve="LanguageData" />
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="3Fg0S50geAj" role="jymVt" />
    <node concept="3clFbW" id="3Fg0S50hc1U" role="jymVt">
      <node concept="3cqZAl" id="3Fg0S50hc1V" role="3clF45" />
      <node concept="3Tm1VV" id="3Fg0S50hc1W" role="1B3o_S" />
      <node concept="3clFbS" id="3Fg0S50hc1Y" role="3clF47">
        <node concept="3clFbF" id="3Fg0S50hc22" role="3cqZAp">
          <node concept="37vLTI" id="3Fg0S50hc24" role="3clFbG">
            <node concept="2OqwBi" id="3Fg0S50hc28" role="37vLTJ">
              <node concept="Xjq3P" id="3Fg0S50hc29" role="2Oq$k0" />
              <node concept="2OwXpG" id="3Fg0S50hc2a" role="2OqNvi">
                <ref role="2Oxat5" node="3Fg0S50geDN" resolve="outputFolder" />
              </node>
            </node>
            <node concept="37vLTw" id="3Fg0S50hc2b" role="37vLTx">
              <ref role="3cqZAo" node="3Fg0S50hc21" resolve="outputFolder" />
            </node>
          </node>
        </node>
      </node>
      <node concept="37vLTG" id="3Fg0S50hc21" role="3clF46">
        <property role="TrG5h" value="outputFolder" />
        <node concept="3uibUv" id="3Fg0S50hc20" role="1tU5fm">
          <ref role="3uigEE" to="guwi:~File" resolve="File" />
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="27LOqMUYLWK" role="jymVt" />
    <node concept="3clFb_" id="27LOqMUYMV1" role="jymVt">
      <property role="TrG5h" value="getNumLanguages" />
      <node concept="10Oyi0" id="27LOqMUYPmO" role="3clF45" />
      <node concept="3Tm1VV" id="27LOqMUYMV4" role="1B3o_S" />
      <node concept="3clFbS" id="27LOqMUYMV5" role="3clF47">
        <node concept="3clFbF" id="27LOqMUYQJ5" role="3cqZAp">
          <node concept="2OqwBi" id="27LOqMUYRvS" role="3clFbG">
            <node concept="37vLTw" id="27LOqMUYQJ4" role="2Oq$k0">
              <ref role="3cqZAo" node="3Fg0S50geRS" resolve="processedLanguages" />
            </node>
            <node concept="34oBXx" id="27LOqMUYSqq" role="2OqNvi" />
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="3Fg0S50hdjl" role="jymVt" />
    <node concept="3clFb_" id="2pErVStxHxp" role="jymVt">
      <property role="TrG5h" value="exportLanguage" />
      <node concept="37vLTG" id="2pErVStxL$O" role="3clF46">
        <property role="TrG5h" value="nodeInLanguage" />
        <node concept="3Tqbb2" id="2pErVStxMOu" role="1tU5fm" />
      </node>
      <node concept="3cqZAl" id="2pErVStxHxr" role="3clF45" />
      <node concept="3Tm6S6" id="2pErVStxIWX" role="1B3o_S" />
      <node concept="3clFbS" id="2pErVStxHxt" role="3clF47">
        <node concept="3clFbJ" id="2pErVStxPji" role="3cqZAp">
          <node concept="2OqwBi" id="2pErVStxP__" role="3clFbw">
            <node concept="37vLTw" id="2pErVStxPkq" role="2Oq$k0">
              <ref role="3cqZAo" node="2pErVStxL$O" resolve="nodeInLanguage" />
            </node>
            <node concept="3w_OXm" id="2pErVStxPJx" role="2OqNvi" />
          </node>
          <node concept="3clFbS" id="2pErVStxPjk" role="3clFbx">
            <node concept="3cpWs6" id="2pErVStxPYG" role="3cqZAp" />
          </node>
        </node>
        <node concept="3cpWs8" id="2pErVStxR0G" role="3cqZAp">
          <node concept="3cpWsn" id="2pErVStxR0H" role="3cpWs9">
            <property role="TrG5h" value="model" />
            <node concept="3uibUv" id="2pErVStxQPq" role="1tU5fm">
              <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
            </node>
            <node concept="2JrnkZ" id="2pErVStxR0I" role="33vP2m">
              <node concept="2OqwBi" id="2pErVStxR0J" role="2JrQYb">
                <node concept="37vLTw" id="2pErVStxR0K" role="2Oq$k0">
                  <ref role="3cqZAo" node="2pErVStxL$O" resolve="nodeInLanguage" />
                </node>
                <node concept="I4A8Y" id="2pErVStxR0L" role="2OqNvi" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbJ" id="2pErVStxR7J" role="3cqZAp">
          <node concept="3clFbS" id="2pErVStxR7L" role="3clFbx">
            <node concept="3cpWs6" id="2pErVStxRov" role="3cqZAp" />
          </node>
          <node concept="3clFbC" id="2pErVStxRgV" role="3clFbw">
            <node concept="10Nm6u" id="2pErVStxRk$" role="3uHU7w" />
            <node concept="37vLTw" id="2pErVStxR9f" role="3uHU7B">
              <ref role="3cqZAo" node="2pErVStxR0H" resolve="model" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="2pErVStxRYG" role="3cqZAp">
          <node concept="3cpWsn" id="2pErVStxRYH" role="3cpWs9">
            <property role="TrG5h" value="language" />
            <node concept="3uibUv" id="2pErVStxRKv" role="1tU5fm">
              <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
            </node>
            <node concept="0kSF2" id="2pErVStxRYI" role="33vP2m">
              <node concept="3uibUv" id="2pErVStxRYJ" role="0kSFW">
                <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
              </node>
              <node concept="2OqwBi" id="2pErVStxRYK" role="0kSFX">
                <node concept="37vLTw" id="2pErVStxRYL" role="2Oq$k0">
                  <ref role="3cqZAo" node="2pErVStxR0H" resolve="model" />
                </node>
                <node concept="liA8E" id="2pErVStxRYM" role="2OqNvi">
                  <ref role="37wK5l" to="mhbf:~SModel.getModule()" resolve="getModule" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbJ" id="2pErVStxSe9" role="3cqZAp">
          <node concept="3clFbS" id="2pErVStxSeb" role="3clFbx">
            <node concept="3cpWs6" id="2pErVStxSLk" role="3cqZAp" />
          </node>
          <node concept="3clFbC" id="2pErVStxSG_" role="3clFbw">
            <node concept="10Nm6u" id="2pErVStxSH6" role="3uHU7w" />
            <node concept="37vLTw" id="2pErVStxSga" role="3uHU7B">
              <ref role="3cqZAo" node="2pErVStxRYH" resolve="language" />
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="2pErVStxSTI" role="3cqZAp">
          <node concept="1rXfSq" id="2pErVStxSTF" role="3clFbG">
            <ref role="37wK5l" node="3Fg0S50ge_5" resolve="exportLanguage" />
            <node concept="37vLTw" id="2pErVStxTac" role="37wK5m">
              <ref role="3cqZAo" node="2pErVStxRYH" resolve="language" />
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="2pErVStxBpc" role="jymVt" />
    <node concept="3clFb_" id="3Fg0S50ge_5" role="jymVt">
      <property role="TrG5h" value="exportLanguage" />
      <node concept="37vLTG" id="3Fg0S50gf7e" role="3clF46">
        <property role="TrG5h" value="languageModule" />
        <node concept="3uibUv" id="3Fg0S50gfem" role="1tU5fm">
          <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
        </node>
      </node>
      <node concept="3cqZAl" id="3Fg0S50ge_7" role="3clF45" />
      <node concept="3Tm1VV" id="3Fg0S50ge_8" role="1B3o_S" />
      <node concept="3clFbS" id="3Fg0S50ge_9" role="3clF47">
        <node concept="3clFbJ" id="3Fg0S50gndt" role="3cqZAp">
          <node concept="3clFbS" id="3Fg0S50gndv" role="3clFbx">
            <node concept="3cpWs6" id="3Fg0S50gqeM" role="3cqZAp" />
          </node>
          <node concept="2OqwBi" id="3Fg0S50goSH" role="3clFbw">
            <node concept="37vLTw" id="3Fg0S50go6F" role="2Oq$k0">
              <ref role="3cqZAo" node="3Fg0S50geRS" resolve="processedLanguages" />
            </node>
            <node concept="3JPx81" id="3Fg0S50gpSG" role="2OqNvi">
              <node concept="37vLTw" id="3Fg0S50gq9Z" role="25WWJ7">
                <ref role="3cqZAo" node="3Fg0S50gf7e" resolve="languageModule" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="3Fg0S50h7M3" role="3cqZAp">
          <node concept="2OqwBi" id="3Fg0S50h9jZ" role="3clFbG">
            <node concept="37vLTw" id="3Fg0S50h7M1" role="2Oq$k0">
              <ref role="3cqZAo" node="3Fg0S50geRS" resolve="processedLanguages" />
            </node>
            <node concept="TSZUe" id="3Fg0S50haqJ" role="2OqNvi">
              <node concept="37vLTw" id="3Fg0S50haN5" role="25WWJ7">
                <ref role="3cqZAo" node="3Fg0S50gf7e" resolve="languageModule" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbH" id="3Fg0S50gO2k" role="3cqZAp" />
        <node concept="3cpWs8" id="3Fg0S50cQcZ" role="3cqZAp">
          <node concept="3cpWsn" id="3Fg0S50cQd0" role="3cpWs9">
            <property role="TrG5h" value="structureModel" />
            <node concept="3uibUv" id="3Fg0S50cQch" role="1tU5fm">
              <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
            </node>
            <node concept="2OqwBi" id="3Fg0S50cQd1" role="33vP2m">
              <node concept="Rm8GO" id="3Fg0S50cQd2" role="2Oq$k0">
                <ref role="Rm8GQ" to="w1kc:~LanguageAspect.STRUCTURE" resolve="STRUCTURE" />
                <ref role="1Px2BO" to="w1kc:~LanguageAspect" resolve="LanguageAspect" />
              </node>
              <node concept="liA8E" id="3Fg0S50cQd3" role="2OqNvi">
                <ref role="37wK5l" to="w1kc:~LanguageAspect.get(jetbrains.mps.smodel.Language)" resolve="get" />
                <node concept="37vLTw" id="3Fg0S50glL8" role="37wK5m">
                  <ref role="3cqZAo" node="3Fg0S50gf7e" resolve="languageModule" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="3Fg0S50cQzU" role="3cqZAp">
          <node concept="3cpWsn" id="3Fg0S50cQzV" role="3cpWs9">
            <property role="TrG5h" value="rootNodes" />
            <node concept="A3Dl8" id="3Fg0S50cQLu" role="1tU5fm">
              <node concept="3uibUv" id="3Fg0S50cQLw" role="A3Ik2">
                <ref role="3uigEE" to="mhbf:~SNode" resolve="SNode" />
              </node>
            </node>
            <node concept="2OqwBi" id="3Fg0S50cQzW" role="33vP2m">
              <node concept="37vLTw" id="3Fg0S50cQzX" role="2Oq$k0">
                <ref role="3cqZAo" node="3Fg0S50cQd0" resolve="structureModel" />
              </node>
              <node concept="liA8E" id="3Fg0S50cQzY" role="2OqNvi">
                <ref role="37wK5l" to="mhbf:~SModel.getRootNodes()" resolve="getRootNodes" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbH" id="3Fg0S50gNsX" role="3cqZAp" />
        <node concept="3cpWs8" id="3Fg0S50cWmO" role="3cqZAp">
          <node concept="3cpWsn" id="3Fg0S50cWmP" role="3cpWs9">
            <property role="TrG5h" value="concepts" />
            <node concept="_YKpA" id="3Fg0S50cYzK" role="1tU5fm">
              <node concept="3uibUv" id="3Fg0S50cYzM" role="_ZDj9">
                <ref role="3uigEE" to="sgfj:~ConceptData" resolve="ConceptData" />
              </node>
            </node>
            <node concept="2OqwBi" id="3Fg0S50cXMV" role="33vP2m">
              <node concept="2OqwBi" id="3Fg0S50cWmQ" role="2Oq$k0">
                <node concept="2OqwBi" id="3Fg0S50cWmR" role="2Oq$k0">
                  <node concept="37vLTw" id="3Fg0S50cWmS" role="2Oq$k0">
                    <ref role="3cqZAo" node="3Fg0S50cQzV" resolve="rootNodes" />
                  </node>
                  <node concept="v3k3i" id="3Fg0S50cWmT" role="2OqNvi">
                    <node concept="chp4Y" id="3Fg0S50cWmU" role="v3oSu">
                      <ref role="cht4Q" to="tpce:h0PkWnZ" resolve="AbstractConceptDeclaration" />
                    </node>
                  </node>
                </node>
                <node concept="3$u5V9" id="3Fg0S50cWmV" role="2OqNvi">
                  <node concept="1bVj0M" id="3Fg0S50cWmW" role="23t8la">
                    <node concept="3clFbS" id="3Fg0S50cWmX" role="1bW5cS">
                      <node concept="3cpWs8" id="3Fg0S50exti" role="3cqZAp">
                        <node concept="3cpWsn" id="3Fg0S50extj" role="3cpWs9">
                          <property role="TrG5h" value="properties" />
                          <node concept="_YKpA" id="3Fg0S50expq" role="1tU5fm">
                            <node concept="3uibUv" id="3Fg0S50expt" role="_ZDj9">
                              <ref role="3uigEE" to="sgfj:~PropertyData" resolve="PropertyData" />
                            </node>
                          </node>
                          <node concept="2OqwBi" id="3Fg0S50extk" role="33vP2m">
                            <node concept="2OqwBi" id="3Fg0S50extl" role="2Oq$k0">
                              <node concept="2OqwBi" id="3Fg0S50extm" role="2Oq$k0">
                                <node concept="37vLTw" id="3Fg0S50extn" role="2Oq$k0">
                                  <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                                </node>
                                <node concept="3Tsc0h" id="3Fg0S50exto" role="2OqNvi">
                                  <ref role="3TtcxE" to="tpce:f_TKVDG" resolve="propertyDeclaration" />
                                </node>
                              </node>
                              <node concept="3$u5V9" id="3Fg0S50extp" role="2OqNvi">
                                <node concept="1bVj0M" id="3Fg0S50extq" role="23t8la">
                                  <node concept="3clFbS" id="3Fg0S50extr" role="1bW5cS">
                                    <node concept="3cpWs8" id="AjwKkD6CmE" role="3cqZAp">
                                      <node concept="3cpWsn" id="AjwKkD6CmF" role="3cpWs9">
                                        <property role="TrG5h" value="type" />
                                        <node concept="3uibUv" id="AjwKkD6B65" role="1tU5fm">
                                          <ref role="3uigEE" to="sgfj:~PropertyType" resolve="PropertyType" />
                                        </node>
                                        <node concept="2ShNRf" id="4zSRxm72Iaa" role="33vP2m">
                                          <node concept="1pGfFk" id="4zSRxm72Jz0" role="2ShVmc">
                                            <ref role="37wK5l" to="sgfj:~PrimitivePropertyType.&lt;init&gt;(org.modelix.model.data.Primitive)" resolve="PrimitivePropertyType" />
                                            <node concept="Rm8GO" id="1lNY4J8UNVd" role="37wK5m">
                                              <ref role="Rm8GQ" to="sgfj:~Primitive.STRING" resolve="STRING" />
                                              <ref role="1Px2BO" to="sgfj:~Primitive" resolve="Primitive" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                    <node concept="3clFbJ" id="AjwKkD6HlC" role="3cqZAp">
                                      <node concept="3clFbS" id="AjwKkD6HlE" role="3clFbx">
                                        <node concept="3clFbF" id="AjwKkD74tJ" role="3cqZAp">
                                          <node concept="37vLTI" id="AjwKkD76zq" role="3clFbG">
                                            <node concept="37vLTw" id="AjwKkD74tH" role="37vLTJ">
                                              <ref role="3cqZAo" node="AjwKkD6CmF" resolve="type" />
                                            </node>
                                            <node concept="2ShNRf" id="1lNY4J8USdQ" role="37vLTx">
                                              <node concept="1pGfFk" id="1lNY4J8UWY4" role="2ShVmc">
                                                <ref role="37wK5l" to="sgfj:~PrimitivePropertyType.&lt;init&gt;(org.modelix.model.data.Primitive)" resolve="PrimitivePropertyType" />
                                                <node concept="Rm8GO" id="1lNY4J8V4SK" role="37wK5m">
                                                  <ref role="Rm8GQ" to="sgfj:~Primitive.INT" resolve="INT" />
                                                  <ref role="1Px2BO" to="sgfj:~Primitive" resolve="Primitive" />
                                                </node>
                                              </node>
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                      <node concept="17R0WA" id="AjwKkD6SFD" role="3clFbw">
                                        <node concept="2tJFMh" id="AjwKkD6Upn" role="3uHU7w">
                                          <node concept="ZC_QK" id="AjwKkD6V_V" role="2tJFKM">
                                            <ref role="2aWVGs" to="tpck:fKAQMTA" resolve="integer" />
                                          </node>
                                        </node>
                                        <node concept="2OqwBi" id="AjwKkD6Ya8" role="3uHU7B">
                                          <node concept="2OqwBi" id="AjwKkD6KWb" role="2Oq$k0">
                                            <node concept="37vLTw" id="AjwKkD6Jll" role="2Oq$k0">
                                              <ref role="3cqZAo" node="3Fg0S50extz" resolve="it" />
                                            </node>
                                            <node concept="3TrEf2" id="AjwKkD6MX6" role="2OqNvi">
                                              <ref role="3Tt5mk" to="tpce:fKAX2Z_" resolve="dataType" />
                                            </node>
                                          </node>
                                          <node concept="iZEcu" id="AjwKkD73tR" role="2OqNvi" />
                                        </node>
                                      </node>
                                      <node concept="3eNFk2" id="AjwKkD7c1W" role="3eNLev">
                                        <node concept="3clFbS" id="AjwKkD7c1Y" role="3eOfB_">
                                          <node concept="3clFbF" id="AjwKkD7iDK" role="3cqZAp">
                                            <node concept="37vLTI" id="AjwKkD7iDL" role="3clFbG">
                                              <node concept="37vLTw" id="AjwKkD7iDN" role="37vLTJ">
                                                <ref role="3cqZAo" node="AjwKkD6CmF" resolve="type" />
                                              </node>
                                              <node concept="2ShNRf" id="1lNY4J8V8$a" role="37vLTx">
                                                <node concept="1pGfFk" id="1lNY4J8VbiR" role="2ShVmc">
                                                  <ref role="37wK5l" to="sgfj:~PrimitivePropertyType.&lt;init&gt;(org.modelix.model.data.Primitive)" resolve="PrimitivePropertyType" />
                                                  <node concept="Rm8GO" id="1lNY4J8Vf5j" role="37wK5m">
                                                    <ref role="Rm8GQ" to="sgfj:~Primitive.BOOLEAN" resolve="BOOLEAN" />
                                                    <ref role="1Px2BO" to="sgfj:~Primitive" resolve="Primitive" />
                                                  </node>
                                                </node>
                                              </node>
                                            </node>
                                          </node>
                                        </node>
                                        <node concept="17R0WA" id="AjwKkD7e0u" role="3eO9$A">
                                          <node concept="2tJFMh" id="AjwKkD7e0v" role="3uHU7w">
                                            <node concept="ZC_QK" id="AjwKkD7e0w" role="2tJFKM">
                                              <ref role="2aWVGs" to="tpck:fKAQMTB" resolve="boolean" />
                                            </node>
                                          </node>
                                          <node concept="2OqwBi" id="AjwKkD7e0x" role="3uHU7B">
                                            <node concept="2OqwBi" id="AjwKkD7e0y" role="2Oq$k0">
                                              <node concept="37vLTw" id="AjwKkD7e0z" role="2Oq$k0">
                                                <ref role="3cqZAo" node="3Fg0S50extz" resolve="it" />
                                              </node>
                                              <node concept="3TrEf2" id="AjwKkD7e0$" role="2OqNvi">
                                                <ref role="3Tt5mk" to="tpce:fKAX2Z_" resolve="dataType" />
                                              </node>
                                            </node>
                                            <node concept="iZEcu" id="AjwKkD7e0_" role="2OqNvi" />
                                          </node>
                                        </node>
                                      </node>
                                      <node concept="3eNFk2" id="4zSRxm6Yb5b" role="3eNLev">
                                        <node concept="3clFbS" id="4zSRxm6Yb5d" role="3eOfB_">
                                          <node concept="3cpWs8" id="3lhG7y_kbq8" role="3cqZAp">
                                            <node concept="3cpWsn" id="3lhG7y_kbqb" role="3cpWs9">
                                              <property role="TrG5h" value="pckg" />
                                              <node concept="17QB3L" id="3lhG7y_kbq6" role="1tU5fm" />
                                              <node concept="2OqwBi" id="5pDpbLMp_7j" role="33vP2m">
                                                <node concept="2OqwBi" id="5pDpbLMp_7k" role="2Oq$k0">
                                                  <node concept="2OqwBi" id="5pDpbLMpKDJ" role="2Oq$k0">
                                                    <node concept="2JrnkZ" id="5pDpbLMpIc1" role="2Oq$k0">
                                                      <node concept="2OqwBi" id="5pDpbLMpD95" role="2JrQYb">
                                                        <node concept="37vLTw" id="5pDpbLMp_7n" role="2Oq$k0">
                                                          <ref role="3cqZAo" node="3Fg0S50extz" resolve="it" />
                                                        </node>
                                                        <node concept="3TrEf2" id="5pDpbLMpFA3" role="2OqNvi">
                                                          <ref role="3Tt5mk" to="tpce:fKAX2Z_" resolve="dataType" />
                                                        </node>
                                                      </node>
                                                    </node>
                                                    <node concept="liA8E" id="5pDpbLMpM9Z" role="2OqNvi">
                                                      <ref role="37wK5l" to="mhbf:~SNode.getModel()" resolve="getModel" />
                                                    </node>
                                                  </node>
                                                  <node concept="liA8E" id="5pDpbLMp_7p" role="2OqNvi">
                                                    <ref role="37wK5l" to="mhbf:~SModel.getModule()" resolve="getModule" />
                                                  </node>
                                                </node>
                                                <node concept="liA8E" id="5pDpbLMp_7q" role="2OqNvi">
                                                  <ref role="37wK5l" to="lui2:~SModule.getModuleName()" resolve="getModuleName" />
                                                </node>
                                              </node>
                                            </node>
                                          </node>
                                          <node concept="3clFbF" id="4zSRxm6Yy2a" role="3cqZAp">
                                            <node concept="37vLTI" id="4zSRxm6Y$Bk" role="3clFbG">
                                              <node concept="37vLTw" id="4zSRxm6Yy29" role="37vLTJ">
                                                <ref role="3cqZAo" node="AjwKkD6CmF" resolve="type" />
                                              </node>
                                              <node concept="2ShNRf" id="4zSRxm6YFoO" role="37vLTx">
                                                <node concept="1pGfFk" id="3lhG7y_iX1z" role="2ShVmc">
                                                  <ref role="37wK5l" to="sgfj:~EnumPropertyType.&lt;init&gt;(java.lang.String,java.lang.String)" resolve="EnumPropertyType" />
                                                  <node concept="37vLTw" id="3lhG7y_lsL3" role="37wK5m">
                                                    <ref role="3cqZAo" node="3lhG7y_kbqb" resolve="pckg" />
                                                  </node>
                                                  <node concept="2OqwBi" id="3lhG7y_l$mf" role="37wK5m">
                                                    <node concept="2OqwBi" id="3lhG7y_lxLr" role="2Oq$k0">
                                                      <node concept="37vLTw" id="3lhG7y_lvYb" role="2Oq$k0">
                                                        <ref role="3cqZAo" node="3Fg0S50extz" resolve="it" />
                                                      </node>
                                                      <node concept="3TrEf2" id="3lhG7y_lz78" role="2OqNvi">
                                                        <ref role="3Tt5mk" to="tpce:fKAX2Z_" resolve="dataType" />
                                                      </node>
                                                    </node>
                                                    <node concept="3TrcHB" id="3lhG7y_lAVX" role="2OqNvi">
                                                      <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                                                    </node>
                                                  </node>
                                                </node>
                                              </node>
                                            </node>
                                          </node>
                                        </node>
                                        <node concept="2OqwBi" id="7mpQTBZY3O5" role="3eO9$A">
                                          <node concept="2OqwBi" id="4zSRxm6YeoH" role="2Oq$k0">
                                            <node concept="37vLTw" id="4zSRxm6Yd6Z" role="2Oq$k0">
                                              <ref role="3cqZAo" node="3Fg0S50extz" resolve="it" />
                                            </node>
                                            <node concept="3TrEf2" id="4zSRxm6YfAr" role="2OqNvi">
                                              <ref role="3Tt5mk" to="tpce:fKAX2Z_" resolve="dataType" />
                                            </node>
                                          </node>
                                          <node concept="1mIQ4w" id="7mpQTBZYzp7" role="2OqNvi">
                                            <node concept="chp4Y" id="7mpQTBZY_u3" role="cj9EA">
                                              <ref role="cht4Q" to="tpce:2TR3acGo7Lv" resolve="EnumerationDeclaration" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                    <node concept="3clFbF" id="3Fg0S50exts" role="3cqZAp">
                                      <node concept="2ShNRf" id="3Fg0S50extt" role="3clFbG">
                                        <node concept="1pGfFk" id="3Fg0S50extu" role="2ShVmc">
                                          <ref role="37wK5l" to="sgfj:~PropertyData.&lt;init&gt;(java.lang.String,java.lang.String,org.modelix.model.data.PropertyType,boolean,java.lang.String)" resolve="PropertyData" />
                                          <node concept="2OqwBi" id="sN$G5gkz6p" role="37wK5m">
                                            <node concept="2YIFZM" id="sN$G5gkwjt" role="2Oq$k0">
                                              <ref role="1Pybhc" to="e8bb:~MetaIdByDeclaration" resolve="MetaIdByDeclaration" />
                                              <ref role="37wK5l" to="e8bb:~MetaIdByDeclaration.getPropId(org.jetbrains.mps.openapi.model.SNode)" resolve="getPropId" />
                                              <node concept="37vLTw" id="sN$G5gkylp" role="37wK5m">
                                                <ref role="3cqZAo" node="3Fg0S50extz" resolve="it" />
                                              </node>
                                            </node>
                                            <node concept="liA8E" id="sN$G5gk_2U" role="2OqNvi">
                                              <ref role="37wK5l" to="e8bb:~SPropertyId.toString()" resolve="toString" />
                                            </node>
                                          </node>
                                          <node concept="2OqwBi" id="5oMuLXHRO5x" role="37wK5m">
                                            <node concept="37vLTw" id="5oMuLXHRNlh" role="2Oq$k0">
                                              <ref role="3cqZAo" node="3Fg0S50extz" resolve="it" />
                                            </node>
                                            <node concept="3TrcHB" id="5oMuLXHRPtW" role="2OqNvi">
                                              <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                                            </node>
                                          </node>
                                          <node concept="37vLTw" id="AjwKkD6CmH" role="37wK5m">
                                            <ref role="3cqZAo" node="AjwKkD6CmF" resolve="type" />
                                          </node>
                                          <node concept="3clFbT" id="7jUShhooSid" role="37wK5m">
                                            <property role="3clFbU" value="true" />
                                          </node>
                                          <node concept="1rXfSq" id="6leOzHDskSh" role="37wK5m">
                                            <ref role="37wK5l" node="6leOzHDqtxp" resolve="deprecationMsg" />
                                            <node concept="37vLTw" id="6leOzHDsn6Y" role="37wK5m">
                                              <ref role="3cqZAo" node="3Fg0S50extz" resolve="it" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                  </node>
                                  <node concept="Rh6nW" id="3Fg0S50extz" role="1bW2Oz">
                                    <property role="TrG5h" value="it" />
                                    <node concept="2jxLKc" id="3Fg0S50ext$" role="1tU5fm" />
                                  </node>
                                </node>
                              </node>
                            </node>
                            <node concept="ANE8D" id="3Fg0S50ext_" role="2OqNvi" />
                          </node>
                        </node>
                      </node>
                      <node concept="3clFbH" id="6YtYONzPmwI" role="3cqZAp" />
                      <node concept="3cpWs8" id="3Fg0S50eoNg" role="3cqZAp">
                        <node concept="3cpWsn" id="3Fg0S50eoNh" role="3cpWs9">
                          <property role="TrG5h" value="childLinks" />
                          <node concept="_YKpA" id="3Fg0S50evCt" role="1tU5fm">
                            <node concept="3uibUv" id="3Fg0S50evCv" role="_ZDj9">
                              <ref role="3uigEE" to="sgfj:~ChildLinkData" resolve="ChildLinkData" />
                            </node>
                          </node>
                          <node concept="2OqwBi" id="3Fg0S50eu4v" role="33vP2m">
                            <node concept="2OqwBi" id="3Fg0S50eoNi" role="2Oq$k0">
                              <node concept="2OqwBi" id="3Fg0S50eoNj" role="2Oq$k0">
                                <node concept="2OqwBi" id="5oMuLXHR2yX" role="2Oq$k0">
                                  <node concept="2OqwBi" id="3Fg0S50eoNk" role="2Oq$k0">
                                    <node concept="37vLTw" id="3Fg0S50eoNl" role="2Oq$k0">
                                      <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                                    </node>
                                    <node concept="3Tsc0h" id="3Fg0S50eoNm" role="2OqNvi">
                                      <ref role="3TtcxE" to="tpce:f_TKVDF" resolve="linkDeclaration" />
                                    </node>
                                  </node>
                                  <node concept="3zZkjj" id="5oMuLXHR5m7" role="2OqNvi">
                                    <node concept="1bVj0M" id="5oMuLXHR5m9" role="23t8la">
                                      <node concept="3clFbS" id="5oMuLXHR5ma" role="1bW5cS">
                                        <node concept="3clFbF" id="5oMuLXHR6il" role="3cqZAp">
                                          <node concept="2OqwBi" id="5oMuLXHR8Tl" role="3clFbG">
                                            <node concept="2OqwBi" id="5oMuLXHR78c" role="2Oq$k0">
                                              <node concept="37vLTw" id="5oMuLXHR6ik" role="2Oq$k0">
                                                <ref role="3cqZAo" node="5oMuLXHR5mb" resolve="it" />
                                              </node>
                                              <node concept="3TrEf2" id="5oMuLXHR7PE" role="2OqNvi">
                                                <ref role="3Tt5mk" to="tpce:fA0ks94" resolve="specializedLink" />
                                              </node>
                                            </node>
                                            <node concept="3w_OXm" id="5oMuLXHR9Lq" role="2OqNvi" />
                                          </node>
                                        </node>
                                      </node>
                                      <node concept="Rh6nW" id="5oMuLXHR5mb" role="1bW2Oz">
                                        <property role="TrG5h" value="it" />
                                        <node concept="2jxLKc" id="5oMuLXHR5mc" role="1tU5fm" />
                                      </node>
                                    </node>
                                  </node>
                                </node>
                                <node concept="3zZkjj" id="3Fg0S50eoNn" role="2OqNvi">
                                  <node concept="1bVj0M" id="3Fg0S50eoNo" role="23t8la">
                                    <node concept="Rh6nW" id="3Fg0S50eoNp" role="1bW2Oz">
                                      <property role="TrG5h" value="it" />
                                      <node concept="2jxLKc" id="3Fg0S50eoNq" role="1tU5fm" />
                                    </node>
                                    <node concept="3clFbS" id="3Fg0S50eoNr" role="1bW5cS">
                                      <node concept="3clFbF" id="3Fg0S50eoNs" role="3cqZAp">
                                        <node concept="2OqwBi" id="3Fg0S50eoNt" role="3clFbG">
                                          <node concept="2OqwBi" id="3Fg0S50eoNu" role="2Oq$k0">
                                            <node concept="37vLTw" id="3Fg0S50eoNv" role="2Oq$k0">
                                              <ref role="3cqZAo" node="3Fg0S50eoNp" resolve="it" />
                                            </node>
                                            <node concept="3TrcHB" id="3Fg0S50eoNw" role="2OqNvi">
                                              <ref role="3TsBF5" to="tpce:3Ftr4R6BH8$" resolve="metaClass" />
                                            </node>
                                          </node>
                                          <node concept="21noJN" id="3Fg0S50eoNx" role="2OqNvi">
                                            <node concept="21nZrQ" id="3Fg0S50eoNy" role="21noJM">
                                              <ref role="21nZrZ" to="tpce:3Ftr4R6BFyo" resolve="aggregation" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                  </node>
                                </node>
                              </node>
                              <node concept="3$u5V9" id="3Fg0S50eoNz" role="2OqNvi">
                                <node concept="1bVj0M" id="3Fg0S50eoN$" role="23t8la">
                                  <node concept="3clFbS" id="3Fg0S50eoN_" role="1bW5cS">
                                    <node concept="3clFbF" id="2pErVStxVSl" role="3cqZAp">
                                      <node concept="1rXfSq" id="2pErVStxVSm" role="3clFbG">
                                        <ref role="37wK5l" node="2pErVStxHxp" resolve="exportLanguage" />
                                        <node concept="2OqwBi" id="2pErVStxVSn" role="37wK5m">
                                          <node concept="37vLTw" id="2pErVStxVSo" role="2Oq$k0">
                                            <ref role="3cqZAo" node="3Fg0S50eoNT" resolve="it" />
                                          </node>
                                          <node concept="3TrEf2" id="2pErVStxVSp" role="2OqNvi">
                                            <ref role="3Tt5mk" to="tpce:fA0lvVK" resolve="target" />
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                    <node concept="3clFbF" id="3Fg0S50eoNA" role="3cqZAp">
                                      <node concept="2ShNRf" id="3Fg0S50eoNB" role="3clFbG">
                                        <node concept="1pGfFk" id="3Fg0S50eoNC" role="2ShVmc">
                                          <ref role="37wK5l" to="sgfj:~ChildLinkData.&lt;init&gt;(java.lang.String,java.lang.String,java.lang.String,boolean,boolean,java.lang.String)" resolve="ChildLinkData" />
                                          <node concept="2OqwBi" id="sN$G5gkB5e" role="37wK5m">
                                            <node concept="2YIFZM" id="sN$G5gkCu1" role="2Oq$k0">
                                              <ref role="1Pybhc" to="e8bb:~MetaIdByDeclaration" resolve="MetaIdByDeclaration" />
                                              <ref role="37wK5l" to="e8bb:~MetaIdByDeclaration.getLinkId(org.jetbrains.mps.openapi.model.SNode)" resolve="getLinkId" />
                                              <node concept="37vLTw" id="sN$G5gkCu2" role="37wK5m">
                                                <ref role="3cqZAo" node="3Fg0S50eoNT" resolve="it" />
                                              </node>
                                            </node>
                                            <node concept="liA8E" id="sN$G5gkB5h" role="2OqNvi">
                                              <ref role="37wK5l" to="e8bb:~SContainmentLinkId.toString()" resolve="toString" />
                                            </node>
                                          </node>
                                          <node concept="2OqwBi" id="5oMuLXHRJfL" role="37wK5m">
                                            <node concept="37vLTw" id="5oMuLXHRIq1" role="2Oq$k0">
                                              <ref role="3cqZAo" node="3Fg0S50eoNT" resolve="it" />
                                            </node>
                                            <node concept="3TrcHB" id="5oMuLXHRJQq" role="2OqNvi">
                                              <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                                            </node>
                                          </node>
                                          <node concept="1rXfSq" id="4VPKBwf$8w8" role="37wK5m">
                                            <ref role="37wK5l" node="TKTYk$gcfk" resolve="linkTargetFqName" />
                                            <node concept="37vLTw" id="3Fg0S50eoNI" role="37wK5m">
                                              <ref role="3cqZAo" node="3Fg0S50eoNT" resolve="it" />
                                            </node>
                                          </node>
                                          <node concept="3fqX7Q" id="3Fg0S50eoNL" role="37wK5m">
                                            <node concept="2OqwBi" id="3Fg0S50eoNM" role="3fr31v">
                                              <node concept="37vLTw" id="3Fg0S50eoNN" role="2Oq$k0">
                                                <ref role="3cqZAo" node="3Fg0S50eoNT" resolve="it" />
                                              </node>
                                              <node concept="2qgKlT" id="3Fg0S50eoNO" role="2OqNvi">
                                                <ref role="37wK5l" to="tpcn:hEwIfAt" resolve="isSingular" />
                                              </node>
                                            </node>
                                          </node>
                                          <node concept="3fqX7Q" id="3Fg0S50eoNP" role="37wK5m">
                                            <node concept="2OqwBi" id="3Fg0S50eoNQ" role="3fr31v">
                                              <node concept="37vLTw" id="3Fg0S50eoNR" role="2Oq$k0">
                                                <ref role="3cqZAo" node="3Fg0S50eoNT" resolve="it" />
                                              </node>
                                              <node concept="2qgKlT" id="3Fg0S50eoNS" role="2OqNvi">
                                                <ref role="37wK5l" to="tpcn:2VYdUfnkjmB" resolve="isAtLeastOneCardinality" />
                                              </node>
                                            </node>
                                          </node>
                                          <node concept="1rXfSq" id="6leOzHDspCB" role="37wK5m">
                                            <ref role="37wK5l" node="6leOzHDqtxp" resolve="deprecationMsg" />
                                            <node concept="37vLTw" id="6leOzHDss7R" role="37wK5m">
                                              <ref role="3cqZAo" node="3Fg0S50eoNT" resolve="it" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                  </node>
                                  <node concept="Rh6nW" id="3Fg0S50eoNT" role="1bW2Oz">
                                    <property role="TrG5h" value="it" />
                                    <node concept="2jxLKc" id="3Fg0S50eoNU" role="1tU5fm" />
                                  </node>
                                </node>
                              </node>
                            </node>
                            <node concept="ANE8D" id="3Fg0S50ev0E" role="2OqNvi" />
                          </node>
                        </node>
                      </node>
                      <node concept="3cpWs8" id="3Fg0S50e_Ej" role="3cqZAp">
                        <node concept="3cpWsn" id="3Fg0S50e_Ek" role="3cpWs9">
                          <property role="TrG5h" value="referenceLinks" />
                          <node concept="_YKpA" id="3Fg0S50e_El" role="1tU5fm">
                            <node concept="3uibUv" id="3Fg0S50e_Em" role="_ZDj9">
                              <ref role="3uigEE" to="sgfj:~ReferenceLinkData" resolve="ReferenceLinkData" />
                            </node>
                          </node>
                          <node concept="2OqwBi" id="3Fg0S50e_En" role="33vP2m">
                            <node concept="2OqwBi" id="3Fg0S50e_Eo" role="2Oq$k0">
                              <node concept="2OqwBi" id="3Fg0S50e_Ep" role="2Oq$k0">
                                <node concept="2OqwBi" id="5oMuLXHRbfz" role="2Oq$k0">
                                  <node concept="2OqwBi" id="3Fg0S50e_Eq" role="2Oq$k0">
                                    <node concept="37vLTw" id="3Fg0S50e_Er" role="2Oq$k0">
                                      <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                                    </node>
                                    <node concept="3Tsc0h" id="3Fg0S50e_Es" role="2OqNvi">
                                      <ref role="3TtcxE" to="tpce:f_TKVDF" resolve="linkDeclaration" />
                                    </node>
                                  </node>
                                  <node concept="3zZkjj" id="5oMuLXHRe7p" role="2OqNvi">
                                    <node concept="1bVj0M" id="5oMuLXHRe7r" role="23t8la">
                                      <node concept="3clFbS" id="5oMuLXHRe7s" role="1bW5cS">
                                        <node concept="3clFbF" id="5oMuLXHRf8j" role="3cqZAp">
                                          <node concept="2OqwBi" id="5oMuLXHRi2s" role="3clFbG">
                                            <node concept="2OqwBi" id="5oMuLXHRfZM" role="2Oq$k0">
                                              <node concept="37vLTw" id="5oMuLXHRf8i" role="2Oq$k0">
                                                <ref role="3cqZAo" node="5oMuLXHRe7t" resolve="it" />
                                              </node>
                                              <node concept="3TrEf2" id="5oMuLXHRhbl" role="2OqNvi">
                                                <ref role="3Tt5mk" to="tpce:fA0ks94" resolve="specializedLink" />
                                              </node>
                                            </node>
                                            <node concept="3w_OXm" id="5oMuLXHRiyW" role="2OqNvi" />
                                          </node>
                                        </node>
                                      </node>
                                      <node concept="Rh6nW" id="5oMuLXHRe7t" role="1bW2Oz">
                                        <property role="TrG5h" value="it" />
                                        <node concept="2jxLKc" id="5oMuLXHRe7u" role="1tU5fm" />
                                      </node>
                                    </node>
                                  </node>
                                </node>
                                <node concept="3zZkjj" id="3Fg0S50e_Et" role="2OqNvi">
                                  <node concept="1bVj0M" id="3Fg0S50e_Eu" role="23t8la">
                                    <node concept="Rh6nW" id="3Fg0S50e_Ev" role="1bW2Oz">
                                      <property role="TrG5h" value="it" />
                                      <node concept="2jxLKc" id="3Fg0S50e_Ew" role="1tU5fm" />
                                    </node>
                                    <node concept="3clFbS" id="3Fg0S50e_Ex" role="1bW5cS">
                                      <node concept="3clFbF" id="3Fg0S50e_Ey" role="3cqZAp">
                                        <node concept="2OqwBi" id="3Fg0S50e_Ez" role="3clFbG">
                                          <node concept="2OqwBi" id="3Fg0S50e_E$" role="2Oq$k0">
                                            <node concept="37vLTw" id="3Fg0S50e_E_" role="2Oq$k0">
                                              <ref role="3cqZAo" node="3Fg0S50e_Ev" resolve="it" />
                                            </node>
                                            <node concept="3TrcHB" id="3Fg0S50e_EA" role="2OqNvi">
                                              <ref role="3TsBF5" to="tpce:3Ftr4R6BH8$" resolve="metaClass" />
                                            </node>
                                          </node>
                                          <node concept="21noJN" id="3Fg0S50e_EB" role="2OqNvi">
                                            <node concept="21nZrQ" id="3Fg0S50e_EC" role="21noJM">
                                              <ref role="21nZrZ" to="tpce:3Ftr4R6BFyn" resolve="reference" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                  </node>
                                </node>
                              </node>
                              <node concept="3$u5V9" id="3Fg0S50e_ED" role="2OqNvi">
                                <node concept="1bVj0M" id="3Fg0S50e_EE" role="23t8la">
                                  <node concept="3clFbS" id="3Fg0S50e_EF" role="1bW5cS">
                                    <node concept="3clFbF" id="2pErVStxqRH" role="3cqZAp">
                                      <node concept="1rXfSq" id="2pErVStxqRF" role="3clFbG">
                                        <ref role="37wK5l" node="2pErVStxHxp" resolve="exportLanguage" />
                                        <node concept="2OqwBi" id="2pErVStxt3A" role="37wK5m">
                                          <node concept="37vLTw" id="2pErVStxrWG" role="2Oq$k0">
                                            <ref role="3cqZAo" node="3Fg0S50e_EZ" resolve="it" />
                                          </node>
                                          <node concept="3TrEf2" id="2pErVStxuSb" role="2OqNvi">
                                            <ref role="3Tt5mk" to="tpce:fA0lvVK" resolve="target" />
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                    <node concept="3clFbF" id="3Fg0S50e_EG" role="3cqZAp">
                                      <node concept="2ShNRf" id="3Fg0S50e_EH" role="3clFbG">
                                        <node concept="1pGfFk" id="3Fg0S50e_EI" role="2ShVmc">
                                          <ref role="37wK5l" to="sgfj:~ReferenceLinkData.&lt;init&gt;(java.lang.String,java.lang.String,java.lang.String,boolean,java.lang.String)" resolve="ReferenceLinkData" />
                                          <node concept="2OqwBi" id="sN$G5gkFrP" role="37wK5m">
                                            <node concept="2YIFZM" id="sN$G5gkGsm" role="2Oq$k0">
                                              <ref role="37wK5l" to="e8bb:~MetaIdByDeclaration.getLinkId(org.jetbrains.mps.openapi.model.SNode)" resolve="getLinkId" />
                                              <ref role="1Pybhc" to="e8bb:~MetaIdByDeclaration" resolve="MetaIdByDeclaration" />
                                              <node concept="37vLTw" id="sN$G5gkGsn" role="37wK5m">
                                                <ref role="3cqZAo" node="3Fg0S50e_EZ" resolve="it" />
                                              </node>
                                            </node>
                                            <node concept="liA8E" id="sN$G5gkFrS" role="2OqNvi">
                                              <ref role="37wK5l" to="e8bb:~SContainmentLinkId.toString()" resolve="toString" />
                                            </node>
                                          </node>
                                          <node concept="2OqwBi" id="5oMuLXHRLy9" role="37wK5m">
                                            <node concept="37vLTw" id="5oMuLXHRKNx" role="2Oq$k0">
                                              <ref role="3cqZAo" node="3Fg0S50e_EZ" resolve="it" />
                                            </node>
                                            <node concept="3TrcHB" id="5oMuLXHRMos" role="2OqNvi">
                                              <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                                            </node>
                                          </node>
                                          <node concept="1rXfSq" id="4VPKBwf$5Nw" role="37wK5m">
                                            <ref role="37wK5l" node="TKTYk$gcfk" resolve="linkTargetFqName" />
                                            <node concept="37vLTw" id="3Fg0S50e_EO" role="37wK5m">
                                              <ref role="3cqZAo" node="3Fg0S50e_EZ" resolve="it" />
                                            </node>
                                          </node>
                                          <node concept="3fqX7Q" id="3Fg0S50e_EV" role="37wK5m">
                                            <node concept="2OqwBi" id="3Fg0S50e_EW" role="3fr31v">
                                              <node concept="37vLTw" id="3Fg0S50e_EX" role="2Oq$k0">
                                                <ref role="3cqZAo" node="3Fg0S50e_EZ" resolve="it" />
                                              </node>
                                              <node concept="2qgKlT" id="3Fg0S50e_EY" role="2OqNvi">
                                                <ref role="37wK5l" to="tpcn:2VYdUfnkjmB" resolve="isAtLeastOneCardinality" />
                                              </node>
                                            </node>
                                          </node>
                                          <node concept="1rXfSq" id="6leOzHDsvk5" role="37wK5m">
                                            <ref role="37wK5l" node="6leOzHDqtxp" resolve="deprecationMsg" />
                                            <node concept="37vLTw" id="6leOzHDsx8H" role="37wK5m">
                                              <ref role="3cqZAo" node="3Fg0S50e_EZ" resolve="it" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                  </node>
                                  <node concept="Rh6nW" id="3Fg0S50e_EZ" role="1bW2Oz">
                                    <property role="TrG5h" value="it" />
                                    <node concept="2jxLKc" id="3Fg0S50e_F0" role="1tU5fm" />
                                  </node>
                                </node>
                              </node>
                            </node>
                            <node concept="ANE8D" id="3Fg0S50e_F1" role="2OqNvi" />
                          </node>
                        </node>
                      </node>
                      <node concept="3cpWs8" id="3Fg0S50e$xm" role="3cqZAp">
                        <node concept="3cpWsn" id="3Fg0S50e$xn" role="3cpWs9">
                          <property role="TrG5h" value="is_abstract" />
                          <node concept="10P_77" id="3Fg0S50e$fG" role="1tU5fm" />
                          <node concept="22lmx$" id="3Fg0S50e$xo" role="33vP2m">
                            <node concept="2OqwBi" id="3Fg0S50e$xp" role="3uHU7w">
                              <node concept="37vLTw" id="3Fg0S50e$xq" role="2Oq$k0">
                                <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                              </node>
                              <node concept="1mIQ4w" id="3Fg0S50e$xr" role="2OqNvi">
                                <node concept="chp4Y" id="3Fg0S50e$xs" role="cj9EA">
                                  <ref role="cht4Q" to="tpce:h0PlHMJ" resolve="InterfaceConceptDeclaration" />
                                </node>
                              </node>
                            </node>
                            <node concept="2OqwBi" id="3Fg0S50e$xt" role="3uHU7B">
                              <node concept="37vLTw" id="3Fg0S50e$xu" role="2Oq$k0">
                                <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                              </node>
                              <node concept="3TrcHB" id="3Fg0S50e$xv" role="2OqNvi">
                                <ref role="3TsBF5" to="tpce:40UcGlRb7V2" resolve="abstract" />
                              </node>
                            </node>
                          </node>
                        </node>
                      </node>
                      <node concept="3cpWs8" id="3Fg0S50fId$" role="3cqZAp">
                        <node concept="3cpWsn" id="3Fg0S50fId_" role="3cpWs9">
                          <property role="TrG5h" value="superConcepts" />
                          <node concept="_YKpA" id="3Fg0S50fM6z" role="1tU5fm">
                            <node concept="17QB3L" id="3Fg0S50fM6_" role="_ZDj9" />
                          </node>
                          <node concept="2OqwBi" id="3Fg0S50fKkx" role="33vP2m">
                            <node concept="2OqwBi" id="3Fg0S50fIdA" role="2Oq$k0">
                              <node concept="2OqwBi" id="TKTYk$hJPc" role="2Oq$k0">
                                <node concept="2OqwBi" id="cGlNZN4URS" role="2Oq$k0">
                                  <node concept="2OqwBi" id="3Fg0S50fIdB" role="2Oq$k0">
                                    <node concept="37vLTw" id="3Fg0S50fIdC" role="2Oq$k0">
                                      <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                                    </node>
                                    <node concept="2qgKlT" id="3Fg0S50fIdD" role="2OqNvi">
                                      <ref role="37wK5l" to="tpcn:hMuxyK2" resolve="getImmediateSuperconcepts" />
                                    </node>
                                  </node>
                                  <node concept="1VAtEI" id="cGlNZN50Lv" role="2OqNvi" />
                                </node>
                                <node concept="1KnU$U" id="TKTYk$hLmF" role="2OqNvi" />
                              </node>
                              <node concept="3$u5V9" id="3Fg0S50fIdE" role="2OqNvi">
                                <node concept="1bVj0M" id="3Fg0S50fIdF" role="23t8la">
                                  <node concept="3clFbS" id="3Fg0S50fIdG" role="1bW5cS">
                                    <node concept="3cpWs8" id="3Fg0S50gXFl" role="3cqZAp">
                                      <node concept="3cpWsn" id="3Fg0S50gXFm" role="3cpWs9">
                                        <property role="TrG5h" value="superLanguage" />
                                        <node concept="3uibUv" id="3Fg0S50gXhX" role="1tU5fm">
                                          <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
                                        </node>
                                        <node concept="0kSF2" id="3Fg0S50gXFn" role="33vP2m">
                                          <node concept="3uibUv" id="3Fg0S50gXFo" role="0kSFW">
                                            <ref role="3uigEE" to="w1kc:~Language" resolve="Language" />
                                          </node>
                                          <node concept="2OqwBi" id="TKTYk$hMSq" role="0kSFX">
                                            <node concept="2JrnkZ" id="3Fg0S50gXFq" role="2Oq$k0">
                                              <node concept="2OqwBi" id="TKTYk$hMez" role="2JrQYb">
                                                <node concept="37vLTw" id="3Fg0S50gXFs" role="2Oq$k0">
                                                  <ref role="3cqZAo" node="3Fg0S50fIdL" resolve="it" />
                                                </node>
                                                <node concept="I4A8Y" id="3Fg0S50gXFt" role="2OqNvi" />
                                              </node>
                                            </node>
                                            <node concept="liA8E" id="3Fg0S50gXFu" role="2OqNvi">
                                              <ref role="37wK5l" to="mhbf:~SModel.getModule()" resolve="getModule" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                    <node concept="3clFbJ" id="3Fg0S50h04K" role="3cqZAp">
                                      <node concept="3clFbS" id="3Fg0S50h04M" role="3clFbx">
                                        <node concept="3clFbF" id="3Fg0S50h5jn" role="3cqZAp">
                                          <node concept="1rXfSq" id="3Fg0S50h5jm" role="3clFbG">
                                            <ref role="37wK5l" node="3Fg0S50ge_5" resolve="exportLanguage" />
                                            <node concept="37vLTw" id="3Fg0S50h68K" role="37wK5m">
                                              <ref role="3cqZAo" node="3Fg0S50gXFm" resolve="superLanguage" />
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                      <node concept="3y3z36" id="3Fg0S50h2a6" role="3clFbw">
                                        <node concept="10Nm6u" id="3Fg0S50h2Pm" role="3uHU7w" />
                                        <node concept="37vLTw" id="3Fg0S50h0TY" role="3uHU7B">
                                          <ref role="3cqZAo" node="3Fg0S50gXFm" resolve="superLanguage" />
                                        </node>
                                      </node>
                                    </node>
                                    <node concept="3clFbF" id="3Fg0S50fIdH" role="3cqZAp">
                                      <node concept="1rXfSq" id="4VPKBwf$Gp8" role="3clFbG">
                                        <ref role="37wK5l" node="4VPKBwfzUho" resolve="fqName" />
                                        <node concept="37vLTw" id="4VPKBwf$HiD" role="37wK5m">
                                          <ref role="3cqZAo" node="3Fg0S50fIdL" resolve="it" />
                                        </node>
                                      </node>
                                    </node>
                                  </node>
                                  <node concept="Rh6nW" id="3Fg0S50fIdL" role="1bW2Oz">
                                    <property role="TrG5h" value="it" />
                                    <node concept="2jxLKc" id="3Fg0S50fIdM" role="1tU5fm" />
                                  </node>
                                </node>
                              </node>
                            </node>
                            <node concept="ANE8D" id="3Fg0S50fLyg" role="2OqNvi" />
                          </node>
                        </node>
                      </node>
                      <node concept="3clFbH" id="3Fg0S50gLTv" role="3cqZAp" />
                      <node concept="3clFbF" id="3Fg0S50cWJn" role="3cqZAp">
                        <node concept="2ShNRf" id="3Fg0S50cWJj" role="3clFbG">
                          <node concept="1pGfFk" id="3Fg0S50cX7i" role="2ShVmc">
                            <ref role="37wK5l" to="sgfj:~ConceptData.&lt;init&gt;(java.lang.String,java.lang.String,boolean,java.util.List,java.util.List,java.util.List,java.util.List,java.lang.String)" resolve="ConceptData" />
                            <node concept="3cpWs3" id="2sGJABKvA1m" role="37wK5m">
                              <node concept="Xl_RD" id="2sGJABKvBm5" role="3uHU7B">
                                <property role="Xl_RC" value="mps:" />
                              </node>
                              <node concept="2OqwBi" id="sN$G5gkJEp" role="3uHU7w">
                                <node concept="2YIFZM" id="sN$G5gkKmz" role="2Oq$k0">
                                  <ref role="1Pybhc" to="e8bb:~MetaIdByDeclaration" resolve="MetaIdByDeclaration" />
                                  <ref role="37wK5l" to="e8bb:~MetaIdByDeclaration.getConceptId(org.jetbrains.mps.openapi.model.SNode)" resolve="getConceptId" />
                                  <node concept="37vLTw" id="sN$G5gkKm$" role="37wK5m">
                                    <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                                  </node>
                                </node>
                                <node concept="liA8E" id="sN$G5gkJEs" role="2OqNvi">
                                  <ref role="37wK5l" to="e8bb:~SConceptId.toString()" resolve="toString" />
                                </node>
                              </node>
                            </node>
                            <node concept="2OqwBi" id="3Fg0S50cZsA" role="37wK5m">
                              <node concept="37vLTw" id="3Fg0S50cZ4o" role="2Oq$k0">
                                <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                              </node>
                              <node concept="3TrcHB" id="3Fg0S50d05b" role="2OqNvi">
                                <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                              </node>
                            </node>
                            <node concept="37vLTw" id="3Fg0S50e$xw" role="37wK5m">
                              <ref role="3cqZAo" node="3Fg0S50e$xn" resolve="is_abstract" />
                            </node>
                            <node concept="37vLTw" id="3Fg0S50extA" role="37wK5m">
                              <ref role="3cqZAo" node="3Fg0S50extj" resolve="properties" />
                            </node>
                            <node concept="37vLTw" id="3Fg0S50eoNV" role="37wK5m">
                              <ref role="3cqZAo" node="3Fg0S50eoNh" resolve="childLinks" />
                            </node>
                            <node concept="37vLTw" id="3Fg0S50eDFl" role="37wK5m">
                              <ref role="3cqZAo" node="3Fg0S50e_Ek" resolve="referenceLinks" />
                            </node>
                            <node concept="37vLTw" id="3Fg0S50fMQv" role="37wK5m">
                              <ref role="3cqZAo" node="3Fg0S50fId_" resolve="superConcepts" />
                            </node>
                            <node concept="1rXfSq" id="6leOzHDs$KP" role="37wK5m">
                              <ref role="37wK5l" node="6leOzHDqtxp" resolve="deprecationMsg" />
                              <node concept="37vLTw" id="6leOzHDsAcV" role="37wK5m">
                                <ref role="3cqZAo" node="3Fg0S50cWmY" resolve="concept" />
                              </node>
                            </node>
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="Rh6nW" id="3Fg0S50cWmY" role="1bW2Oz">
                      <property role="TrG5h" value="concept" />
                      <node concept="2jxLKc" id="3Fg0S50cWmZ" role="1tU5fm" />
                    </node>
                  </node>
                </node>
              </node>
              <node concept="ANE8D" id="3Fg0S50cYuV" role="2OqNvi" />
            </node>
          </node>
        </node>
        <node concept="3clFbH" id="3Fg0S50gMDI" role="3cqZAp" />
        <node concept="3cpWs8" id="4zSRxm6Znpl" role="3cqZAp">
          <node concept="3cpWsn" id="4zSRxm6Znpo" role="3cpWs9">
            <property role="TrG5h" value="enums" />
            <node concept="_YKpA" id="4zSRxm6Znph" role="1tU5fm">
              <node concept="3uibUv" id="4zSRxm6ZozG" role="_ZDj9">
                <ref role="3uigEE" to="sgfj:~EnumData" resolve="EnumData" />
              </node>
            </node>
            <node concept="2OqwBi" id="4zSRxm71bJM" role="33vP2m">
              <node concept="2OqwBi" id="4zSRxm6ZzSQ" role="2Oq$k0">
                <node concept="2OqwBi" id="4zSRxm6ZvFD" role="2Oq$k0">
                  <node concept="37vLTw" id="4zSRxm6ZtKc" role="2Oq$k0">
                    <ref role="3cqZAo" node="3Fg0S50cQzV" resolve="rootNodes" />
                  </node>
                  <node concept="v3k3i" id="4zSRxm6ZwQ6" role="2OqNvi">
                    <node concept="chp4Y" id="4zSRxm6ZxHC" role="v3oSu">
                      <ref role="cht4Q" to="tpce:2TR3acGo7Lv" resolve="EnumerationDeclaration" />
                    </node>
                  </node>
                </node>
                <node concept="3$u5V9" id="4zSRxm6ZEdh" role="2OqNvi">
                  <node concept="1bVj0M" id="4zSRxm6ZEdj" role="23t8la">
                    <node concept="3clFbS" id="4zSRxm6ZEdk" role="1bW5cS">
                      <node concept="3cpWs8" id="4zSRxm70fW7" role="3cqZAp">
                        <node concept="3cpWsn" id="4zSRxm70fWa" role="3cpWs9">
                          <property role="TrG5h" value="members" />
                          <node concept="_YKpA" id="4zSRxm70fW5" role="1tU5fm">
                            <node concept="3uibUv" id="4zSRxm70hK$" role="_ZDj9">
                              <ref role="3uigEE" to="sgfj:~EnumMemberData" resolve="EnumMemberData" />
                            </node>
                          </node>
                          <node concept="2OqwBi" id="4zSRxm70O1U" role="33vP2m">
                            <node concept="2OqwBi" id="4zSRxm70v9L" role="2Oq$k0">
                              <node concept="2OqwBi" id="4zSRxm70pkJ" role="2Oq$k0">
                                <node concept="37vLTw" id="4zSRxm70nVG" role="2Oq$k0">
                                  <ref role="3cqZAo" node="4zSRxm6ZEdl" resolve="it" />
                                </node>
                                <node concept="3Tsc0h" id="4zSRxm70rS8" role="2OqNvi">
                                  <ref role="3TtcxE" to="tpce:2TR3acGo7N1" resolve="members" />
                                </node>
                              </node>
                              <node concept="3$u5V9" id="4zSRxm70ylp" role="2OqNvi">
                                <node concept="1bVj0M" id="4zSRxm70ylr" role="23t8la">
                                  <node concept="3clFbS" id="4zSRxm70yls" role="1bW5cS">
                                    <node concept="3cpWs8" id="2HDFhwS9wkk" role="3cqZAp">
                                      <node concept="3cpWsn" id="2HDFhwS9wkl" role="3cpWs9">
                                        <property role="TrG5h" value="presentation" />
                                        <node concept="3uibUv" id="2HDFhwS9wkm" role="1tU5fm">
                                          <ref role="3uigEE" to="wyt6:~String" resolve="String" />
                                        </node>
                                        <node concept="3K4zz7" id="2HDFhwS9OjA" role="33vP2m">
                                          <node concept="10Nm6u" id="2HDFhwS9QJZ" role="3K4E3e" />
                                          <node concept="2OqwBi" id="2HDFhwS9UbM" role="3K4GZi">
                                            <node concept="37vLTw" id="2HDFhwS9S9I" role="2Oq$k0">
                                              <ref role="3cqZAo" node="4zSRxm70ylt" resolve="it" />
                                            </node>
                                            <node concept="3TrcHB" id="2HDFhwS9X9S" role="2OqNvi">
                                              <ref role="3TsBF5" to="tpce:_jzzDSlxy8" resolve="presentation" />
                                            </node>
                                          </node>
                                          <node concept="2OqwBi" id="2HDFhwS9FnO" role="3K4Cdx">
                                            <node concept="2OqwBi" id="2HDFhwS9AgT" role="2Oq$k0">
                                              <node concept="37vLTw" id="2HDFhwS9$gi" role="2Oq$k0">
                                                <ref role="3cqZAo" node="4zSRxm70ylt" resolve="it" />
                                              </node>
                                              <node concept="3TrcHB" id="2HDFhwS9DnP" role="2OqNvi">
                                                <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                                              </node>
                                            </node>
                                            <node concept="liA8E" id="2HDFhwS9GXk" role="2OqNvi">
                                              <ref role="37wK5l" to="wyt6:~String.equals(java.lang.Object)" resolve="equals" />
                                              <node concept="2OqwBi" id="2HDFhwS9Kdp" role="37wK5m">
                                                <node concept="37vLTw" id="2HDFhwS9I9E" role="2Oq$k0">
                                                  <ref role="3cqZAo" node="4zSRxm70ylt" resolve="it" />
                                                </node>
                                                <node concept="3TrcHB" id="2HDFhwS9MX2" role="2OqNvi">
                                                  <ref role="3TsBF5" to="tpce:_jzzDSlxy8" resolve="presentation" />
                                                </node>
                                              </node>
                                            </node>
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                    <node concept="3clFbF" id="4zSRxm70zVx" role="3cqZAp">
                                      <node concept="2ShNRf" id="4zSRxm70zVv" role="3clFbG">
                                        <node concept="1pGfFk" id="4zSRxm70Bwh" role="2ShVmc">
                                          <ref role="37wK5l" to="sgfj:~EnumMemberData.&lt;init&gt;(java.lang.String,java.lang.String,java.lang.String)" resolve="EnumMemberData" />
                                          <node concept="2OqwBi" id="7ryKvClc3z$" role="37wK5m">
                                            <node concept="2ShNRf" id="7ryKvClbWLm" role="2Oq$k0">
                                              <node concept="1pGfFk" id="7ryKvClc1fD" role="2ShVmc">
                                                <ref role="37wK5l" to="w1kc:~JavaFriendlyBase64.&lt;init&gt;()" resolve="JavaFriendlyBase64" />
                                              </node>
                                            </node>
                                            <node concept="liA8E" id="7ryKvClc4X2" role="2OqNvi">
                                              <ref role="37wK5l" to="w1kc:~JavaFriendlyBase64.toString(long)" resolve="toString" />
                                              <node concept="2YIFZM" id="7ryKvClcjfc" role="37wK5m">
                                                <ref role="37wK5l" to="wyt6:~Long.parseLong(java.lang.String)" resolve="parseLong" />
                                                <ref role="1Pybhc" to="wyt6:~Long" resolve="Long" />
                                                <node concept="2OqwBi" id="7ryKvClcmPJ" role="37wK5m">
                                                  <node concept="37vLTw" id="7ryKvClclkY" role="2Oq$k0">
                                                    <ref role="3cqZAo" node="4zSRxm70ylt" resolve="it" />
                                                  </node>
                                                  <node concept="3TrcHB" id="7ryKvClcomT" role="2OqNvi">
                                                    <ref role="3TsBF5" to="tpce:1eSXJRel0SS" resolve="memberId" />
                                                  </node>
                                                </node>
                                              </node>
                                            </node>
                                          </node>
                                          <node concept="2OqwBi" id="1lNY4J8Wmi4" role="37wK5m">
                                            <node concept="37vLTw" id="1lNY4J8WkCE" role="2Oq$k0">
                                              <ref role="3cqZAo" node="4zSRxm70ylt" resolve="it" />
                                            </node>
                                            <node concept="3TrcHB" id="1lNY4J8WoMN" role="2OqNvi">
                                              <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                                            </node>
                                          </node>
                                          <node concept="37vLTw" id="2HDFhwSa3K6" role="37wK5m">
                                            <ref role="3cqZAo" node="2HDFhwS9wkl" resolve="presentation" />
                                          </node>
                                        </node>
                                      </node>
                                    </node>
                                  </node>
                                  <node concept="Rh6nW" id="4zSRxm70ylt" role="1bW2Oz">
                                    <property role="TrG5h" value="it" />
                                    <node concept="2jxLKc" id="4zSRxm70ylu" role="1tU5fm" />
                                  </node>
                                </node>
                              </node>
                            </node>
                            <node concept="ANE8D" id="4zSRxm70QDt" role="2OqNvi" />
                          </node>
                        </node>
                      </node>
                      <node concept="3cpWs8" id="5IyBvLCgrQX" role="3cqZAp">
                        <node concept="3cpWsn" id="5IyBvLCgrR0" role="3cpWs9">
                          <property role="TrG5h" value="defaultIndex" />
                          <node concept="10Oyi0" id="5IyBvLCgrQV" role="1tU5fm" />
                          <node concept="3K4zz7" id="5IyBvLCgL$2" role="33vP2m">
                            <node concept="2OqwBi" id="5IyBvLCgTVt" role="3K4Cdx">
                              <node concept="2OqwBi" id="5IyBvLCgPsx" role="2Oq$k0">
                                <node concept="37vLTw" id="5IyBvLCgNDb" role="2Oq$k0">
                                  <ref role="3cqZAo" node="4zSRxm6ZEdl" resolve="it" />
                                </node>
                                <node concept="3TrEf2" id="5IyBvLCgScL" role="2OqNvi">
                                  <ref role="3Tt5mk" to="tpce:VFd4XzZw5G" resolve="defaultMember" />
                                </node>
                              </node>
                              <node concept="3x8VRR" id="5IyBvLCgVqp" role="2OqNvi" />
                            </node>
                            <node concept="2OqwBi" id="5IyBvLCh2oL" role="3K4E3e">
                              <node concept="2OqwBi" id="5IyBvLCgYTU" role="2Oq$k0">
                                <node concept="37vLTw" id="5IyBvLCgXng" role="2Oq$k0">
                                  <ref role="3cqZAo" node="4zSRxm6ZEdl" resolve="it" />
                                </node>
                                <node concept="3TrEf2" id="5IyBvLCh0ty" role="2OqNvi">
                                  <ref role="3Tt5mk" to="tpce:VFd4XzZw5G" resolve="defaultMember" />
                                </node>
                              </node>
                              <node concept="2bSWHS" id="5IyBvLCh4Io" role="2OqNvi" />
                            </node>
                            <node concept="3cmrfG" id="5IyBvLCh60J" role="3K4GZi">
                              <property role="3cmrfH" value="0" />
                            </node>
                          </node>
                        </node>
                      </node>
                      <node concept="3clFbF" id="4zSRxm70TNt" role="3cqZAp">
                        <node concept="2ShNRf" id="4zSRxm70TNp" role="3clFbG">
                          <node concept="1pGfFk" id="4zSRxm70V1b" role="2ShVmc">
                            <ref role="37wK5l" to="sgfj:~EnumData.&lt;init&gt;(java.lang.String,java.lang.String,java.util.List,int,java.lang.String)" resolve="EnumData" />
                            <node concept="3cpWs3" id="5pDpbLMqabe" role="37wK5m">
                              <node concept="Xl_RD" id="5pDpbLMqceA" role="3uHU7B">
                                <property role="Xl_RC" value="mps:" />
                              </node>
                              <node concept="2OqwBi" id="5pDpbLMq80M" role="3uHU7w">
                                <node concept="2YIFZM" id="5pDpbLMq80K" role="2Oq$k0">
                                  <ref role="37wK5l" to="e8bb:~MetaIdByDeclaration.getDatatypeId(org.jetbrains.mps.openapi.model.SNode)" resolve="getDatatypeId" />
                                  <ref role="1Pybhc" to="e8bb:~MetaIdByDeclaration" resolve="MetaIdByDeclaration" />
                                  <node concept="37vLTw" id="5pDpbLMq80I" role="37wK5m">
                                    <ref role="3cqZAo" node="4zSRxm6ZEdl" resolve="it" />
                                  </node>
                                </node>
                                <node concept="liA8E" id="1lNY4J8WMKb" role="2OqNvi">
                                  <ref role="37wK5l" to="e8bb:~SElementId.toString()" resolve="toString" />
                                </node>
                              </node>
                            </node>
                            <node concept="2OqwBi" id="5IyBvLCjhgv" role="37wK5m">
                              <node concept="37vLTw" id="5IyBvLCjfN8" role="2Oq$k0">
                                <ref role="3cqZAo" node="4zSRxm6ZEdl" resolve="it" />
                              </node>
                              <node concept="3TrcHB" id="5IyBvLCjmnV" role="2OqNvi">
                                <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                              </node>
                            </node>
                            <node concept="37vLTw" id="1lNY4J8WWyB" role="37wK5m">
                              <ref role="3cqZAo" node="4zSRxm70fWa" resolve="members" />
                            </node>
                            <node concept="37vLTw" id="3lhG7y_rl7q" role="37wK5m">
                              <ref role="3cqZAo" node="5IyBvLCgrR0" resolve="defaultIndex" />
                            </node>
                            <node concept="1rXfSq" id="6leOzHDsENP" role="37wK5m">
                              <ref role="37wK5l" node="6leOzHDqtxp" resolve="deprecationMsg" />
                              <node concept="37vLTw" id="6leOzHDsGa8" role="37wK5m">
                                <ref role="3cqZAo" node="4zSRxm6ZEdl" resolve="it" />
                              </node>
                            </node>
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="Rh6nW" id="4zSRxm6ZEdl" role="1bW2Oz">
                      <property role="TrG5h" value="it" />
                      <node concept="2jxLKc" id="4zSRxm6ZEdm" role="1tU5fm" />
                    </node>
                  </node>
                </node>
              </node>
              <node concept="ANE8D" id="4zSRxm71dm0" role="2OqNvi" />
            </node>
          </node>
        </node>
        <node concept="3clFbH" id="4zSRxm6Zkqg" role="3cqZAp" />
        <node concept="3cpWs8" id="3Fg0S50fP2V" role="3cqZAp">
          <node concept="3cpWsn" id="3Fg0S50fP2W" role="3cpWs9">
            <property role="TrG5h" value="languageData" />
            <node concept="3uibUv" id="3Fg0S50fOHr" role="1tU5fm">
              <ref role="3uigEE" to="sgfj:~LanguageData" resolve="LanguageData" />
            </node>
            <node concept="2ShNRf" id="3Fg0S50fP2X" role="33vP2m">
              <node concept="1pGfFk" id="3Fg0S50fP2Y" role="2ShVmc">
                <ref role="37wK5l" to="sgfj:~LanguageData.&lt;init&gt;(java.lang.String,java.lang.String,java.util.List,java.util.List)" resolve="LanguageData" />
                <node concept="2OqwBi" id="sN$G5gkOhI" role="37wK5m">
                  <node concept="2YIFZM" id="sN$G5gkOuf" role="2Oq$k0">
                    <ref role="37wK5l" to="e8bb:~MetaIdByDeclaration.getLanguageId(jetbrains.mps.smodel.Language)" resolve="getLanguageId" />
                    <ref role="1Pybhc" to="e8bb:~MetaIdByDeclaration" resolve="MetaIdByDeclaration" />
                    <node concept="37vLTw" id="sN$G5gkOug" role="37wK5m">
                      <ref role="3cqZAo" node="3Fg0S50gf7e" resolve="languageModule" />
                    </node>
                  </node>
                  <node concept="liA8E" id="sN$G5gkOhL" role="2OqNvi">
                    <ref role="37wK5l" to="e8bb:~SLanguageId.toString()" resolve="toString" />
                  </node>
                </node>
                <node concept="2OqwBi" id="3Fg0S50fP2Z" role="37wK5m">
                  <node concept="37vLTw" id="3Fg0S50gDEP" role="2Oq$k0">
                    <ref role="3cqZAo" node="3Fg0S50gf7e" resolve="languageModule" />
                  </node>
                  <node concept="liA8E" id="3Fg0S50fP31" role="2OqNvi">
                    <ref role="37wK5l" to="z1c3:~AbstractModule.getModuleName()" resolve="getModuleName" />
                  </node>
                </node>
                <node concept="37vLTw" id="3Fg0S50fP32" role="37wK5m">
                  <ref role="3cqZAo" node="3Fg0S50cWmP" resolve="concepts" />
                </node>
                <node concept="37vLTw" id="4zSRxm6ZCSS" role="37wK5m">
                  <ref role="3cqZAo" node="4zSRxm6Znpo" resolve="enums" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="18fUb1nwUhP" role="3cqZAp">
          <node concept="37vLTI" id="18fUb1nwVKw" role="3clFbG">
            <node concept="37vLTw" id="18fUb1nwVWn" role="37vLTx">
              <ref role="3cqZAo" node="3Fg0S50fP2W" resolve="languageData" />
            </node>
            <node concept="3EllGN" id="18fUb1nwVtw" role="37vLTJ">
              <node concept="37vLTw" id="18fUb1nwVDa" role="3ElVtu">
                <ref role="3cqZAo" node="3Fg0S50gf7e" resolve="languageModule" />
              </node>
              <node concept="37vLTw" id="18fUb1nwUhN" role="3ElQJh">
                <ref role="3cqZAo" node="18fUb1nwRKI" resolve="producedData" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="18fUb1nxmv8" role="3cqZAp">
          <node concept="3cpWsn" id="18fUb1nxmv9" role="3cpWs9">
            <property role="TrG5h" value="jsonFile" />
            <node concept="3uibUv" id="18fUb1nxmva" role="1tU5fm">
              <ref role="3uigEE" to="guwi:~File" resolve="File" />
            </node>
            <node concept="2ShNRf" id="18fUb1nxq6N" role="33vP2m">
              <node concept="1pGfFk" id="18fUb1nxq6u" role="2ShVmc">
                <ref role="37wK5l" to="guwi:~File.&lt;init&gt;(java.io.File,java.lang.String)" resolve="File" />
                <node concept="37vLTw" id="TKTYk$fIHm" role="37wK5m">
                  <ref role="3cqZAo" node="3Fg0S50geDN" resolve="outputFolder" />
                </node>
                <node concept="3cpWs3" id="18fUb1nxqdW" role="37wK5m">
                  <node concept="Xl_RD" id="18fUb1nxqdX" role="3uHU7w">
                    <property role="Xl_RC" value=".json" />
                  </node>
                  <node concept="2OqwBi" id="18fUb1nxqdY" role="3uHU7B">
                    <node concept="37vLTw" id="18fUb1nxqdZ" role="2Oq$k0">
                      <ref role="3cqZAo" node="3Fg0S50fP2W" resolve="languageData" />
                    </node>
                    <node concept="liA8E" id="18fUb1nxqe0" role="2OqNvi">
                      <ref role="37wK5l" to="sgfj:~LanguageData.getName()" resolve="getName" />
                    </node>
                  </node>
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="TKTYk$dQf9" role="3cqZAp">
          <node concept="2YIFZM" id="TKTYk$dQgG" role="3clFbG">
            <ref role="37wK5l" to="4nxv:~FilesKt__FileReadWriteKt.writeText(java.io.File,java.lang.String,java.nio.charset.Charset)" resolve="writeText" />
            <ref role="1Pybhc" to="4nxv:~FilesKt" resolve="FilesKt" />
            <node concept="37vLTw" id="TKTYk$dQib" role="37wK5m">
              <ref role="3cqZAo" node="18fUb1nxmv9" resolve="jsonFile" />
            </node>
            <node concept="2OqwBi" id="TKTYk$dQlZ" role="37wK5m">
              <node concept="37vLTw" id="TKTYk$dQm0" role="2Oq$k0">
                <ref role="3cqZAo" node="3Fg0S50fP2W" resolve="languageData" />
              </node>
              <node concept="liA8E" id="TKTYk$dQm1" role="2OqNvi">
                <ref role="37wK5l" to="sgfj:~LanguageData.toJson()" resolve="toJson" />
              </node>
            </node>
            <node concept="10M0yZ" id="TKTYk$dQx0" role="37wK5m">
              <ref role="3cqZAo" to="7x5y:~StandardCharsets.UTF_8" resolve="UTF_8" />
              <ref role="1PxDUh" to="7x5y:~StandardCharsets" resolve="StandardCharsets" />
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="18fUb1nwW0k" role="jymVt" />
    <node concept="3clFb_" id="18fUb1nwXw8" role="jymVt">
      <property role="TrG5h" value="getOutput" />
      <node concept="_YKpA" id="18fUb1nx0v$" role="3clF45">
        <node concept="3uibUv" id="18fUb1nx1j3" role="_ZDj9">
          <ref role="3uigEE" to="sgfj:~LanguageData" resolve="LanguageData" />
        </node>
      </node>
      <node concept="3Tm1VV" id="18fUb1nwXwb" role="1B3o_S" />
      <node concept="3clFbS" id="18fUb1nwXwc" role="3clF47">
        <node concept="3clFbF" id="18fUb1nx3tW" role="3cqZAp">
          <node concept="2OqwBi" id="18fUb1nx4FK" role="3clFbG">
            <node concept="2OqwBi" id="18fUb1nx3SA" role="2Oq$k0">
              <node concept="37vLTw" id="18fUb1nx3tV" role="2Oq$k0">
                <ref role="3cqZAo" node="18fUb1nwRKI" resolve="producedData" />
              </node>
              <node concept="T8wYR" id="18fUb1nx4nl" role="2OqNvi" />
            </node>
            <node concept="ANE8D" id="18fUb1nx53q" role="2OqNvi" />
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="4VPKBwfzSxm" role="jymVt" />
    <node concept="3clFb_" id="TKTYk$gcfk" role="jymVt">
      <property role="TrG5h" value="linkTargetFqName" />
      <node concept="37vLTG" id="TKTYk$gjBn" role="3clF46">
        <property role="TrG5h" value="link" />
        <node concept="3Tqbb2" id="TKTYk$glrw" role="1tU5fm">
          <ref role="ehGHo" to="tpce:f_TJgxE" resolve="LinkDeclaration" />
        </node>
      </node>
      <node concept="17QB3L" id="TKTYk$gdM3" role="3clF45" />
      <node concept="3Tm1VV" id="TKTYk$gcfn" role="1B3o_S" />
      <node concept="3clFbS" id="TKTYk$gcfo" role="3clF47">
        <node concept="3cpWs8" id="TKTYk$gmCj" role="3cqZAp">
          <node concept="3cpWsn" id="TKTYk$gmCk" role="3cpWs9">
            <property role="TrG5h" value="target" />
            <node concept="3Tqbb2" id="TKTYk$gmvA" role="1tU5fm">
              <ref role="ehGHo" to="tpce:h0PkWnZ" resolve="AbstractConceptDeclaration" />
            </node>
            <node concept="2OqwBi" id="TKTYk$gmCl" role="33vP2m">
              <node concept="37vLTw" id="TKTYk$gmCm" role="2Oq$k0">
                <ref role="3cqZAo" node="TKTYk$gjBn" resolve="link" />
              </node>
              <node concept="3TrEf2" id="TKTYk$gmCn" role="2OqNvi">
                <ref role="3Tt5mk" to="tpce:fA0lvVK" resolve="target" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbJ" id="TKTYk$gmLT" role="3cqZAp">
          <node concept="3clFbS" id="TKTYk$gmLV" role="3clFbx">
            <node concept="3clFbF" id="TKTYk$gmQT" role="3cqZAp">
              <node concept="2OqwBi" id="TKTYk$gmQQ" role="3clFbG">
                <node concept="10M0yZ" id="TKTYk$gmQR" role="2Oq$k0">
                  <ref role="1PxDUh" to="wyt6:~System" resolve="System" />
                  <ref role="3cqZAo" to="wyt6:~System.out" resolve="out" />
                </node>
                <node concept="liA8E" id="TKTYk$gmQS" role="2OqNvi">
                  <ref role="37wK5l" to="guwi:~PrintStream.println(java.lang.String)" resolve="println" />
                  <node concept="3cpWs3" id="TKTYk$g$jx" role="37wK5m">
                    <node concept="Xl_RD" id="TKTYk$g$jV" role="3uHU7w">
                      <property role="Xl_RC" value=" has no target concept. Using BaseConcept instead." />
                    </node>
                    <node concept="3cpWs3" id="TKTYk$gynA" role="3uHU7B">
                      <node concept="3cpWs3" id="TKTYk$gy2q" role="3uHU7B">
                        <node concept="3cpWs3" id="TKTYk$gv_Z" role="3uHU7B">
                          <node concept="Xl_RD" id="TKTYk$guUe" role="3uHU7B">
                            <property role="Xl_RC" value="Link " />
                          </node>
                          <node concept="2OqwBi" id="TKTYk$gxpv" role="3uHU7w">
                            <node concept="1PxgMI" id="TKTYk$gwPv" role="2Oq$k0">
                              <property role="1BlNFB" value="true" />
                              <node concept="chp4Y" id="TKTYk$gx4i" role="3oSUPX">
                                <ref role="cht4Q" to="tpck:h0TrEE$" resolve="INamedConcept" />
                              </node>
                              <node concept="2OqwBi" id="TKTYk$gvQn" role="1m5AlR">
                                <node concept="37vLTw" id="TKTYk$gvAR" role="2Oq$k0">
                                  <ref role="3cqZAo" node="TKTYk$gjBn" resolve="link" />
                                </node>
                                <node concept="1mfA1w" id="TKTYk$gwuC" role="2OqNvi" />
                              </node>
                            </node>
                            <node concept="3TrcHB" id="TKTYk$gxK$" role="2OqNvi">
                              <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                            </node>
                          </node>
                        </node>
                        <node concept="Xl_RD" id="TKTYk$gy2O" role="3uHU7w">
                          <property role="Xl_RC" value="." />
                        </node>
                      </node>
                      <node concept="2OqwBi" id="TKTYk$gz4g" role="3uHU7w">
                        <node concept="37vLTw" id="TKTYk$gyL0" role="2Oq$k0">
                          <ref role="3cqZAo" node="TKTYk$gjBn" resolve="link" />
                        </node>
                        <node concept="3TrcHB" id="TKTYk$gzvO" role="2OqNvi">
                          <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
                        </node>
                      </node>
                    </node>
                  </node>
                </node>
              </node>
            </node>
            <node concept="3clFbF" id="TKTYk$gnkn" role="3cqZAp">
              <node concept="37vLTI" id="TKTYk$gnzo" role="3clFbG">
                <node concept="2OqwBi" id="TKTYk$goHZ" role="37vLTx">
                  <node concept="2tJFMh" id="TKTYk$goe5" role="2Oq$k0">
                    <node concept="ZC_QK" id="TKTYk$goiQ" role="2tJFKM">
                      <ref role="2aWVGs" to="tpck:gw2VY9q" resolve="BaseConcept" />
                    </node>
                  </node>
                  <node concept="Vyspw" id="TKTYk$gp3B" role="2OqNvi">
                    <node concept="2OqwBi" id="TKTYk$guuL" role="Vysub">
                      <node concept="2OqwBi" id="TKTYk$gu3Q" role="2Oq$k0">
                        <node concept="2JrnkZ" id="TKTYk$gtVR" role="2Oq$k0">
                          <node concept="2OqwBi" id="TKTYk$gt7N" role="2JrQYb">
                            <node concept="37vLTw" id="TKTYk$gsPQ" role="2Oq$k0">
                              <ref role="3cqZAo" node="TKTYk$gjBn" resolve="link" />
                            </node>
                            <node concept="I4A8Y" id="TKTYk$gtHW" role="2OqNvi" />
                          </node>
                        </node>
                        <node concept="liA8E" id="TKTYk$guhi" role="2OqNvi">
                          <ref role="37wK5l" to="mhbf:~SModel.getModule()" resolve="getModule" />
                        </node>
                      </node>
                      <node concept="liA8E" id="TKTYk$guS3" role="2OqNvi">
                        <ref role="37wK5l" to="lui2:~SModule.getRepository()" resolve="getRepository" />
                      </node>
                    </node>
                  </node>
                </node>
                <node concept="37vLTw" id="TKTYk$gnkl" role="37vLTJ">
                  <ref role="3cqZAo" node="TKTYk$gmCk" resolve="target" />
                </node>
              </node>
            </node>
          </node>
          <node concept="3clFbC" id="TKTYk$gn61" role="3clFbw">
            <node concept="10Nm6u" id="TKTYk$gng4" role="3uHU7w" />
            <node concept="37vLTw" id="TKTYk$gmNd" role="3uHU7B">
              <ref role="3cqZAo" node="TKTYk$gmCk" resolve="target" />
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="TKTYk$gA63" role="3cqZAp">
          <node concept="1rXfSq" id="TKTYk$gA60" role="3clFbG">
            <ref role="37wK5l" node="4VPKBwfzUho" resolve="fqName" />
            <node concept="37vLTw" id="TKTYk$gAoe" role="37wK5m">
              <ref role="3cqZAo" node="TKTYk$gmCk" resolve="target" />
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="3clFb_" id="4VPKBwfzUho" role="jymVt">
      <property role="TrG5h" value="fqName" />
      <node concept="37vLTG" id="4VPKBwfzWWf" role="3clF46">
        <property role="TrG5h" value="element" />
        <node concept="3Tqbb2" id="4VPKBwfzXYO" role="1tU5fm">
          <ref role="ehGHo" to="tpce:1ob16QT2yIl" resolve="INamedStructureElement" />
        </node>
      </node>
      <node concept="17QB3L" id="4VPKBwfzY64" role="3clF45" />
      <node concept="3Tm6S6" id="4VPKBwfzZvD" role="1B3o_S" />
      <node concept="3clFbS" id="4VPKBwfzUhs" role="3clF47">
        <node concept="3clFbF" id="4VPKBwf$0Wu" role="3cqZAp">
          <node concept="3cpWs3" id="4VPKBwf$3iC" role="3clFbG">
            <node concept="2OqwBi" id="4VPKBwf$3Fk" role="3uHU7w">
              <node concept="37vLTw" id="4VPKBwf$3kb" role="2Oq$k0">
                <ref role="3cqZAo" node="4VPKBwfzWWf" resolve="element" />
              </node>
              <node concept="3TrcHB" id="4VPKBwf$49b" role="2OqNvi">
                <ref role="3TsBF5" to="tpck:h0TrG11" resolve="name" />
              </node>
            </node>
            <node concept="3cpWs3" id="4VPKBwf$2Uu" role="3uHU7B">
              <node concept="2OqwBi" id="4VPKBwf$2ll" role="3uHU7B">
                <node concept="2OqwBi" id="4VPKBwf$215" role="2Oq$k0">
                  <node concept="2JrnkZ" id="4VPKBwf$1SK" role="2Oq$k0">
                    <node concept="2OqwBi" id="4VPKBwf$1at" role="2JrQYb">
                      <node concept="37vLTw" id="4VPKBwf$0Wt" role="2Oq$k0">
                        <ref role="3cqZAo" node="4VPKBwfzWWf" resolve="element" />
                      </node>
                      <node concept="I4A8Y" id="4VPKBwf$1CI" role="2OqNvi" />
                    </node>
                  </node>
                  <node concept="liA8E" id="4VPKBwf$2d8" role="2OqNvi">
                    <ref role="37wK5l" to="mhbf:~SModel.getModule()" resolve="getModule" />
                  </node>
                </node>
                <node concept="liA8E" id="4VPKBwf$2zf" role="2OqNvi">
                  <ref role="37wK5l" to="lui2:~SModule.getModuleName()" resolve="getModuleName" />
                </node>
              </node>
              <node concept="Xl_RD" id="4VPKBwf$2US" role="3uHU7w">
                <property role="Xl_RC" value="." />
              </node>
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="3clFb_" id="6leOzHDqtxp" role="jymVt">
      <property role="TrG5h" value="deprecationMsg" />
      <node concept="3clFbS" id="6leOzHDqtxs" role="3clF47">
        <node concept="3clFbJ" id="6leOzHDqC9S" role="3cqZAp">
          <node concept="3fqX7Q" id="6leOzHDr_RD" role="3clFbw">
            <node concept="2OqwBi" id="6leOzHDr_RF" role="3fr31v">
              <node concept="37vLTw" id="6leOzHDr_RG" role="2Oq$k0">
                <ref role="3cqZAo" node="6leOzHDq_Eo" resolve="node" />
              </node>
              <node concept="2qgKlT" id="6leOzHDr_RH" role="2OqNvi">
                <ref role="37wK5l" to="tpcu:hOwoPtR" resolve="isDeprecated" />
              </node>
            </node>
          </node>
          <node concept="3clFbS" id="6leOzHDqC9U" role="3clFbx">
            <node concept="3cpWs6" id="6leOzHDrBbR" role="3cqZAp">
              <node concept="10Nm6u" id="6leOzHDrDBv" role="3cqZAk" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6leOzHDrI5Q" role="3cqZAp">
          <node concept="3cpWsn" id="6leOzHDrI5T" role="3cpWs9">
            <property role="TrG5h" value="msg" />
            <node concept="17QB3L" id="6leOzHDrI5O" role="1tU5fm" />
            <node concept="2OqwBi" id="6leOzHDrS2E" role="33vP2m">
              <node concept="37vLTw" id="6leOzHDrQxZ" role="2Oq$k0">
                <ref role="3cqZAo" node="6leOzHDq_Eo" resolve="node" />
              </node>
              <node concept="2qgKlT" id="6leOzHDrToO" role="2OqNvi">
                <ref role="37wK5l" to="tpcu:hP43_8K" resolve="getMessage" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs6" id="6leOzHDrWAo" role="3cqZAp">
          <node concept="3K4zz7" id="6leOzHDs4b0" role="3cqZAk">
            <node concept="37vLTw" id="6leOzHDs5vt" role="3K4E3e">
              <ref role="3cqZAo" node="6leOzHDrI5T" resolve="msg" />
            </node>
            <node concept="Xl_RD" id="6leOzHDs7Ht" role="3K4GZi">
              <property role="Xl_RC" value="" />
            </node>
            <node concept="3y3z36" id="6leOzHDs0lI" role="3K4Cdx">
              <node concept="10Nm6u" id="6leOzHDs2_M" role="3uHU7w" />
              <node concept="37vLTw" id="6leOzHDrYOq" role="3uHU7B">
                <ref role="3cqZAo" node="6leOzHDrI5T" resolve="msg" />
              </node>
            </node>
          </node>
        </node>
      </node>
      <node concept="3Tm6S6" id="6leOzHDqs7t" role="1B3o_S" />
      <node concept="17QB3L" id="6leOzHDqtlk" role="3clF45" />
      <node concept="37vLTG" id="6leOzHDq_Eo" role="3clF46">
        <property role="TrG5h" value="node" />
        <node concept="3Tqbb2" id="6leOzHDq_En" role="1tU5fm">
          <ref role="ehGHo" to="tpck:hOwnYed" resolve="IDeprecatable" />
        </node>
      </node>
    </node>
    <node concept="3Tm1VV" id="3Fg0S50gerG" role="1B3o_S" />
  </node>
  <node concept="312cEu" id="6bQHiZUll2y">
    <property role="TrG5h" value="MPSModelExporter" />
    <node concept="312cEg" id="6bQHiZUll2z" role="jymVt">
      <property role="TrG5h" value="outputFolder" />
      <node concept="3Tm6S6" id="6bQHiZUll2$" role="1B3o_S" />
      <node concept="3uibUv" id="6bQHiZUll2_" role="1tU5fm">
        <ref role="3uigEE" to="guwi:~File" resolve="File" />
      </node>
    </node>
    <node concept="2tJIrI" id="6bQHiZUll2Q" role="jymVt" />
    <node concept="3clFbW" id="6bQHiZUll2R" role="jymVt">
      <node concept="3cqZAl" id="6bQHiZUll2S" role="3clF45" />
      <node concept="3Tm1VV" id="6bQHiZUll2T" role="1B3o_S" />
      <node concept="3clFbS" id="6bQHiZUll2U" role="3clF47">
        <node concept="3clFbF" id="6bQHiZUll2V" role="3cqZAp">
          <node concept="37vLTI" id="6bQHiZUll2W" role="3clFbG">
            <node concept="2OqwBi" id="6bQHiZUll2X" role="37vLTJ">
              <node concept="Xjq3P" id="6bQHiZUll2Y" role="2Oq$k0" />
              <node concept="2OwXpG" id="6bQHiZUll2Z" role="2OqNvi">
                <ref role="2Oxat5" node="6bQHiZUll2z" resolve="outputFolder" />
              </node>
            </node>
            <node concept="37vLTw" id="6bQHiZUll30" role="37vLTx">
              <ref role="3cqZAo" node="6bQHiZUll31" resolve="outputFolder" />
            </node>
          </node>
        </node>
      </node>
      <node concept="37vLTG" id="6bQHiZUll31" role="3clF46">
        <property role="TrG5h" value="outputFolder" />
        <node concept="3uibUv" id="6bQHiZUll32" role="1tU5fm">
          <ref role="3uigEE" to="guwi:~File" resolve="File" />
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="6bQHiZUnCrJ" role="jymVt" />
    <node concept="3clFb_" id="6bQHiZUoWgw" role="jymVt">
      <property role="TrG5h" value="exportModelWithDependencies" />
      <node concept="37vLTG" id="6bQHiZUpoOC" role="3clF46">
        <property role="TrG5h" value="model" />
        <node concept="3uibUv" id="6bQHiZUpq0j" role="1tU5fm">
          <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
        </node>
      </node>
      <node concept="3cqZAl" id="6bQHiZUoWgy" role="3clF45" />
      <node concept="3Tm1VV" id="6bQHiZUoWgz" role="1B3o_S" />
      <node concept="3clFbS" id="6bQHiZUoWg$" role="3clF47">
        <node concept="3cpWs8" id="6bQHiZUpY3o" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUpY3r" role="3cpWs9">
            <property role="TrG5h" value="models" />
            <node concept="2hMVRd" id="6bQHiZUpY3k" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUpYhf" role="2hN53Y">
                <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
              </node>
            </node>
            <node concept="2ShNRf" id="6bQHiZUpYVg" role="33vP2m">
              <node concept="2i4dXS" id="6bQHiZUpYSr" role="2ShVmc">
                <node concept="3uibUv" id="6bQHiZUpYSs" role="HW$YZ">
                  <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="6bQHiZUpZEN" role="3cqZAp">
          <node concept="1rXfSq" id="6bQHiZUpZEL" role="3clFbG">
            <ref role="37wK5l" node="6bQHiZUprGi" resolve="collectDependencies" />
            <node concept="37vLTw" id="6bQHiZUq0hW" role="37wK5m">
              <ref role="3cqZAo" node="6bQHiZUpoOC" resolve="model" />
            </node>
            <node concept="37vLTw" id="6bQHiZUq0S7" role="37wK5m">
              <ref role="3cqZAo" node="6bQHiZUpY3r" resolve="models" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUp3Xq" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUp3Xr" role="3cpWs9">
            <property role="TrG5h" value="modules" />
            <node concept="2hMVRd" id="6bQHiZUp3IO" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUp4Q9" role="2hN53Y">
                <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
              </node>
            </node>
            <node concept="2ShNRf" id="6bQHiZUp3Xs" role="33vP2m">
              <node concept="2i4dXS" id="6bQHiZUp3Xt" role="2ShVmc">
                <node concept="2OqwBi" id="6bQHiZUp3Xu" role="I$8f6">
                  <node concept="37vLTw" id="6bQHiZUp3Xv" role="2Oq$k0">
                    <ref role="3cqZAo" node="6bQHiZUpY3r" resolve="models" />
                  </node>
                  <node concept="3$u5V9" id="6bQHiZUp3Xw" role="2OqNvi">
                    <node concept="1bVj0M" id="6bQHiZUp3Xx" role="23t8la">
                      <node concept="3clFbS" id="6bQHiZUp3Xy" role="1bW5cS">
                        <node concept="3clFbF" id="6bQHiZUp3Xz" role="3cqZAp">
                          <node concept="2OqwBi" id="6bQHiZUp3X$" role="3clFbG">
                            <node concept="37vLTw" id="6bQHiZUp3X_" role="2Oq$k0">
                              <ref role="3cqZAo" node="6bQHiZUp3XB" resolve="it" />
                            </node>
                            <node concept="liA8E" id="6bQHiZUp3XA" role="2OqNvi">
                              <ref role="37wK5l" to="mhbf:~SModel.getModule()" resolve="getModule" />
                            </node>
                          </node>
                        </node>
                      </node>
                      <node concept="Rh6nW" id="6bQHiZUp3XB" role="1bW2Oz">
                        <property role="TrG5h" value="it" />
                        <node concept="2jxLKc" id="6bQHiZUp3XC" role="1tU5fm" />
                      </node>
                    </node>
                  </node>
                </node>
                <node concept="3uibUv" id="6bQHiZUp4zt" role="HW$YZ">
                  <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUpiWB" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUpiWC" role="3cpWs9">
            <property role="TrG5h" value="data" />
            <node concept="3uibUv" id="6bQHiZUpiSB" role="1tU5fm">
              <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
            </node>
            <node concept="1rXfSq" id="6bQHiZUpiWD" role="33vP2m">
              <ref role="37wK5l" node="6bQHiZUnQAl" resolve="exportModules" />
              <node concept="2OqwBi" id="6bQHiZUpiWE" role="37wK5m">
                <node concept="37vLTw" id="6bQHiZUpiWF" role="2Oq$k0">
                  <ref role="3cqZAo" node="6bQHiZUp3Xr" resolve="modules" />
                </node>
                <node concept="ANE8D" id="6bQHiZUpiWG" role="2OqNvi" />
              </node>
              <node concept="37vLTw" id="6bQHiZUpiWH" role="37wK5m">
                <ref role="3cqZAo" node="6bQHiZUpY3r" resolve="models" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="6bQHiZUpjDS" role="3cqZAp">
          <node concept="1rXfSq" id="6bQHiZUpjDP" role="3clFbG">
            <ref role="37wK5l" node="6bQHiZUoAyZ" resolve="writeFiles" />
            <node concept="2OqwBi" id="6bQHiZUq7Lm" role="37wK5m">
              <node concept="2OqwBi" id="6bQHiZUq7eW" role="2Oq$k0">
                <node concept="37vLTw" id="6bQHiZUq6LK" role="2Oq$k0">
                  <ref role="3cqZAo" node="6bQHiZUpoOC" resolve="model" />
                </node>
                <node concept="liA8E" id="6bQHiZUq7CF" role="2OqNvi">
                  <ref role="37wK5l" to="mhbf:~SModel.getName()" resolve="getName" />
                </node>
              </node>
              <node concept="liA8E" id="6bQHiZUq8yF" role="2OqNvi">
                <ref role="37wK5l" to="mhbf:~SModelName.getValue()" resolve="getValue" />
              </node>
            </node>
            <node concept="37vLTw" id="6bQHiZUq8Fo" role="37wK5m">
              <ref role="3cqZAo" node="6bQHiZUpiWC" resolve="data" />
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="7jUShhor7x2" role="jymVt" />
    <node concept="3clFb_" id="7jUShhor5dr" role="jymVt">
      <property role="TrG5h" value="exportModule" />
      <node concept="37vLTG" id="7jUShhor5ds" role="3clF46">
        <property role="TrG5h" value="module" />
        <node concept="3uibUv" id="7jUShhor5dt" role="1tU5fm">
          <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
        </node>
      </node>
      <node concept="3cqZAl" id="7jUShhor5du" role="3clF45" />
      <node concept="3Tm1VV" id="7jUShhor5dv" role="1B3o_S" />
      <node concept="3clFbS" id="7jUShhor5dw" role="3clF47">
        <node concept="3cpWs8" id="7jUShhor5dY" role="3cqZAp">
          <node concept="3cpWsn" id="7jUShhor5dZ" role="3cpWs9">
            <property role="TrG5h" value="data" />
            <node concept="3uibUv" id="7jUShhor5e0" role="1tU5fm">
              <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
            </node>
            <node concept="1rXfSq" id="7jUShhor5e1" role="33vP2m">
              <ref role="37wK5l" node="6bQHiZUnQAl" resolve="exportModules" />
              <node concept="2ShNRf" id="7jUShhorpO7" role="37wK5m">
                <node concept="Tc6Ow" id="7jUShhoroUB" role="2ShVmc">
                  <node concept="3uibUv" id="7jUShhoroUC" role="HW$YZ">
                    <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
                  </node>
                  <node concept="37vLTw" id="7jUShhortJz" role="HW$Y0">
                    <ref role="3cqZAo" node="7jUShhor5ds" resolve="module" />
                  </node>
                </node>
              </node>
              <node concept="10Nm6u" id="7jUShhork7I" role="37wK5m" />
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="7jUShhor5e6" role="3cqZAp">
          <node concept="1rXfSq" id="7jUShhor5e7" role="3clFbG">
            <ref role="37wK5l" node="6bQHiZUoAyZ" resolve="writeFiles" />
            <node concept="2OqwBi" id="7jUShhor5e9" role="37wK5m">
              <node concept="37vLTw" id="7jUShhor5ea" role="2Oq$k0">
                <ref role="3cqZAo" node="7jUShhor5ds" resolve="module" />
              </node>
              <node concept="liA8E" id="7jUShhor5eb" role="2OqNvi">
                <ref role="37wK5l" to="lui2:~SModule.getModuleName()" resolve="getModuleName" />
              </node>
            </node>
            <node concept="37vLTw" id="7jUShhor5ed" role="37wK5m">
              <ref role="3cqZAo" node="7jUShhor5dZ" resolve="data" />
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="6bQHiZUpqCC" role="jymVt" />
    <node concept="3clFb_" id="6bQHiZUprGi" role="jymVt">
      <property role="TrG5h" value="collectDependencies" />
      <node concept="37vLTG" id="6bQHiZUpxe6" role="3clF46">
        <property role="TrG5h" value="model" />
        <node concept="3uibUv" id="6bQHiZUpymx" role="1tU5fm">
          <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
        </node>
      </node>
      <node concept="37vLTG" id="6bQHiZUpyYj" role="3clF46">
        <property role="TrG5h" value="result" />
        <node concept="2hMVRd" id="6bQHiZUp$6S" role="1tU5fm">
          <node concept="3uibUv" id="6bQHiZUp$_n" role="2hN53Y">
            <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
          </node>
        </node>
      </node>
      <node concept="3cqZAl" id="6bQHiZUprGk" role="3clF45" />
      <node concept="3Tm6S6" id="6bQHiZUpsT3" role="1B3o_S" />
      <node concept="3clFbS" id="6bQHiZUprGm" role="3clF47">
        <node concept="3clFbJ" id="6bQHiZUp_rS" role="3cqZAp">
          <node concept="2OqwBi" id="6bQHiZUp_Kt" role="3clFbw">
            <node concept="37vLTw" id="6bQHiZUp_uW" role="2Oq$k0">
              <ref role="3cqZAo" node="6bQHiZUpyYj" resolve="result" />
            </node>
            <node concept="3JPx81" id="6bQHiZUpAB8" role="2OqNvi">
              <node concept="37vLTw" id="6bQHiZUpAIa" role="25WWJ7">
                <ref role="3cqZAo" node="6bQHiZUpxe6" resolve="model" />
              </node>
            </node>
          </node>
          <node concept="3clFbS" id="6bQHiZUp_rU" role="3clFbx">
            <node concept="3cpWs6" id="6bQHiZUpBdt" role="3cqZAp" />
          </node>
        </node>
        <node concept="3clFbF" id="6bQHiZUpBjR" role="3cqZAp">
          <node concept="2OqwBi" id="6bQHiZUpBul" role="3clFbG">
            <node concept="37vLTw" id="6bQHiZUpBjP" role="2Oq$k0">
              <ref role="3cqZAo" node="6bQHiZUpyYj" resolve="result" />
            </node>
            <node concept="TSZUe" id="6bQHiZUpCkK" role="2OqNvi">
              <node concept="37vLTw" id="6bQHiZUpCux" role="25WWJ7">
                <ref role="3cqZAo" node="6bQHiZUpxe6" resolve="model" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUpDNo" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUpDNp" role="3cpWs9">
            <property role="TrG5h" value="rootNodes" />
            <node concept="A3Dl8" id="6bQHiZUpES8" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUpESa" role="A3Ik2">
                <ref role="3uigEE" to="mhbf:~SNode" resolve="SNode" />
              </node>
            </node>
            <node concept="2OqwBi" id="6bQHiZUpDNq" role="33vP2m">
              <node concept="37vLTw" id="6bQHiZUpDNr" role="2Oq$k0">
                <ref role="3cqZAo" node="6bQHiZUpxe6" resolve="model" />
              </node>
              <node concept="liA8E" id="6bQHiZUpDNs" role="2OqNvi">
                <ref role="37wK5l" to="mhbf:~SModel.getRootNodes()" resolve="getRootNodes" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUpSCe" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUpSCf" role="3cpWs9">
            <property role="TrG5h" value="referencedModels" />
            <node concept="A3Dl8" id="6bQHiZUpStS" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUpStV" role="A3Ik2">
                <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
              </node>
            </node>
            <node concept="2OqwBi" id="6bQHiZUpUw_" role="33vP2m">
              <node concept="2OqwBi" id="6bQHiZUpSCg" role="2Oq$k0">
                <node concept="2OqwBi" id="6bQHiZUpSCh" role="2Oq$k0">
                  <node concept="2OqwBi" id="6bQHiZUpSCi" role="2Oq$k0">
                    <node concept="2OqwBi" id="6bQHiZUpSCj" role="2Oq$k0">
                      <node concept="2OqwBi" id="6bQHiZUpSCk" role="2Oq$k0">
                        <node concept="2OqwBi" id="6bQHiZUpSCl" role="2Oq$k0">
                          <node concept="37vLTw" id="6bQHiZUpSCm" role="2Oq$k0">
                            <ref role="3cqZAo" node="6bQHiZUpDNp" resolve="rootNodes" />
                          </node>
                          <node concept="UnYns" id="6bQHiZUpSCn" role="2OqNvi">
                            <node concept="3Tqbb2" id="6bQHiZUpSCo" role="UnYnz" />
                          </node>
                        </node>
                        <node concept="3goQfb" id="6bQHiZUpSCp" role="2OqNvi">
                          <node concept="1bVj0M" id="6bQHiZUpSCq" role="23t8la">
                            <node concept="3clFbS" id="6bQHiZUpSCr" role="1bW5cS">
                              <node concept="3clFbF" id="6bQHiZUpSCs" role="3cqZAp">
                                <node concept="2OqwBi" id="6bQHiZUpSCt" role="3clFbG">
                                  <node concept="37vLTw" id="6bQHiZUpSCu" role="2Oq$k0">
                                    <ref role="3cqZAo" node="6bQHiZUpSCy" resolve="it" />
                                  </node>
                                  <node concept="2Rf3mk" id="6bQHiZUpSCv" role="2OqNvi">
                                    <node concept="1xMEDy" id="6bQHiZUpSCw" role="1xVPHs">
                                      <node concept="chp4Y" id="6bQHiZUpSCx" role="ri$Ld">
                                        <ref role="cht4Q" to="tpck:gw2VY9q" resolve="BaseConcept" />
                                      </node>
                                    </node>
                                  </node>
                                </node>
                              </node>
                            </node>
                            <node concept="Rh6nW" id="6bQHiZUpSCy" role="1bW2Oz">
                              <property role="TrG5h" value="it" />
                              <node concept="2jxLKc" id="6bQHiZUpSCz" role="1tU5fm" />
                            </node>
                          </node>
                        </node>
                      </node>
                      <node concept="3goQfb" id="6bQHiZUpSC$" role="2OqNvi">
                        <node concept="1bVj0M" id="6bQHiZUpSC_" role="23t8la">
                          <node concept="3clFbS" id="6bQHiZUpSCA" role="1bW5cS">
                            <node concept="3clFbF" id="6bQHiZUpSCB" role="3cqZAp">
                              <node concept="2OqwBi" id="6bQHiZUpSCC" role="3clFbG">
                                <node concept="37vLTw" id="6bQHiZUpSCD" role="2Oq$k0">
                                  <ref role="3cqZAo" node="6bQHiZUpSCF" resolve="it" />
                                </node>
                                <node concept="2z74zc" id="6bQHiZUpSCE" role="2OqNvi" />
                              </node>
                            </node>
                          </node>
                          <node concept="Rh6nW" id="6bQHiZUpSCF" role="1bW2Oz">
                            <property role="TrG5h" value="it" />
                            <node concept="2jxLKc" id="6bQHiZUpSCG" role="1tU5fm" />
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="3$u5V9" id="6bQHiZUpSCH" role="2OqNvi">
                      <node concept="1bVj0M" id="6bQHiZUpSCI" role="23t8la">
                        <node concept="3clFbS" id="6bQHiZUpSCJ" role="1bW5cS">
                          <node concept="3clFbF" id="6bQHiZUpSCK" role="3cqZAp">
                            <node concept="2OqwBi" id="6bQHiZUpSCL" role="3clFbG">
                              <node concept="37vLTw" id="6bQHiZUpSCM" role="2Oq$k0">
                                <ref role="3cqZAo" node="6bQHiZUpSCO" resolve="it" />
                              </node>
                              <node concept="liA8E" id="6bQHiZUpSCN" role="2OqNvi">
                                <ref role="37wK5l" to="mhbf:~SReference.getTargetNode()" resolve="getTargetNode" />
                              </node>
                            </node>
                          </node>
                        </node>
                        <node concept="Rh6nW" id="6bQHiZUpSCO" role="1bW2Oz">
                          <property role="TrG5h" value="it" />
                          <node concept="2jxLKc" id="6bQHiZUpSCP" role="1tU5fm" />
                        </node>
                      </node>
                    </node>
                  </node>
                  <node concept="3zZkjj" id="6bQHiZUpSCQ" role="2OqNvi">
                    <node concept="1bVj0M" id="6bQHiZUpSCR" role="23t8la">
                      <node concept="3clFbS" id="6bQHiZUpSCS" role="1bW5cS">
                        <node concept="3clFbF" id="6bQHiZUpSCT" role="3cqZAp">
                          <node concept="3y3z36" id="6bQHiZUpSCU" role="3clFbG">
                            <node concept="10Nm6u" id="6bQHiZUpSCV" role="3uHU7w" />
                            <node concept="37vLTw" id="6bQHiZUpSCW" role="3uHU7B">
                              <ref role="3cqZAo" node="6bQHiZUpSCX" resolve="it" />
                            </node>
                          </node>
                        </node>
                      </node>
                      <node concept="Rh6nW" id="6bQHiZUpSCX" role="1bW2Oz">
                        <property role="TrG5h" value="it" />
                        <node concept="2jxLKc" id="6bQHiZUpSCY" role="1tU5fm" />
                      </node>
                    </node>
                  </node>
                </node>
                <node concept="3$u5V9" id="6bQHiZUpSCZ" role="2OqNvi">
                  <node concept="1bVj0M" id="6bQHiZUpSD0" role="23t8la">
                    <node concept="3clFbS" id="6bQHiZUpSD1" role="1bW5cS">
                      <node concept="3clFbF" id="6bQHiZUpSD2" role="3cqZAp">
                        <node concept="2OqwBi" id="6bQHiZUpSD3" role="3clFbG">
                          <node concept="37vLTw" id="6bQHiZUpSD4" role="2Oq$k0">
                            <ref role="3cqZAo" node="6bQHiZUpSD6" resolve="it" />
                          </node>
                          <node concept="liA8E" id="6bQHiZUpSD5" role="2OqNvi">
                            <ref role="37wK5l" to="mhbf:~SNode.getModel()" resolve="getModel" />
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="Rh6nW" id="6bQHiZUpSD6" role="1bW2Oz">
                      <property role="TrG5h" value="it" />
                      <node concept="2jxLKc" id="6bQHiZUpSD7" role="1tU5fm" />
                    </node>
                  </node>
                </node>
              </node>
              <node concept="1VAtEI" id="6bQHiZUpW21" role="2OqNvi" />
            </node>
          </node>
        </node>
        <node concept="2Gpval" id="6bQHiZUpWag" role="3cqZAp">
          <node concept="2GrKxI" id="6bQHiZUpWai" role="2Gsz3X">
            <property role="TrG5h" value="referencedModel" />
          </node>
          <node concept="37vLTw" id="6bQHiZUpW_M" role="2GsD0m">
            <ref role="3cqZAo" node="6bQHiZUpSCf" resolve="referencedModels" />
          </node>
          <node concept="3clFbS" id="6bQHiZUpWam" role="2LFqv$">
            <node concept="3clFbF" id="6bQHiZUpWNP" role="3cqZAp">
              <node concept="1rXfSq" id="6bQHiZUpWNO" role="3clFbG">
                <ref role="37wK5l" node="6bQHiZUprGi" resolve="collectDependencies" />
                <node concept="2GrUjf" id="6bQHiZUpXmc" role="37wK5m">
                  <ref role="2Gs0qQ" node="6bQHiZUpWai" resolve="referencedModel" />
                </node>
                <node concept="37vLTw" id="6bQHiZUpXvJ" role="37wK5m">
                  <ref role="3cqZAo" node="6bQHiZUpyYj" resolve="result" />
                </node>
              </node>
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="6bQHiZUoVma" role="jymVt" />
    <node concept="3clFb_" id="6bQHiZUoAyZ" role="jymVt">
      <property role="TrG5h" value="writeFiles" />
      <node concept="37vLTG" id="6bQHiZUoE14" role="3clF46">
        <property role="TrG5h" value="name" />
        <node concept="17QB3L" id="6bQHiZUoEP5" role="1tU5fm" />
      </node>
      <node concept="37vLTG" id="6bQHiZUoGaW" role="3clF46">
        <property role="TrG5h" value="nodeData" />
        <node concept="3uibUv" id="6bQHiZUoH2A" role="1tU5fm">
          <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
        </node>
      </node>
      <node concept="3cqZAl" id="6bQHiZUoAz1" role="3clF45" />
      <node concept="3Tm6S6" id="6bQHiZUoBld" role="1B3o_S" />
      <node concept="3clFbS" id="6bQHiZUoAz3" role="3clF47">
        <node concept="3cpWs8" id="6bQHiZUoL2F" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUoL2G" role="3cpWs9">
            <property role="TrG5h" value="data" />
            <node concept="3uibUv" id="6bQHiZUoKWh" role="1tU5fm">
              <ref role="3uigEE" to="sgfj:~ModelData" resolve="ModelData" />
            </node>
            <node concept="2ShNRf" id="6bQHiZUoL2H" role="33vP2m">
              <node concept="1pGfFk" id="6bQHiZUoL2I" role="2ShVmc">
                <ref role="37wK5l" to="sgfj:~ModelData.&lt;init&gt;(java.lang.String,org.modelix.model.data.NodeData)" resolve="ModelData" />
                <node concept="10Nm6u" id="6bQHiZUoL2J" role="37wK5m" />
                <node concept="37vLTw" id="6bQHiZUoL2K" role="37wK5m">
                  <ref role="3cqZAo" node="6bQHiZUoGaW" resolve="nodeData" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUnJmA" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUnJmB" role="3cpWs9">
            <property role="TrG5h" value="jsonFile" />
            <node concept="3uibUv" id="6bQHiZUnJmC" role="1tU5fm">
              <ref role="3uigEE" to="guwi:~File" resolve="File" />
            </node>
            <node concept="2ShNRf" id="6bQHiZUnJmD" role="33vP2m">
              <node concept="1pGfFk" id="6bQHiZUnJmE" role="2ShVmc">
                <ref role="37wK5l" to="guwi:~File.&lt;init&gt;(java.io.File,java.lang.String)" resolve="File" />
                <node concept="37vLTw" id="7jUShhopqPp" role="37wK5m">
                  <ref role="3cqZAo" node="6bQHiZUll2z" resolve="outputFolder" />
                </node>
                <node concept="3cpWs3" id="6bQHiZUnJmJ" role="37wK5m">
                  <node concept="Xl_RD" id="6bQHiZUnJmK" role="3uHU7w">
                    <property role="Xl_RC" value=".json" />
                  </node>
                  <node concept="37vLTw" id="6bQHiZUoKE1" role="3uHU7B">
                    <ref role="3cqZAo" node="6bQHiZUoE14" resolve="name" />
                  </node>
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="3HNwGULa$Uo" role="3cqZAp">
          <node concept="2YIFZM" id="3HNwGULa$Up" role="3clFbG">
            <ref role="1Pybhc" to="4nxv:~FilesKt" resolve="FilesKt" />
            <ref role="37wK5l" to="4nxv:~FilesKt__FileReadWriteKt.writeText(java.io.File,java.lang.String,java.nio.charset.Charset)" resolve="writeText" />
            <node concept="37vLTw" id="3HNwGULa$Uq" role="37wK5m">
              <ref role="3cqZAo" node="6bQHiZUnJmB" resolve="jsonFile" />
            </node>
            <node concept="2OqwBi" id="3HNwGULa$Ur" role="37wK5m">
              <node concept="37vLTw" id="3HNwGULa$Us" role="2Oq$k0">
                <ref role="3cqZAo" node="6bQHiZUoL2G" resolve="data" />
              </node>
              <node concept="liA8E" id="3HNwGULa$Ut" role="2OqNvi">
                <ref role="37wK5l" to="sgfj:~ModelData.toJson()" resolve="toJson" />
              </node>
            </node>
            <node concept="10M0yZ" id="3HNwGULa$Uu" role="37wK5m">
              <ref role="1PxDUh" to="7x5y:~StandardCharsets" resolve="StandardCharsets" />
              <ref role="3cqZAo" to="7x5y:~StandardCharsets.UTF_8" resolve="UTF_8" />
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="6bQHiZUo_Cu" role="jymVt" />
    <node concept="3clFb_" id="7jUShhopQyV" role="jymVt">
      <property role="TrG5h" value="exportModules" />
      <node concept="37vLTG" id="7jUShhopUYi" role="3clF46">
        <property role="TrG5h" value="modules" />
        <node concept="_YKpA" id="7jUShhopWw4" role="1tU5fm">
          <node concept="3uibUv" id="7jUShhopWWe" role="_ZDj9">
            <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
          </node>
        </node>
      </node>
      <node concept="3uibUv" id="7jUShhopXOn" role="3clF45">
        <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
      </node>
      <node concept="3Tm1VV" id="7jUShhopQyY" role="1B3o_S" />
      <node concept="3clFbS" id="7jUShhopQyZ" role="3clF47">
        <node concept="3clFbF" id="7jUShhopZGY" role="3cqZAp">
          <node concept="1rXfSq" id="7jUShhopZGX" role="3clFbG">
            <ref role="37wK5l" node="6bQHiZUnQAl" resolve="exportModules" />
            <node concept="37vLTw" id="7jUShhoq3cT" role="37wK5m">
              <ref role="3cqZAo" node="7jUShhopUYi" resolve="modules" />
            </node>
            <node concept="10Nm6u" id="7jUShhoq6Kl" role="37wK5m" />
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="7jUShhopOFY" role="jymVt" />
    <node concept="3clFb_" id="6bQHiZUnQAl" role="jymVt">
      <property role="TrG5h" value="exportModules" />
      <node concept="37vLTG" id="6bQHiZUnShk" role="3clF46">
        <property role="TrG5h" value="modules" />
        <node concept="_YKpA" id="6bQHiZUnSSC" role="1tU5fm">
          <node concept="3uibUv" id="6bQHiZUnT4N" role="_ZDj9">
            <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
          </node>
        </node>
      </node>
      <node concept="37vLTG" id="6bQHiZUp5kM" role="3clF46">
        <property role="TrG5h" value="modelsToInclude" />
        <node concept="2hMVRd" id="6bQHiZUp6dJ" role="1tU5fm">
          <node concept="3uibUv" id="6bQHiZUp6xO" role="2hN53Y">
            <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
          </node>
        </node>
        <node concept="2AHcQZ" id="7jUShhopJ_w" role="2AJF6D">
          <ref role="2AI5Lk" to="mhfm:~Nullable" resolve="Nullable" />
        </node>
      </node>
      <node concept="3uibUv" id="6bQHiZUpdr2" role="3clF45">
        <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
      </node>
      <node concept="3Tm6S6" id="6bQHiZUoTDX" role="1B3o_S" />
      <node concept="3clFbS" id="6bQHiZUnQAp" role="3clF47">
        <node concept="3cpWs8" id="6bQHiZUnYDW" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUnYDX" role="3cpWs9">
            <property role="TrG5h" value="modulesData" />
            <node concept="_YKpA" id="6bQHiZUnYDY" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUnYDZ" role="_ZDj9">
                <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
              </node>
            </node>
            <node concept="2OqwBi" id="6bQHiZUnYE0" role="33vP2m">
              <node concept="2OqwBi" id="6bQHiZUnYE1" role="2Oq$k0">
                <node concept="37vLTw" id="6bQHiZUnYE2" role="2Oq$k0">
                  <ref role="3cqZAo" node="6bQHiZUnShk" resolve="modules" />
                </node>
                <node concept="3$u5V9" id="6bQHiZUnYE3" role="2OqNvi">
                  <node concept="1bVj0M" id="6bQHiZUnYE4" role="23t8la">
                    <node concept="3clFbS" id="6bQHiZUnYE5" role="1bW5cS">
                      <node concept="3clFbF" id="6bQHiZUnYE6" role="3cqZAp">
                        <node concept="1rXfSq" id="6bQHiZUnYE7" role="3clFbG">
                          <ref role="37wK5l" node="6bQHiZUnJlf" resolve="exportModule" />
                          <node concept="37vLTw" id="6bQHiZUnYE8" role="37wK5m">
                            <ref role="3cqZAo" node="6bQHiZUnYE9" resolve="it" />
                          </node>
                          <node concept="37vLTw" id="6bQHiZUpcHn" role="37wK5m">
                            <ref role="3cqZAo" node="6bQHiZUp5kM" resolve="modelsToInclude" />
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="Rh6nW" id="6bQHiZUnYE9" role="1bW2Oz">
                      <property role="TrG5h" value="it" />
                      <node concept="2jxLKc" id="6bQHiZUnYEa" role="1tU5fm" />
                    </node>
                  </node>
                </node>
              </node>
              <node concept="ANE8D" id="6bQHiZUnYEb" role="2OqNvi" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUocoN" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUocoO" role="3cpWs9">
            <property role="TrG5h" value="root" />
            <node concept="3uibUv" id="6bQHiZUociY" role="1tU5fm">
              <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
            </node>
            <node concept="2ShNRf" id="6bQHiZUocoP" role="33vP2m">
              <node concept="1pGfFk" id="6bQHiZUocoQ" role="2ShVmc">
                <ref role="37wK5l" to="sgfj:~NodeData.&lt;init&gt;(java.lang.String,java.lang.String,java.lang.String,java.util.List,java.util.Map,java.util.Map)" resolve="NodeData" />
                <node concept="Xl_RD" id="6bQHiZUocoR" role="37wK5m">
                  <property role="Xl_RC" value="" />
                </node>
                <node concept="10Nm6u" id="6bQHiZUocoS" role="37wK5m" />
                <node concept="10Nm6u" id="6bQHiZUocoT" role="37wK5m" />
                <node concept="37vLTw" id="6bQHiZUocoU" role="37wK5m">
                  <ref role="3cqZAo" node="6bQHiZUnYDX" resolve="modulesData" />
                </node>
                <node concept="2YIFZM" id="6bQHiZUocoV" role="37wK5m">
                  <ref role="1Pybhc" to="33ny:~Collections" resolve="Collections" />
                  <ref role="37wK5l" to="33ny:~Collections.emptyMap()" resolve="emptyMap" />
                  <node concept="17QB3L" id="6bQHiZUocoW" role="3PaCim" />
                  <node concept="17QB3L" id="6bQHiZUocoX" role="3PaCim" />
                </node>
                <node concept="2YIFZM" id="6bQHiZUocoY" role="37wK5m">
                  <ref role="37wK5l" to="33ny:~Collections.emptyMap()" resolve="emptyMap" />
                  <ref role="1Pybhc" to="33ny:~Collections" resolve="Collections" />
                  <node concept="17QB3L" id="6bQHiZUocoZ" role="3PaCim" />
                  <node concept="17QB3L" id="6bQHiZUocp0" role="3PaCim" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs6" id="6bQHiZUpexJ" role="3cqZAp">
          <node concept="37vLTw" id="6bQHiZUpeH$" role="3cqZAk">
            <ref role="3cqZAo" node="6bQHiZUocoO" resolve="root" />
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="6bQHiZUnPRt" role="jymVt" />
    <node concept="3clFb_" id="6bQHiZUnJlf" role="jymVt">
      <property role="TrG5h" value="exportModule" />
      <node concept="37vLTG" id="6bQHiZUnJlg" role="3clF46">
        <property role="TrG5h" value="mpsModule" />
        <node concept="3uibUv" id="6bQHiZUnJlh" role="1tU5fm">
          <ref role="3uigEE" to="lui2:~SModule" resolve="SModule" />
        </node>
      </node>
      <node concept="37vLTG" id="6bQHiZUp8fi" role="3clF46">
        <property role="TrG5h" value="modelsToInclude" />
        <node concept="2hMVRd" id="6bQHiZUp8fj" role="1tU5fm">
          <node concept="3uibUv" id="6bQHiZUp8fk" role="2hN53Y">
            <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
          </node>
        </node>
        <node concept="2AHcQZ" id="7jUShhopsyp" role="2AJF6D">
          <ref role="2AI5Lk" to="mhfm:~Nullable" resolve="Nullable" />
        </node>
      </node>
      <node concept="3uibUv" id="6bQHiZUnWK3" role="3clF45">
        <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
      </node>
      <node concept="3Tm6S6" id="6bQHiZUoSO0" role="1B3o_S" />
      <node concept="3clFbS" id="6bQHiZUnJlk" role="3clF47">
        <node concept="3cpWs8" id="6bQHiZUnJll" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUnJlm" role="3cpWs9">
            <property role="TrG5h" value="models" />
            <node concept="A3Dl8" id="6bQHiZUnJln" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUnJlo" role="A3Ik2">
                <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
              </node>
            </node>
            <node concept="2OqwBi" id="6bQHiZUnJlp" role="33vP2m">
              <node concept="37vLTw" id="6bQHiZUnJlq" role="2Oq$k0">
                <ref role="3cqZAo" node="6bQHiZUnJlg" resolve="mpsModule" />
              </node>
              <node concept="liA8E" id="6bQHiZUnJlr" role="2OqNvi">
                <ref role="37wK5l" to="lui2:~SModule.getModels()" resolve="getModels" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUnJls" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUnJlt" role="3cpWs9">
            <property role="TrG5h" value="modelsData" />
            <node concept="_YKpA" id="6bQHiZUnJlu" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUnJlv" role="_ZDj9">
                <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
              </node>
            </node>
            <node concept="2OqwBi" id="6bQHiZUnJlw" role="33vP2m">
              <node concept="2OqwBi" id="6bQHiZUnJlx" role="2Oq$k0">
                <node concept="2OqwBi" id="6bQHiZUp9tW" role="2Oq$k0">
                  <node concept="37vLTw" id="6bQHiZUnJly" role="2Oq$k0">
                    <ref role="3cqZAo" node="6bQHiZUnJlm" resolve="models" />
                  </node>
                  <node concept="3zZkjj" id="6bQHiZUp9YV" role="2OqNvi">
                    <node concept="1bVj0M" id="6bQHiZUp9YX" role="23t8la">
                      <node concept="3clFbS" id="6bQHiZUp9YY" role="1bW5cS">
                        <node concept="3clFbF" id="6bQHiZUpam0" role="3cqZAp">
                          <node concept="22lmx$" id="7jUShhopCei" role="3clFbG">
                            <node concept="3clFbC" id="7jUShhopGrc" role="3uHU7B">
                              <node concept="10Nm6u" id="7jUShhopI0k" role="3uHU7w" />
                              <node concept="37vLTw" id="7jUShhopE1d" role="3uHU7B">
                                <ref role="3cqZAo" node="6bQHiZUp8fi" resolve="modelsToInclude" />
                              </node>
                            </node>
                            <node concept="2OqwBi" id="6bQHiZUpb2n" role="3uHU7w">
                              <node concept="37vLTw" id="6bQHiZUpalZ" role="2Oq$k0">
                                <ref role="3cqZAo" node="6bQHiZUp8fi" resolve="modelsToInclude" />
                              </node>
                              <node concept="3JPx81" id="6bQHiZUpbZT" role="2OqNvi">
                                <node concept="37vLTw" id="6bQHiZUpcep" role="25WWJ7">
                                  <ref role="3cqZAo" node="6bQHiZUp9YZ" resolve="it" />
                                </node>
                              </node>
                            </node>
                          </node>
                        </node>
                      </node>
                      <node concept="Rh6nW" id="6bQHiZUp9YZ" role="1bW2Oz">
                        <property role="TrG5h" value="it" />
                        <node concept="2jxLKc" id="6bQHiZUp9Z0" role="1tU5fm" />
                      </node>
                    </node>
                  </node>
                </node>
                <node concept="3$u5V9" id="6bQHiZUnJlz" role="2OqNvi">
                  <node concept="1bVj0M" id="6bQHiZUnJl$" role="23t8la">
                    <node concept="3clFbS" id="6bQHiZUnJl_" role="1bW5cS">
                      <node concept="3clFbF" id="6bQHiZUnJlA" role="3cqZAp">
                        <node concept="1rXfSq" id="6bQHiZUnJlB" role="3clFbG">
                          <ref role="37wK5l" node="6bQHiZUll3S" resolve="exportModel" />
                          <node concept="37vLTw" id="6bQHiZUnJlC" role="37wK5m">
                            <ref role="3cqZAo" node="6bQHiZUnJlD" resolve="it" />
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="Rh6nW" id="6bQHiZUnJlD" role="1bW2Oz">
                      <property role="TrG5h" value="it" />
                      <node concept="2jxLKc" id="6bQHiZUnJlE" role="1tU5fm" />
                    </node>
                  </node>
                </node>
              </node>
              <node concept="ANE8D" id="6bQHiZUnJlF" role="2OqNvi" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="77vieP7W4iR" role="3cqZAp">
          <node concept="3cpWsn" id="77vieP7W4iS" role="3cpWs9">
            <property role="TrG5h" value="properties" />
            <node concept="3rvAFt" id="77vieP7W4iT" role="1tU5fm">
              <node concept="17QB3L" id="77vieP7W4iU" role="3rvQeY" />
              <node concept="17QB3L" id="77vieP7W4iV" role="3rvSg0" />
            </node>
            <node concept="2ShNRf" id="77vieP7W4iW" role="33vP2m">
              <node concept="32Fmki" id="77vieP7W4iX" role="2ShVmc">
                <node concept="17QB3L" id="77vieP7W4iY" role="3rHrn6" />
                <node concept="17QB3L" id="77vieP7W4iZ" role="3rHtpV" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="77vieP7W4j0" role="3cqZAp">
          <node concept="37vLTI" id="77vieP7W4j1" role="3clFbG">
            <node concept="2OqwBi" id="77vieP7W4j2" role="37vLTx">
              <node concept="2OqwBi" id="77vieP7W4j3" role="2Oq$k0">
                <node concept="37vLTw" id="77vieP7W4j4" role="2Oq$k0">
                  <ref role="3cqZAo" node="6bQHiZUnJlg" resolve="mpsModule" />
                </node>
                <node concept="liA8E" id="77vieP7W4j5" role="2OqNvi">
                  <ref role="37wK5l" to="lui2:~SModule.getModuleId()" resolve="getModuleId" />
                </node>
              </node>
              <node concept="liA8E" id="77vieP7W4j6" role="2OqNvi">
                <ref role="37wK5l" to="wyt6:~Object.toString()" resolve="toString" />
              </node>
            </node>
            <node concept="3EllGN" id="77vieP7W4j7" role="37vLTJ">
              <node concept="Xl_RD" id="3HNwGUL6Srz" role="3ElVtu">
                <property role="Xl_RC" value="id" />
              </node>
              <node concept="37vLTw" id="77vieP7W4jb" role="3ElQJh">
                <ref role="3cqZAo" node="77vieP7W4iS" resolve="properties" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="77vieP7W4jc" role="3cqZAp">
          <node concept="37vLTI" id="77vieP7W4jd" role="3clFbG">
            <node concept="2OqwBi" id="77vieP7W4jf" role="37vLTx">
              <node concept="37vLTw" id="77vieP7W4jg" role="2Oq$k0">
                <ref role="3cqZAo" node="6bQHiZUnJlg" resolve="mpsModule" />
              </node>
              <node concept="liA8E" id="77vieP7W4jh" role="2OqNvi">
                <ref role="37wK5l" to="lui2:~SModule.getModuleName()" resolve="getModuleName" />
              </node>
            </node>
            <node concept="3EllGN" id="3HNwGUL6T03" role="37vLTJ">
              <node concept="Xl_RD" id="3HNwGUL6T8T" role="3ElVtu">
                <property role="Xl_RC" value="name" />
              </node>
              <node concept="37vLTw" id="77vieP7W4jn" role="3ElQJh">
                <ref role="3cqZAo" node="77vieP7W4iS" resolve="properties" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="6bQHiZUnJlG" role="3cqZAp">
          <node concept="2ShNRf" id="6bQHiZUnJlH" role="3clFbG">
            <node concept="1pGfFk" id="6bQHiZUnJlI" role="2ShVmc">
              <ref role="37wK5l" to="sgfj:~NodeData.&lt;init&gt;(java.lang.String,java.lang.String,java.lang.String,java.util.List,java.util.Map,java.util.Map)" resolve="NodeData" />
              <node concept="2OqwBi" id="6bQHiZUnJlJ" role="37wK5m">
                <node concept="2OqwBi" id="6bQHiZUnJlK" role="2Oq$k0">
                  <node concept="37vLTw" id="6bQHiZUnJlL" role="2Oq$k0">
                    <ref role="3cqZAo" node="6bQHiZUnJlg" resolve="mpsModule" />
                  </node>
                  <node concept="liA8E" id="6bQHiZUnJlM" role="2OqNvi">
                    <ref role="37wK5l" to="lui2:~SModule.getModuleReference()" resolve="getModuleReference" />
                  </node>
                </node>
                <node concept="liA8E" id="6bQHiZUnJlN" role="2OqNvi">
                  <ref role="37wK5l" to="wyt6:~Object.toString()" resolve="toString" />
                </node>
              </node>
              <node concept="Xl_RD" id="3HNwGUL9cuh" role="37wK5m">
                <property role="Xl_RC" value="mps-module" />
              </node>
              <node concept="Xl_RD" id="3HNwGUL72dl" role="37wK5m">
                <property role="Xl_RC" value="modules" />
              </node>
              <node concept="37vLTw" id="6bQHiZUnJlX" role="37wK5m">
                <ref role="3cqZAo" node="6bQHiZUnJlt" resolve="modelsData" />
              </node>
              <node concept="37vLTw" id="3jurMztiJip" role="37wK5m">
                <ref role="3cqZAo" node="77vieP7W4iS" resolve="properties" />
              </node>
              <node concept="2YIFZM" id="6bQHiZUnJm1" role="37wK5m">
                <ref role="37wK5l" to="33ny:~Collections.emptyMap()" resolve="emptyMap" />
                <ref role="1Pybhc" to="33ny:~Collections" resolve="Collections" />
                <node concept="17QB3L" id="6bQHiZUnJm2" role="3PaCim" />
                <node concept="17QB3L" id="6bQHiZUnJm3" role="3PaCim" />
              </node>
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="6bQHiZUnCy9" role="jymVt" />
    <node concept="3clFb_" id="6bQHiZUll3S" role="jymVt">
      <property role="TrG5h" value="exportModel" />
      <node concept="37vLTG" id="6bQHiZUll3T" role="3clF46">
        <property role="TrG5h" value="mpsModel" />
        <node concept="3uibUv" id="6bQHiZUll3U" role="1tU5fm">
          <ref role="3uigEE" to="mhbf:~SModel" resolve="SModel" />
        </node>
      </node>
      <node concept="3uibUv" id="6bQHiZUnMyI" role="3clF45">
        <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
      </node>
      <node concept="3Tm6S6" id="6bQHiZUoS0S" role="1B3o_S" />
      <node concept="3clFbS" id="6bQHiZUll3X" role="3clF47">
        <node concept="3cpWs8" id="6bQHiZUll4i" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUll4j" role="3cpWs9">
            <property role="TrG5h" value="rootNodes" />
            <node concept="A3Dl8" id="6bQHiZUll4k" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUll4l" role="A3Ik2">
                <ref role="3uigEE" to="mhbf:~SNode" resolve="SNode" />
              </node>
            </node>
            <node concept="2OqwBi" id="6bQHiZUll4m" role="33vP2m">
              <node concept="37vLTw" id="6bQHiZUll4n" role="2Oq$k0">
                <ref role="3cqZAo" node="6bQHiZUll3T" resolve="mpsModel" />
              </node>
              <node concept="liA8E" id="6bQHiZUll4o" role="2OqNvi">
                <ref role="37wK5l" to="mhbf:~SModel.getRootNodes()" resolve="getRootNodes" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUnvSY" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUnvSZ" role="3cpWs9">
            <property role="TrG5h" value="rootNodeData" />
            <node concept="_YKpA" id="6bQHiZUny2y" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUny2$" role="_ZDj9">
                <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
              </node>
            </node>
            <node concept="2OqwBi" id="6bQHiZUnx0M" role="33vP2m">
              <node concept="2OqwBi" id="6bQHiZUnvT0" role="2Oq$k0">
                <node concept="37vLTw" id="6bQHiZUnvT1" role="2Oq$k0">
                  <ref role="3cqZAo" node="6bQHiZUll4j" resolve="rootNodes" />
                </node>
                <node concept="3$u5V9" id="6bQHiZUnvT2" role="2OqNvi">
                  <node concept="1bVj0M" id="6bQHiZUnvT3" role="23t8la">
                    <node concept="3clFbS" id="6bQHiZUnvT4" role="1bW5cS">
                      <node concept="3clFbF" id="6bQHiZUnvT5" role="3cqZAp">
                        <node concept="1rXfSq" id="6bQHiZUnvT6" role="3clFbG">
                          <ref role="37wK5l" node="6bQHiZUm5Ov" resolve="exportNode" />
                          <node concept="37vLTw" id="6bQHiZUnvT7" role="37wK5m">
                            <ref role="3cqZAo" node="6bQHiZUnvT8" resolve="it" />
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="Rh6nW" id="6bQHiZUnvT8" role="1bW2Oz">
                      <property role="TrG5h" value="it" />
                      <node concept="2jxLKc" id="6bQHiZUnvT9" role="1tU5fm" />
                    </node>
                  </node>
                </node>
              </node>
              <node concept="ANE8D" id="6bQHiZUnxHQ" role="2OqNvi" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUotE$" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUotE_" role="3cpWs9">
            <property role="TrG5h" value="properties" />
            <node concept="3rvAFt" id="6bQHiZUotEA" role="1tU5fm">
              <node concept="17QB3L" id="6bQHiZUotEB" role="3rvQeY" />
              <node concept="17QB3L" id="6bQHiZUotEC" role="3rvSg0" />
            </node>
            <node concept="2ShNRf" id="6bQHiZUotED" role="33vP2m">
              <node concept="32Fmki" id="6bQHiZUotEE" role="2ShVmc">
                <node concept="17QB3L" id="6bQHiZUotEF" role="3rHrn6" />
                <node concept="17QB3L" id="6bQHiZUotEG" role="3rHtpV" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="6bQHiZUoxaG" role="3cqZAp">
          <node concept="37vLTI" id="6bQHiZUoxaH" role="3clFbG">
            <node concept="2OqwBi" id="6bQHiZUoy3Y" role="37vLTx">
              <node concept="2OqwBi" id="6bQHiZUoxaJ" role="2Oq$k0">
                <node concept="37vLTw" id="6bQHiZUoxaK" role="2Oq$k0">
                  <ref role="3cqZAo" node="6bQHiZUll3T" resolve="mpsModel" />
                </node>
                <node concept="liA8E" id="6bQHiZUoxaL" role="2OqNvi">
                  <ref role="37wK5l" to="mhbf:~SModel.getModelId()" resolve="getModelId" />
                </node>
              </node>
              <node concept="liA8E" id="6bQHiZUoymS" role="2OqNvi">
                <ref role="37wK5l" to="wyt6:~Object.toString()" resolve="toString" />
              </node>
            </node>
            <node concept="3EllGN" id="6bQHiZUoxaN" role="37vLTJ">
              <node concept="Xl_RD" id="3HNwGUL72o8" role="3ElVtu">
                <property role="Xl_RC" value="id" />
              </node>
              <node concept="37vLTw" id="6bQHiZUoxaR" role="3ElQJh">
                <ref role="3cqZAo" node="6bQHiZUotE_" resolve="properties" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="6bQHiZUotZP" role="3cqZAp">
          <node concept="37vLTI" id="6bQHiZUowb7" role="3clFbG">
            <node concept="2OqwBi" id="6bQHiZUowQc" role="37vLTx">
              <node concept="2OqwBi" id="6bQHiZUowxZ" role="2Oq$k0">
                <node concept="37vLTw" id="6bQHiZUownG" role="2Oq$k0">
                  <ref role="3cqZAo" node="6bQHiZUll3T" resolve="mpsModel" />
                </node>
                <node concept="liA8E" id="6bQHiZUowHK" role="2OqNvi">
                  <ref role="37wK5l" to="mhbf:~SModel.getName()" resolve="getName" />
                </node>
              </node>
              <node concept="liA8E" id="6bQHiZUox8S" role="2OqNvi">
                <ref role="37wK5l" to="mhbf:~SModelName.getValue()" resolve="getValue" />
              </node>
            </node>
            <node concept="3EllGN" id="6bQHiZUoux6" role="37vLTJ">
              <node concept="Xl_RD" id="3HNwGUL72xR" role="3ElVtu">
                <property role="Xl_RC" value="name" />
              </node>
              <node concept="37vLTw" id="6bQHiZUotZN" role="3ElQJh">
                <ref role="3cqZAo" node="6bQHiZUotE_" resolve="properties" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUo$qZ" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUo$r0" role="3cpWs9">
            <property role="TrG5h" value="data" />
            <node concept="3uibUv" id="6bQHiZUozpq" role="1tU5fm">
              <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
            </node>
            <node concept="2ShNRf" id="6bQHiZUo$r1" role="33vP2m">
              <node concept="1pGfFk" id="6bQHiZUo$r2" role="2ShVmc">
                <ref role="37wK5l" to="sgfj:~NodeData.&lt;init&gt;(java.lang.String,java.lang.String,java.lang.String,java.util.List,java.util.Map,java.util.Map)" resolve="NodeData" />
                <node concept="2OqwBi" id="6bQHiZUo$r3" role="37wK5m">
                  <node concept="2OqwBi" id="6bQHiZUo$r4" role="2Oq$k0">
                    <node concept="37vLTw" id="6bQHiZUo$r5" role="2Oq$k0">
                      <ref role="3cqZAo" node="6bQHiZUll3T" resolve="mpsModel" />
                    </node>
                    <node concept="liA8E" id="6bQHiZUo$r6" role="2OqNvi">
                      <ref role="37wK5l" to="mhbf:~SModel.getReference()" resolve="getReference" />
                    </node>
                  </node>
                  <node concept="liA8E" id="6bQHiZUo$r7" role="2OqNvi">
                    <ref role="37wK5l" to="wyt6:~Object.toString()" resolve="toString" />
                  </node>
                </node>
                <node concept="Xl_RD" id="3HNwGUL98Dd" role="37wK5m">
                  <property role="Xl_RC" value="mps-model" />
                </node>
                <node concept="Xl_RD" id="3HNwGUL74CC" role="37wK5m">
                  <property role="Xl_RC" value="models" />
                </node>
                <node concept="37vLTw" id="6bQHiZUo$rh" role="37wK5m">
                  <ref role="3cqZAo" node="6bQHiZUnvSZ" resolve="rootNodeData" />
                </node>
                <node concept="37vLTw" id="77vieP7W33b" role="37wK5m">
                  <ref role="3cqZAo" node="6bQHiZUotE_" resolve="properties" />
                </node>
                <node concept="2YIFZM" id="6bQHiZUo$rl" role="37wK5m">
                  <ref role="1Pybhc" to="33ny:~Collections" resolve="Collections" />
                  <ref role="37wK5l" to="33ny:~Collections.emptyMap()" resolve="emptyMap" />
                  <node concept="17QB3L" id="6bQHiZUo$rm" role="3PaCim" />
                  <node concept="17QB3L" id="6bQHiZUo$rn" role="3PaCim" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs6" id="6bQHiZUo_hO" role="3cqZAp">
          <node concept="37vLTw" id="6bQHiZUo_hQ" role="3cqZAk">
            <ref role="3cqZAo" node="6bQHiZUo$r0" resolve="data" />
          </node>
        </node>
      </node>
    </node>
    <node concept="2tJIrI" id="6bQHiZUnf3x" role="jymVt" />
    <node concept="3clFb_" id="6bQHiZUm5Ov" role="jymVt">
      <property role="TrG5h" value="exportNode" />
      <node concept="37vLTG" id="6bQHiZUmbCh" role="3clF46">
        <property role="TrG5h" value="node" />
        <node concept="3Tqbb2" id="6bQHiZUmwB7" role="1tU5fm" />
      </node>
      <node concept="3uibUv" id="6bQHiZUm$8W" role="3clF45">
        <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
      </node>
      <node concept="3Tm6S6" id="6bQHiZUoR34" role="1B3o_S" />
      <node concept="3clFbS" id="6bQHiZUm5Oz" role="3clF47">
        <node concept="3cpWs8" id="6bQHiZUmATQ" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUmATR" role="3cpWs9">
            <property role="TrG5h" value="id" />
            <node concept="17QB3L" id="6bQHiZUmBb$" role="1tU5fm" />
            <node concept="2OqwBi" id="6bQHiZUmATS" role="33vP2m">
              <node concept="2OqwBi" id="6bQHiZUmATT" role="2Oq$k0">
                <node concept="2JrnkZ" id="6bQHiZUmATU" role="2Oq$k0">
                  <node concept="37vLTw" id="6bQHiZUmATV" role="2JrQYb">
                    <ref role="3cqZAo" node="6bQHiZUmbCh" resolve="node" />
                  </node>
                </node>
                <node concept="liA8E" id="6bQHiZUntP0" role="2OqNvi">
                  <ref role="37wK5l" to="mhbf:~SNode.getReference()" resolve="getReference" />
                </node>
              </node>
              <node concept="liA8E" id="6bQHiZUmATX" role="2OqNvi">
                <ref role="37wK5l" to="wyt6:~Object.toString()" resolve="toString" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUmBOV" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUmBOW" role="3cpWs9">
            <property role="TrG5h" value="concept" />
            <node concept="17QB3L" id="6bQHiZUmC6n" role="1tU5fm" />
            <node concept="3cpWs3" id="4VEEpbhdqbO" role="33vP2m">
              <node concept="Xl_RD" id="4VEEpbhdqCp" role="3uHU7B">
                <property role="Xl_RC" value="mps:" />
              </node>
              <node concept="2OqwBi" id="21RTMdClfmr" role="3uHU7w">
                <node concept="2YIFZM" id="21RTMdCleKY" role="2Oq$k0">
                  <ref role="37wK5l" to="e8bb:~MetaIdHelper.getConcept(org.jetbrains.mps.openapi.language.SAbstractConcept)" resolve="getConcept" />
                  <ref role="1Pybhc" to="e8bb:~MetaIdHelper" resolve="MetaIdHelper" />
                  <node concept="2OqwBi" id="3HNwGUL8wqW" role="37wK5m">
                    <node concept="37vLTw" id="21RTMdClePy" role="2Oq$k0">
                      <ref role="3cqZAo" node="6bQHiZUmbCh" resolve="node" />
                    </node>
                    <node concept="2yIwOk" id="3HNwGUL8x6h" role="2OqNvi" />
                  </node>
                </node>
                <node concept="liA8E" id="21RTMdClfSY" role="2OqNvi">
                  <ref role="37wK5l" to="e8bb:~SConceptId.serialize()" resolve="serialize" />
                </node>
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUmCVd" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUmCVe" role="3cpWs9">
            <property role="TrG5h" value="role" />
            <node concept="17QB3L" id="6bQHiZUmDbj" role="1tU5fm" />
            <node concept="2EnYce" id="6bQHiZUmSpb" role="33vP2m">
              <node concept="2OqwBi" id="6bQHiZUmCVg" role="2Oq$k0">
                <node concept="2JrnkZ" id="6bQHiZUmCVh" role="2Oq$k0">
                  <node concept="37vLTw" id="6bQHiZUmCVi" role="2JrQYb">
                    <ref role="3cqZAo" node="6bQHiZUmbCh" resolve="node" />
                  </node>
                </node>
                <node concept="liA8E" id="6bQHiZUmCVj" role="2OqNvi">
                  <ref role="37wK5l" to="mhbf:~SNode.getContainmentLink()" resolve="getContainmentLink" />
                </node>
              </node>
              <node concept="liA8E" id="6bQHiZUmCVk" role="2OqNvi">
                <ref role="37wK5l" to="c17a:~SNamedElement.getName()" resolve="getName" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbJ" id="6bQHiZUmSRy" role="3cqZAp">
          <node concept="3clFbS" id="6bQHiZUmSR$" role="3clFbx">
            <node concept="3clFbF" id="6bQHiZUmUbN" role="3cqZAp">
              <node concept="37vLTI" id="6bQHiZUmUuM" role="3clFbG">
                <node concept="37vLTw" id="6bQHiZUmUbL" role="37vLTJ">
                  <ref role="3cqZAo" node="6bQHiZUmCVe" resolve="role" />
                </node>
                <node concept="Xl_RD" id="3HNwGUL8xh6" role="37vLTx">
                  <property role="Xl_RC" value="rootNodes" />
                </node>
              </node>
            </node>
          </node>
          <node concept="3clFbC" id="6bQHiZUmTHy" role="3clFbw">
            <node concept="10Nm6u" id="6bQHiZUmU7x" role="3uHU7w" />
            <node concept="37vLTw" id="6bQHiZUmTa$" role="3uHU7B">
              <ref role="3cqZAo" node="6bQHiZUmCVe" resolve="role" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUmAat" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUmAau" role="3cpWs9">
            <property role="TrG5h" value="children" />
            <node concept="_YKpA" id="6bQHiZUnelA" role="1tU5fm">
              <node concept="3uibUv" id="6bQHiZUnelC" role="_ZDj9">
                <ref role="3uigEE" to="sgfj:~NodeData" resolve="NodeData" />
              </node>
            </node>
            <node concept="2OqwBi" id="6bQHiZUnduJ" role="33vP2m">
              <node concept="2OqwBi" id="6bQHiZUmAav" role="2Oq$k0">
                <node concept="2OqwBi" id="6bQHiZUmAaw" role="2Oq$k0">
                  <node concept="37vLTw" id="6bQHiZUmAax" role="2Oq$k0">
                    <ref role="3cqZAo" node="6bQHiZUmbCh" resolve="node" />
                  </node>
                  <node concept="32TBzR" id="6bQHiZUmAay" role="2OqNvi" />
                </node>
                <node concept="3$u5V9" id="6bQHiZUmAaz" role="2OqNvi">
                  <node concept="1bVj0M" id="6bQHiZUmAa$" role="23t8la">
                    <node concept="3clFbS" id="6bQHiZUmAa_" role="1bW5cS">
                      <node concept="3clFbF" id="6bQHiZUmAaA" role="3cqZAp">
                        <node concept="1rXfSq" id="6bQHiZUmAaB" role="3clFbG">
                          <ref role="37wK5l" node="6bQHiZUm5Ov" resolve="exportNode" />
                          <node concept="37vLTw" id="6bQHiZUmAaC" role="37wK5m">
                            <ref role="3cqZAo" node="6bQHiZUmAaD" resolve="it" />
                          </node>
                        </node>
                      </node>
                    </node>
                    <node concept="Rh6nW" id="6bQHiZUmAaD" role="1bW2Oz">
                      <property role="TrG5h" value="it" />
                      <node concept="2jxLKc" id="6bQHiZUmAaE" role="1tU5fm" />
                    </node>
                  </node>
                </node>
              </node>
              <node concept="ANE8D" id="6bQHiZUneb9" role="2OqNvi" />
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUmGBk" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUmGBn" role="3cpWs9">
            <property role="TrG5h" value="properties" />
            <node concept="3rvAFt" id="6bQHiZUmGBe" role="1tU5fm">
              <node concept="17QB3L" id="6bQHiZUmHcW" role="3rvQeY" />
              <node concept="17QB3L" id="6bQHiZUmHn4" role="3rvSg0" />
            </node>
            <node concept="2ShNRf" id="6bQHiZUmH6H" role="33vP2m">
              <node concept="32Fmki" id="6bQHiZUmH5s" role="2ShVmc">
                <node concept="17QB3L" id="6bQHiZUmO6Q" role="3rHrn6" />
                <node concept="17QB3L" id="6bQHiZUmOt3" role="3rHtpV" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3cpWs8" id="6bQHiZUn0Kf" role="3cqZAp">
          <node concept="3cpWsn" id="6bQHiZUn0Kg" role="3cpWs9">
            <property role="TrG5h" value="references" />
            <node concept="3rvAFt" id="6bQHiZUn0Kh" role="1tU5fm">
              <node concept="17QB3L" id="6bQHiZUn0Ki" role="3rvQeY" />
              <node concept="17QB3L" id="6bQHiZUn0Kj" role="3rvSg0" />
            </node>
            <node concept="2ShNRf" id="6bQHiZUn0Kk" role="33vP2m">
              <node concept="32Fmki" id="6bQHiZUn0Kl" role="2ShVmc">
                <node concept="17QB3L" id="6bQHiZUn0Km" role="3rHrn6" />
                <node concept="17QB3L" id="6bQHiZUn0Kn" role="3rHtpV" />
              </node>
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="69o4C9gMofQ" role="3cqZAp">
          <node concept="37vLTI" id="69o4C9gMpJk" role="3clFbG">
            <node concept="2OqwBi" id="69o4C9gMs13" role="37vLTx">
              <node concept="2OqwBi" id="69o4C9gMrgA" role="2Oq$k0">
                <node concept="2JrnkZ" id="69o4C9gMqD8" role="2Oq$k0">
                  <node concept="37vLTw" id="69o4C9gMq4X" role="2JrQYb">
                    <ref role="3cqZAo" node="6bQHiZUmbCh" resolve="node" />
                  </node>
                </node>
                <node concept="liA8E" id="69o4C9gMrQN" role="2OqNvi">
                  <ref role="37wK5l" to="mhbf:~SNode.getNodeId()" resolve="getNodeId" />
                </node>
              </node>
              <node concept="liA8E" id="69o4C9gMs$h" role="2OqNvi">
                <ref role="37wK5l" to="wyt6:~Object.toString()" resolve="toString" />
              </node>
            </node>
            <node concept="3EllGN" id="69o4C9gMoZP" role="37vLTJ">
              <node concept="Xl_RD" id="69o4C9gMpmN" role="3ElVtu">
                <property role="Xl_RC" value="#mpsNodeId#" />
              </node>
              <node concept="37vLTw" id="69o4C9gMofO" role="3ElQJh">
                <ref role="3cqZAo" node="6bQHiZUmGBn" resolve="properties" />
              </node>
            </node>
          </node>
        </node>
        <node concept="2Gpval" id="6bQHiZUmHJW" role="3cqZAp">
          <node concept="2GrKxI" id="6bQHiZUmHJY" role="2Gsz3X">
            <property role="TrG5h" value="property" />
          </node>
          <node concept="3clFbS" id="6bQHiZUmHK2" role="2LFqv$">
            <node concept="3clFbF" id="6bQHiZUmI$e" role="3cqZAp">
              <node concept="37vLTI" id="6bQHiZUmKKx" role="3clFbG">
                <node concept="2OqwBi" id="6bQHiZUmLSX" role="37vLTx">
                  <node concept="2JrnkZ" id="6bQHiZUmLu6" role="2Oq$k0">
                    <node concept="37vLTw" id="6bQHiZUmLaH" role="2JrQYb">
                      <ref role="3cqZAo" node="6bQHiZUmbCh" resolve="node" />
                    </node>
                  </node>
                  <node concept="liA8E" id="6bQHiZUmMeI" role="2OqNvi">
                    <ref role="37wK5l" to="mhbf:~SNode.getProperty(org.jetbrains.mps.openapi.language.SProperty)" resolve="getProperty" />
                    <node concept="2GrUjf" id="6bQHiZUmN6z" role="37wK5m">
                      <ref role="2Gs0qQ" node="6bQHiZUmHJY" resolve="property" />
                    </node>
                  </node>
                </node>
                <node concept="3EllGN" id="6bQHiZUmITh" role="37vLTJ">
                  <node concept="2OqwBi" id="6bQHiZUmJdQ" role="3ElVtu">
                    <node concept="2GrUjf" id="6bQHiZUmIVp" role="2Oq$k0">
                      <ref role="2Gs0qQ" node="6bQHiZUmHJY" resolve="property" />
                    </node>
                    <node concept="liA8E" id="6bQHiZUmKet" role="2OqNvi">
                      <ref role="37wK5l" to="c17a:~SProperty.getName()" resolve="getName" />
                    </node>
                  </node>
                  <node concept="37vLTw" id="6bQHiZUmI$d" role="3ElQJh">
                    <ref role="3cqZAo" node="6bQHiZUmGBn" resolve="properties" />
                  </node>
                </node>
              </node>
            </node>
          </node>
          <node concept="2OqwBi" id="6bQHiZUmIe3" role="2GsD0m">
            <node concept="2JrnkZ" id="6bQHiZUmIe4" role="2Oq$k0">
              <node concept="37vLTw" id="6bQHiZUmIe5" role="2JrQYb">
                <ref role="3cqZAo" node="6bQHiZUmbCh" resolve="node" />
              </node>
            </node>
            <node concept="liA8E" id="6bQHiZUmIe6" role="2OqNvi">
              <ref role="37wK5l" to="mhbf:~SNode.getProperties()" resolve="getProperties" />
            </node>
          </node>
        </node>
        <node concept="2Gpval" id="6bQHiZUmYSj" role="3cqZAp">
          <node concept="2GrKxI" id="6bQHiZUmYSk" role="2Gsz3X">
            <property role="TrG5h" value="reference" />
          </node>
          <node concept="3clFbS" id="6bQHiZUmYSl" role="2LFqv$">
            <node concept="3clFbF" id="6bQHiZUmYSm" role="3cqZAp">
              <node concept="37vLTI" id="6bQHiZUmYSn" role="3clFbG">
                <node concept="2EnYce" id="6bQHiZUn7LL" role="37vLTx">
                  <node concept="2OqwBi" id="6bQHiZUn5s5" role="2Oq$k0">
                    <node concept="2GrUjf" id="6bQHiZUn53V" role="2Oq$k0">
                      <ref role="2Gs0qQ" node="6bQHiZUmYSk" resolve="reference" />
                    </node>
                    <node concept="liA8E" id="6bQHiZUn63m" role="2OqNvi">
                      <ref role="37wK5l" to="mhbf:~SReference.getTargetNodeReference()" resolve="getTargetNodeReference" />
                    </node>
                  </node>
                  <node concept="liA8E" id="6bQHiZUn78S" role="2OqNvi">
                    <ref role="37wK5l" to="wyt6:~Object.toString()" resolve="toString" />
                  </node>
                </node>
                <node concept="3EllGN" id="6bQHiZUmYSt" role="37vLTJ">
                  <node concept="2OqwBi" id="6bQHiZUn38e" role="3ElVtu">
                    <node concept="2OqwBi" id="6bQHiZUmYSu" role="2Oq$k0">
                      <node concept="2GrUjf" id="6bQHiZUmYSv" role="2Oq$k0">
                        <ref role="2Gs0qQ" node="6bQHiZUmYSk" resolve="reference" />
                      </node>
                      <node concept="liA8E" id="6bQHiZUn2uV" role="2OqNvi">
                        <ref role="37wK5l" to="mhbf:~SReference.getLink()" resolve="getLink" />
                      </node>
                    </node>
                    <node concept="liA8E" id="6bQHiZUn4fM" role="2OqNvi">
                      <ref role="37wK5l" to="c17a:~SNamedElement.getName()" resolve="getName" />
                    </node>
                  </node>
                  <node concept="37vLTw" id="6bQHiZUmYSx" role="3ElQJh">
                    <ref role="3cqZAo" node="6bQHiZUn0Kg" resolve="references" />
                  </node>
                </node>
              </node>
            </node>
          </node>
          <node concept="2OqwBi" id="6bQHiZUmYSy" role="2GsD0m">
            <node concept="2JrnkZ" id="6bQHiZUmYSz" role="2Oq$k0">
              <node concept="37vLTw" id="6bQHiZUmYS$" role="2JrQYb">
                <ref role="3cqZAo" node="6bQHiZUmbCh" resolve="node" />
              </node>
            </node>
            <node concept="liA8E" id="6bQHiZUmYS_" role="2OqNvi">
              <ref role="37wK5l" to="mhbf:~SNode.getReferences()" resolve="getReferences" />
            </node>
          </node>
        </node>
        <node concept="3clFbF" id="6bQHiZUmdDd" role="3cqZAp">
          <node concept="2ShNRf" id="6bQHiZUmdDb" role="3clFbG">
            <node concept="1pGfFk" id="6bQHiZUmoSj" role="2ShVmc">
              <ref role="37wK5l" to="sgfj:~NodeData.&lt;init&gt;(java.lang.String,java.lang.String,java.lang.String,java.util.List,java.util.Map,java.util.Map)" resolve="NodeData" />
              <node concept="37vLTw" id="6bQHiZUmATY" role="37wK5m">
                <ref role="3cqZAo" node="6bQHiZUmATR" resolve="id" />
              </node>
              <node concept="37vLTw" id="6bQHiZUmBP5" role="37wK5m">
                <ref role="3cqZAo" node="6bQHiZUmBOW" resolve="concept" />
              </node>
              <node concept="37vLTw" id="6bQHiZUmCVl" role="37wK5m">
                <ref role="3cqZAo" node="6bQHiZUmCVe" resolve="role" />
              </node>
              <node concept="37vLTw" id="6bQHiZUmAoe" role="37wK5m">
                <ref role="3cqZAo" node="6bQHiZUmAau" resolve="children" />
              </node>
              <node concept="37vLTw" id="6bQHiZUn8dv" role="37wK5m">
                <ref role="3cqZAo" node="6bQHiZUmGBn" resolve="properties" />
              </node>
              <node concept="37vLTw" id="6bQHiZUn8k9" role="37wK5m">
                <ref role="3cqZAo" node="6bQHiZUn0Kg" resolve="references" />
              </node>
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="3Tm1VV" id="6bQHiZUll9E" role="1B3o_S" />
  </node>
</model>
