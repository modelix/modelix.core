<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:cd78e6ac-0e34-490a-9b49-e5643f948d6d(NewSolution.a_model)">
 <persistence version="9" />
  <languages>
    <use id="e2840528-cf1a-4707-9968-32c55e0e5b6c" name="NewLanguage" version="0" />
  </languages>
  <imports />
  <registry>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1196978630214" name="jetbrains.mps.lang.core.structure.IResolveInfo" flags="ng" index="2Lv6Xg">
        <property id="1196978656277" name="resolveInfo" index="2Lvdk3" />
      </concept>
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ng" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
    <language id="e2840528-cf1a-4707-9968-32c55e0e5b6c" name="NewLanguage">
      <concept id="4030135827843012252" name="NewLanguage.structure.RootNode" flags="ng" index="3SLrQM">
        <child id="4030135827843012255" name="referencedNode" index="3SLrQL" />
        <child id="4030135827843012253" name="referencingNodes" index="3SLrQN" />
      </concept>
      <concept id="4030135827842946229" name="NewLanguage.structure.ReferencingNode" flags="ng" index="3SMFYr">
        <reference id="4030135827843004992" name="aReference" index="3SLt5I" />
      </concept>
      <concept id="4030135827842946260" name="NewLanguage.structure.ReferencedNodeWithResolveInfo" flags="ng" index="3SMFZU" />
      <concept id="4030135827842946256" name="NewLanguage.structure.ReferencedNodeWithName" flags="ng" index="3SMFZY" />
    </language>
  </registry>
  <node concept="3SLrQM" id="3vHUMVfa5C_">
    <node concept="3SMFYr" id="3vHUMVfa0RX" role="3SLrQN" >
      <ref role="3SLt5I" node="3vHUMVfa0RY" resolve="referencedNodeA" />
    </node>
    <node concept="3SMFZY" id="3vHUMVfa0RY" role="3SLrQL">
      <property role="TrG5h" value="referencedNodeA" />
    </node>
    <node concept="3SMFZU" id="3vHUMVfa0RZ" role="3SLrQL">
      <property role="2Lvdk3" value="referencedNodeC" />
    </node>
    <node concept="3SMFYr" id="3vHUMVfa4pM" role="3SLrQN">
      <ref role="3SLt5I" node="3vHUMVfa0RZ" resolve="referencedNodeC" />
    </node>
  </node>
</model>
