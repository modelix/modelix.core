import {
  isOfConcept_INamedConcept,
  isOfConcept_PropertyAttribute,
  isOfConcept_Attribute,
} from "../build/typescript_src/L_jetbrains_mps_lang_core";
import { useFakeNode } from "./test-helpers";

const { typedNode } = useFakeNode();

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
