/*
 * Note: Ideally, this test should be placed in the ts-model-api module.
 * However, due to the complexity of testing the code without the context
 * of the generated languages, we will conduct the test here for now.
 */

import {
  BaseCommentAttribute,
  C_BaseCommentAttribute,
  C_BaseConcept,
  C_TypeAnnotated,
  isOfConcept_BaseConcept,
  isOfConcept_TypeAnnotated,
} from "../build/typescript_src/L_jetbrains_mps_lang_core";
import { useFakeNode } from "./test-helpers";

const NODE_DATA_WITH_SINGLE_CHILD_ACCESSOR = {
  root: {
    children: [
      {
        concept: C_BaseCommentAttribute.getUID(),
        role: "withSingleChildAccessor",
        properties: {
          name: "aName",
        },
      },
    ],
  },
};

test("new element can be added to SingleChildAccessor when provided the correct base concept", () => {
  const { typedNode } = useFakeNode<BaseCommentAttribute>(
    "withSingleChildAccessor",
    NODE_DATA_WITH_SINGLE_CHILD_ACCESSOR
  );
  const childNode = typedNode.commentedNode.setNew(C_BaseConcept);
  expect(isOfConcept_BaseConcept(childNode)).toBeTruthy();
});

test("new element can be added to SingleChildAccessor when provided a sub concept", () => {
  const { typedNode } = useFakeNode<BaseCommentAttribute>(
    "withSingleChildAccessor",
    NODE_DATA_WITH_SINGLE_CHILD_ACCESSOR
  );
  const childNode = typedNode.commentedNode.setNew(C_TypeAnnotated);
  expect(isOfConcept_TypeAnnotated(childNode)).toBeTruthy();
  expect(isOfConcept_BaseConcept(childNode)).toBeTruthy();
});
