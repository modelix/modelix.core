import { useModelsFromJson } from "@modelix/vue-model-api";
import { computed } from "vue";
import { jetbrains, org, TypedNodeConverter } from "@modelix/model-client";
import { isOfConcept_INamedConcept } from "typescript-generation/dist/L_jetbrains_mps_lang_core";
import N_BaseConcept = jetbrains.mps.lang.core.N_BaseConcept;
import N_StaticFieldReference = jetbrains.mps.baseLanguage.N_StaticFieldReference;
import N_Classifier = jetbrains.mps.baseLanguage.N_Classifier;
import L_jetbrains_mps_lang_core = jetbrains.mps.lang.core.L_jetbrains_mps_lang_core;
import ApigenTestLanguages = org.modelix.apigen.test.ApigenTestLanguages;

ApigenTestLanguages.registerAll();

function useRootNode(nodeData: object) {
  return useModelsFromJson([JSON.stringify(nodeData)]);
}

test("change to property is reactivly updated", () => {
  const nodeData = {
    root: {
      children: [
        {
          // concecpt ID of an INamedConcept
          concept: "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468",
          role: "children1",
          properties: {
            name: "aName",
          },
        },
      ],
    },
  };

  const untypedNode = useRootNode(nodeData).getChildren("children1")[0];
  const typedNode = TypedNodeConverter.toTypedNode(untypedNode);
  if (!isOfConcept_INamedConcept(typedNode)) {
    fail(`${typedNode} should be a INamedConcept`);
  }

  // We use `computed` to test the reactivity with Vue.
  // Accessing the property directly would circumvent Vue
  // and make this test useless.
  const computedProperty = computed(() => typedNode.name);
  expect(computedProperty.value).toBe("aName");

  typedNode.name = "aName2";
  expect(computedProperty.value).toBe("aName2");
});

test("change to children is reactivly updated", () => {
  const nodeData = {
    root: {
      children: [
        {
          // concecpt ID of an BaseConcept
          concept: "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626",
          role: "children1",
          properties: {
            shortDescription: "child0",
          },
          children: [
            {
              // concept ID of an Attribute
              concept:
                "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/5169995583184591161",
              role: "smodelAttribute",
              properties: {
                shortDescription: "attribute1",
              },
            },
          ],
        },
      ],
    },
  };
  const untypedNode = useRootNode(nodeData).getChildren("children1")[0];
  const baseConcept = TypedNodeConverter.toTypedNode(
    untypedNode,
  ) as N_BaseConcept;

  const computedNumberOfAttributes = computed(
    () => baseConcept.smodelAttribute.asArray().length,
  );
  expect(computedNumberOfAttributes.value).toBe(1);

  baseConcept.smodelAttribute.addNewWithConcept(
    L_jetbrains_mps_lang_core.Attribute,
  );
  expect(computedNumberOfAttributes.value).toBe(2);

  const firstAttribute = baseConcept.smodelAttribute.asArray()[0];
  baseConcept.unwrap().removeChild(firstAttribute.unwrap());
  expect(computedNumberOfAttributes.value).toBe(1);
});

test("change to reference is reactivly updated", () => {
  const nodeData = {
    root: {
      children: [
        {
          // concecpt ID of an StaticFieldReference
          concept: "mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1070533707846",
          role: "staticFieldReferences",
          references: {},
        },
        {
          // concept ID of an Classifier
          concept: "mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1107461130800",
          role: "classifiers",
          properties: {
            name: "aName",
          },
        },
      ],
    },
  };
  const rootNode = useRootNode(nodeData);
  const staticFieldReference = TypedNodeConverter.toTypedNode(
    rootNode.getChildren("staticFieldReferences")[0],
  ) as N_StaticFieldReference;
  const classifier = TypedNodeConverter.toTypedNode(
    rootNode.getChildren("classifiers")[0],
  ) as N_Classifier;

  const computedClassifierName = computed(
    () => staticFieldReference.classifier_orNull?.name,
  );

  expect(computedClassifierName.value).toBe(undefined);

  staticFieldReference.classifier = classifier;

  expect(computedClassifierName.value).toBe("aName");
});
