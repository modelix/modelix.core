import { useFakeNode } from "./test-helpers";
import { toRoleJS } from "@modelix/ts-model-api";

test("typed nodes can be removed", () => {
  const { typedNode, rootNode } = useFakeNode();
  expect(rootNode.getChildren(toRoleJS("children1"))).toHaveLength(1);

  typedNode.remove();
  expect(rootNode.getChildren(toRoleJS("children1"))).toHaveLength(0);
});
