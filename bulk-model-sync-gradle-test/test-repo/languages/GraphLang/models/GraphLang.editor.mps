<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:4d98040c-0120-46b2-9920-a3d1b395cc17(GraphLang.editor)">
  <persistence version="9" />
  <languages>
    <use id="18bc6592-03a6-4e29-a83a-7ff23bde13ba" name="jetbrains.mps.lang.editor" version="14" />
    <use id="aee9cad2-acd4-4608-aef2-0004f6a1cdbd" name="jetbrains.mps.lang.actions" version="4" />
    <devkit ref="fbc25dd2-5da4-483a-8b19-70928e1b62d7(jetbrains.mps.devkit.general-purpose)" />
  </languages>
  <imports>
    <import index="7quj" ref="r:da9d3bd2-acb9-4ab0-addf-bcae1a76de67(GraphLang.structure)" implicit="true" />
    <import index="tpck" ref="r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)" implicit="true" />
  </imports>
  <registry>
    <language id="18bc6592-03a6-4e29-a83a-7ff23bde13ba" name="jetbrains.mps.lang.editor">
      <concept id="1071666914219" name="jetbrains.mps.lang.editor.structure.ConceptEditorDeclaration" flags="ig" index="24kQdi" />
      <concept id="1140524381322" name="jetbrains.mps.lang.editor.structure.CellModel_ListWithRole" flags="ng" index="2czfm3">
        <child id="1140524464360" name="cellLayout" index="2czzBx" />
      </concept>
      <concept id="1106270549637" name="jetbrains.mps.lang.editor.structure.CellLayout_Horizontal" flags="nn" index="2iRfu4" />
      <concept id="1106270571710" name="jetbrains.mps.lang.editor.structure.CellLayout_Vertical" flags="nn" index="2iRkQZ" />
      <concept id="1237303669825" name="jetbrains.mps.lang.editor.structure.CellLayout_Indent" flags="nn" index="l2Vlx" />
      <concept id="1237307900041" name="jetbrains.mps.lang.editor.structure.IndentLayoutIndentStyleClassItem" flags="ln" index="lj46D" />
      <concept id="1237385578942" name="jetbrains.mps.lang.editor.structure.IndentLayoutOnNewLineStyleClassItem" flags="ln" index="pVoyu" />
      <concept id="1080736578640" name="jetbrains.mps.lang.editor.structure.BaseEditorComponent" flags="ig" index="2wURMF">
        <child id="1080736633877" name="cellModel" index="2wV5jI" />
      </concept>
      <concept id="1186414536763" name="jetbrains.mps.lang.editor.structure.BooleanStyleSheetItem" flags="ln" index="VOi$J">
        <property id="1186414551515" name="flag" index="VOm3f" />
      </concept>
      <concept id="1088013125922" name="jetbrains.mps.lang.editor.structure.CellModel_RefCell" flags="sg" stub="730538219795941030" index="1iCGBv">
        <child id="1088186146602" name="editorComponent" index="1sWHZn" />
      </concept>
      <concept id="1088185857835" name="jetbrains.mps.lang.editor.structure.InlineEditorComponent" flags="ig" index="1sVBvm" />
      <concept id="1139848536355" name="jetbrains.mps.lang.editor.structure.CellModel_WithRole" flags="ng" index="1$h60E">
        <property id="1140017977771" name="readOnly" index="1Intyy" />
        <reference id="1140103550593" name="relationDeclaration" index="1NtTu8" />
      </concept>
      <concept id="1073389446423" name="jetbrains.mps.lang.editor.structure.CellModel_Collection" flags="sn" stub="3013115976261988961" index="3EZMnI">
        <child id="1106270802874" name="cellLayout" index="2iSdaV" />
        <child id="1073389446424" name="childCellModel" index="3EZMnx" />
      </concept>
      <concept id="1073389577006" name="jetbrains.mps.lang.editor.structure.CellModel_Constant" flags="sn" stub="3610246225209162225" index="3F0ifn">
        <property id="1073389577007" name="text" index="3F0ifm" />
      </concept>
      <concept id="1073389658414" name="jetbrains.mps.lang.editor.structure.CellModel_Property" flags="sg" stub="730538219796134133" index="3F0A7n" />
      <concept id="1219418625346" name="jetbrains.mps.lang.editor.structure.IStyleContainer" flags="ng" index="3F0Thp">
        <child id="1219418656006" name="styleItem" index="3F10Kt" />
      </concept>
      <concept id="1073390211982" name="jetbrains.mps.lang.editor.structure.CellModel_RefNodeList" flags="sg" stub="2794558372793454595" index="3F2HdR" />
      <concept id="1166049232041" name="jetbrains.mps.lang.editor.structure.AbstractComponent" flags="ng" index="1XWOmA">
        <reference id="1166049300910" name="conceptDeclaration" index="1XX52x" />
      </concept>
    </language>
  </registry>
  <node concept="24kQdi" id="pSCM1J8wWP">
    <ref role="1XX52x" to="7quj:pSCM1J8wHn" resolve="Edge" />
    <node concept="3EZMnI" id="pSCM1J8wX1" role="2wV5jI">
      <node concept="2iRfu4" id="pSCM1J8wX4" role="2iSdaV" />
      <node concept="1iCGBv" id="pSCM1J8CUR" role="3EZMnx">
        <ref role="1NtTu8" to="7quj:pSCM1J8CUP" resolve="source" />
        <node concept="1sVBvm" id="pSCM1J8CUT" role="1sWHZn">
          <node concept="3F0A7n" id="pSCM1J8CUX" role="2wV5jI">
            <property role="1Intyy" value="true" />
            <ref role="1NtTu8" to="tpck:h0TrG11" resolve="name" />
          </node>
        </node>
      </node>
      <node concept="3F0ifn" id="pSCM1J8CV0" role="3EZMnx">
        <property role="3F0ifm" value="-&gt;" />
      </node>
      <node concept="1iCGBv" id="pSCM1J8CV3" role="3EZMnx">
        <ref role="1NtTu8" to="7quj:pSCM1J8CUQ" resolve="target" />
        <node concept="1sVBvm" id="pSCM1J8CV5" role="1sWHZn">
          <node concept="3F0A7n" id="pSCM1J8CV9" role="2wV5jI">
            <property role="1Intyy" value="true" />
            <ref role="1NtTu8" to="tpck:h0TrG11" resolve="name" />
          </node>
        </node>
      </node>
    </node>
  </node>
  <node concept="24kQdi" id="pSCM1J8y9E">
    <ref role="1XX52x" to="7quj:pSCM1J8wHi" resolve="Graph" />
    <node concept="3EZMnI" id="pSCM1J8y9G" role="2wV5jI">
      <node concept="3F0ifn" id="pSCM1J8BUq" role="3EZMnx">
        <property role="3F0ifm" value="graph:" />
      </node>
      <node concept="3F0A7n" id="27XSKLmUjrz" role="3EZMnx">
        <ref role="1NtTu8" to="tpck:h0TrG11" resolve="name" />
      </node>
      <node concept="3F0ifn" id="pSCM1J8CVd" role="3EZMnx">
        <property role="3F0ifm" value="nodes:" />
        <node concept="pVoyu" id="pSCM1J8CVg" role="3F10Kt">
          <property role="VOm3f" value="true" />
        </node>
        <node concept="lj46D" id="pSCM1J8CVh" role="3F10Kt">
          <property role="VOm3f" value="true" />
        </node>
      </node>
      <node concept="3F2HdR" id="pSCM1J8CVj" role="3EZMnx">
        <ref role="1NtTu8" to="7quj:pSCM1J8CUp" resolve="nodes" />
        <node concept="l2Vlx" id="pSCM1J8CVm" role="2czzBx" />
      </node>
      <node concept="3F0ifn" id="pSCM1J8CVq" role="3EZMnx">
        <property role="3F0ifm" value="edges:" />
        <node concept="pVoyu" id="pSCM1J8CVu" role="3F10Kt">
          <property role="VOm3f" value="true" />
        </node>
        <node concept="lj46D" id="pSCM1J8CVv" role="3F10Kt">
          <property role="VOm3f" value="true" />
        </node>
      </node>
      <node concept="l2Vlx" id="pSCM1J8y9J" role="2iSdaV" />
      <node concept="3F2HdR" id="pSCM1J8BUu" role="3EZMnx">
        <ref role="1NtTu8" to="7quj:pSCM1J8wWH" resolve="edges" />
        <node concept="2iRkQZ" id="pSCM1J8BUx" role="2czzBx" />
        <node concept="pVoyu" id="pSCM1J8BUy" role="3F10Kt">
          <property role="VOm3f" value="true" />
        </node>
        <node concept="pVoyu" id="pSCM1J8CVw" role="3F10Kt">
          <property role="VOm3f" value="true" />
        </node>
        <node concept="lj46D" id="pSCM1J8CVx" role="3F10Kt">
          <property role="VOm3f" value="true" />
        </node>
      </node>
      <node concept="3F0ifn" id="27XSKLmUmXu" role="3EZMnx">
        <property role="3F0ifm" value="related graph:" />
        <node concept="pVoyu" id="27XSKLmUmXK" role="3F10Kt">
          <property role="VOm3f" value="true" />
        </node>
      </node>
      <node concept="1iCGBv" id="27XSKLmUmYZ" role="3EZMnx">
        <ref role="1NtTu8" to="7quj:27XSKLmUjrO" resolve="relatedGraph" />
        <node concept="1sVBvm" id="27XSKLmUmZ1" role="1sWHZn">
          <node concept="3F0A7n" id="27XSKLmUmZp" role="2wV5jI">
            <property role="1Intyy" value="true" />
            <ref role="1NtTu8" to="tpck:h0TrG11" resolve="name" />
          </node>
        </node>
      </node>
    </node>
  </node>
  <node concept="24kQdi" id="pSCM1J8zlz">
    <ref role="1XX52x" to="7quj:pSCM1J8wHk" resolve="Node" />
    <node concept="3F0A7n" id="pSCM1J8zlE" role="2wV5jI">
      <ref role="1NtTu8" to="tpck:h0TrG11" resolve="name" />
    </node>
  </node>
</model>
