<?xml version="1.0" encoding="UTF-8"?>
<solution name="org.modelix.metamodel.export" uuid="e52a4421-48a2-4de1-8327-d9414e799c67" moduleVersion="0" pluginKind="PLUGIN_OTHER" compileInMPS="true">
  <models>
    <modelRoot contentPath="${module}" type="default">
      <sourceRoot location="models" />
    </modelRoot>
    <modelRoot contentPath="${module}/lib" type="java_classes">
      <sourceRoot location="metamodel-generator.jar" />
      <sourceRoot location="metamodel-runtime-jvm.jar" />
      <sourceRoot location="model-api-jvm.jar" />
      <sourceRoot location="kaml-jvm.jar" />
      <sourceRoot location="kotlin-logging-jvm.jar" />
      <sourceRoot location="kotlin-reflect.jar" />
      <sourceRoot location="kotlin-stdlib-common.jar" />
      <sourceRoot location="kotlin-stdlib-jdk7.jar" />
      <sourceRoot location="kotlin-stdlib-jdk8.jar" />
      <sourceRoot location="kotlin-stdlib.jar" />
      <sourceRoot location="kotlinpoet.jar" />
      <sourceRoot location="kotlinx-collections-immutable-jvm.jar" />
      <sourceRoot location="kotlinx-serialization-core-jvm.jar" />
      <sourceRoot location="kotlinx-serialization-json-jvm.jar" />
      <sourceRoot location="snakeyaml-engine.jar" />
    </modelRoot>
  </models>
  <facets>
    <facet type="java">
      <classes generated="true" path="${module}/classes_gen" />
    </facet>
  </facets>
  <stubModelEntries>
    <stubModelEntry path="${module}/lib/metamodel-generator.jar" />
    <stubModelEntry path="${module}/lib/metamodel-runtime-jvm.jar" />
    <stubModelEntry path="${module}/lib/model-api-jvm.jar" />
    <stubModelEntry path="${module}/lib/kaml-jvm.jar" />
    <stubModelEntry path="${module}/lib/kotlin-logging-jvm.jar" />
    <stubModelEntry path="${module}/lib/kotlin-reflect.jar" />
    <stubModelEntry path="${module}/lib/kotlin-stdlib-common.jar" />
    <stubModelEntry path="${module}/lib/kotlin-stdlib-jdk7.jar" />
    <stubModelEntry path="${module}/lib/kotlin-stdlib-jdk8.jar" />
    <stubModelEntry path="${module}/lib/kotlin-stdlib.jar" />
    <stubModelEntry path="${module}/lib/kotlinpoet.jar" />
    <stubModelEntry path="${module}/lib/kotlinx-collections-immutable-jvm.jar" />
    <stubModelEntry path="${module}/lib/kotlinx-serialization-core-jvm.jar" />
    <stubModelEntry path="${module}/lib/kotlinx-serialization-json-jvm.jar" />
    <stubModelEntry path="${module}/lib/snakeyaml-engine.jar" />
  </stubModelEntries>
  <sourcePath />
  <dependencies>
    <dependency reexport="false">6354ebe7-c22a-4a0f-ac54-50b52ab9b065(JDK)</dependency>
    <dependency reexport="false">fdaaf35f-8ee3-4c37-b09d-9efaeaaa7a41(jetbrains.mps.core.tool.environment)</dependency>
    <dependency reexport="false">8865b7a8-5271-43d3-884c-6fd1d9cfdd34(MPS.OpenAPI)</dependency>
    <dependency reexport="false">ceab5195-25ea-4f22-9b92-103b95ca8c0c(jetbrains.mps.lang.core)</dependency>
    <dependency reexport="false">c72da2b9-7cce-4447-8389-f407dc1158b7(jetbrains.mps.lang.structure)</dependency>
    <dependency reexport="false">6ed54515-acc8-4d1e-a16c-9fd6cfe951ea(MPS.Core)</dependency>
  </dependencies>
  <languageVersions>
    <language slang="l:f3061a53-9226-4cc5-a443-f952ceaf5816:jetbrains.mps.baseLanguage" version="11" />
    <language slang="l:774bf8a0-62e5-41e1-af63-f4812e60e48b:jetbrains.mps.baseLanguage.checkedDots" version="0" />
    <language slang="l:fd392034-7849-419d-9071-12563d152375:jetbrains.mps.baseLanguage.closures" version="0" />
    <language slang="l:83888646-71ce-4f1c-9c53-c54016f6ad4f:jetbrains.mps.baseLanguage.collections" version="1" />
    <language slang="l:f2801650-65d5-424e-bb1b-463a8781b786:jetbrains.mps.baseLanguage.javadoc" version="2" />
    <language slang="l:760a0a8c-eabb-4521-8bfd-65db761a9ba3:jetbrains.mps.baseLanguage.logging" version="0" />
    <language slang="l:ceab5195-25ea-4f22-9b92-103b95ca8c0c:jetbrains.mps.lang.core" version="2" />
    <language slang="l:446c26eb-2b7b-4bf0-9b35-f83fa582753e:jetbrains.mps.lang.modelapi" version="0" />
    <language slang="l:7866978e-a0f0-4cc7-81bc-4d213d9375e1:jetbrains.mps.lang.smodel" version="17" />
    <language slang="l:9ded098b-ad6a-4657-bfd9-48636cfe8bc3:jetbrains.mps.lang.traceable" version="0" />
  </languageVersions>
  <dependencyVersions>
    <module reference="3f233e7f-b8a6-46d2-a57f-795d56775243(Annotations)" version="0" />
    <module reference="6354ebe7-c22a-4a0f-ac54-50b52ab9b065(JDK)" version="0" />
    <module reference="6ed54515-acc8-4d1e-a16c-9fd6cfe951ea(MPS.Core)" version="0" />
    <module reference="8865b7a8-5271-43d3-884c-6fd1d9cfdd34(MPS.OpenAPI)" version="0" />
    <module reference="fdaaf35f-8ee3-4c37-b09d-9efaeaaa7a41(jetbrains.mps.core.tool.environment)" version="0" />
    <module reference="d936855b-48da-4812-a8a0-2bfddd633ac5(jetbrains.mps.lang.behavior.api)" version="0" />
    <module reference="ceab5195-25ea-4f22-9b92-103b95ca8c0c(jetbrains.mps.lang.core)" version="0" />
    <module reference="9e98f4e2-decf-4e97-bf80-9109e8b759aa(jetbrains.mps.lang.feedback.context)" version="0" />
    <module reference="d7eb0a2a-bd50-4576-beae-e4a89db35f20(jetbrains.mps.lang.scopes.runtime)" version="0" />
    <module reference="c72da2b9-7cce-4447-8389-f407dc1158b7(jetbrains.mps.lang.structure)" version="0" />
    <module reference="e52a4421-48a2-4de1-8327-d9414e799c67(org.modelix.metamodel.export)" version="0" />
  </dependencyVersions>
</solution>

