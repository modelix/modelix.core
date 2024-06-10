import { useFakeNode } from "./test-helpers";

test("typed nodes can be removed", () => {
  const { typedNode, rootNode } = useFakeNode();
  expect(rootNode.getChildren("children1")).toHaveLength(1);

  typedNode.remove();
  expect(rootNode.getChildren("children1")).toHaveLength(0);
});
