<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:65082adb-125b-47d8-9352-00278e453492(GraphSolution2.example)">
  <persistence version="9" />
  <languages>
    <use id="d5dabe27-fe41-4a5d-92bb-aede71707287" name="GraphLang" version="0" />
    <use id="83888646-71ce-4f1c-9c53-c54016f6ad4f" name="jetbrains.mps.baseLanguage.collections" version="1" />
    <use id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage" version="11" />
    <devkit ref="508c56cf-0851-4930-8aac-ed733da47058(jetbrains.mps.execution.devkit)" />
  </languages>
  <imports>
    <import index="c5sg" ref="r:64fb7a52-94bd-43f1-a6e6-395dcf17eaae(GraphSolution.example)" />
    <import index="6g4x" ref="r:c00d0fd6-74f0-4b31-a6dd-81b87903406e(GraphLang.constraints)" />
    <import index="d3eg" ref="r:4d98040c-0120-46b2-9920-a3d1b395cc17(GraphLang.editor)" />
    <import index="7quj" ref="r:da9d3bd2-acb9-4ab0-addf-bcae1a76de67(GraphLang.structure)" />
    <import index="5sl" ref="r:ecda8711-ed27-4fcc-907a-148f823ede07(GraphLang.behavior)" />
  </imports>
  <registry>
    <language id="d5dabe27-fe41-4a5d-92bb-aede71707287" name="GraphLang">
      <concept id="466301921131629394" name="GraphLang.structure.Graph" flags="ng" index="1DmExM">
        <reference id="2449363414496655092" name="relatedGraph" index="1R5dnS" />
      </concept>
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ng" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
  </registry>
  <node concept="1DmExM" id="27XSKLmUrj6">
    <property role="TrG5h" value="GraphFromSolution2" />
    <ref role="1R5dnS" to="c5sg:pSCM1J8y9y" resolve="GraphFromSolution1" />
  </node>
</model>
