<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:368d034d-6155-4955-9a72-127d324732cc(NewSolution.b_model)">
  <persistence version="9" />
  <languages>
    <use id="96c7c023-6829-44d0-b358-661f058f1c31" name="NewLanguage" version="-1" />
    <devkit ref="568b2514-a608-45b5-aea5-6ffd231cea1d(NewDevkit)" />
  </languages>
  <imports />
  <registry>
    <language id="96c7c023-6829-44d0-b358-661f058f1c31" name="NewLanguage">
      <concept id="8281020627045179518" name="NewLanguage.structure.MyChild" flags="ng" index="36mvws">
        <property id="8281020627045236732" name="value" index="36mEuu" />
      </concept>
      <concept id="8281020627045179517" name="NewLanguage.structure.MyRoot" flags="ng" index="36mvwv">
        <child id="8281020627045179519" name="children" index="36mvwt" />
      </concept>
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ng" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
  </registry>
  <node concept="36mvwv" id="7bG66aOHFZT">
    <property role="TrG5h" value="MyRootAbc" />
    <node concept="36mvws" id="7bG66aOHFZU" role="36mvwt">
      <property role="36mEuu" value="123" />
    </node>
  </node>
</model>
