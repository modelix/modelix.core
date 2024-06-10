import { isOfConcept_PropertyAttribute } from "../build/typescript_src/L_jetbrains_mps_lang_core";
import { useFakeNode } from "./test-helpers";

test("typed nodes can be removed", () => {
  const { typedNode, rootNode } = useFakeNode();
  expect(rootNode.getChildren("children1")).toHaveLength(1);

  typedNode.remove();
  expect(rootNode.getChildren("children1")).toHaveLength(0);
});

test("untyped nodes can be easily wrapped", () => {
  const { untypedNode } = useFakeNode();
  const typedNode = untypedNode.wrap();
  expect(isOfConcept_PropertyAttribute(typedNode)).toBeTruthy();
});

test("typed nodes can be easily unwrapped", () => {
  const { typedNode } = useFakeNode();
  const untypedNode = typedNode.unwrap();
  expect(untypedNode.getConceptUID()).toEqual(
    "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/3364660638048049750"
  );
});
