import { org } from "@modelix/model-client";
import { LanguageRegistry } from "@modelix/ts-model-api";
import {
  isOfConcept_INamedConcept,
  isOfConcept_PropertyAttribute,
  isOfConcept_Attribute,
} from "../build/typescript_src/L_jetbrains_mps_lang_core";
import { registerLanguages } from "../build/typescript_src";

registerLanguages();

const nodeData = {
  root: {
    children: [
      {
        // concecpt ID of an PropertyAttribute
        concept: "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/3364660638048049750",
        role: "children1",
        properties: {
          name: "aName",
        },
      },
    ],
  },
};

const untypedNode = useRootNode(nodeData).getChildren("children1")[0];
const typedNode = LanguageRegistry.INSTANCE.wrapNode(untypedNode);

test("verifies the concept of a node", () => {
  expect(isOfConcept_PropertyAttribute(typedNode)).toBeTruthy();
});

test("verifies the parent concept of a node", () => {
  expect(isOfConcept_Attribute(typedNode)).toBeTruthy();
});

test("recognizes when neither concept or parent concepts of node match", () => {
  expect(isOfConcept_INamedConcept(typedNode)).toBeFalsy();
});

test("nullish values are never of the type of the checked concept", () => {
  expect(isOfConcept_INamedConcept(null)).toBeFalsy();
  expect(isOfConcept_INamedConcept(undefined)).toBeFalsy();
});

function useRootNode(nodeData: object) {
  const { loadModelsFromJson } = org.modelix.model.client2;
  return loadModelsFromJson(
    [JSON.stringify(nodeData)],
    // for the purpose of the test a change handler is not needed
    () => {}
  );
}
