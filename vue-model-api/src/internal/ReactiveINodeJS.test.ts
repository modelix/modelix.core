import { useModelsFromJson } from "../useModelsFromJson";
import { computed, isReactive, reactive } from "vue";
import { runGarbageCollection } from "./runGarbageCollection";
import { toRoleJS } from "@modelix/ts-model-api";

const root = {
  root: {
    children: [
      {
        role: "children1",
        properties: {
          name: "child0",
          aProperty: "aValue",
        },
        references: {
          aReference: undefined,
        },
      },
      {
        role: "children1",
        properties: {
          name: "child1",
        },
      },
      {
        role: "children1",
        properties: {
          name: "child2",
        },
      },
    ],
  },
};

function useRootNode() {
  return useModelsFromJson([JSON.stringify(root)]);
}

test("nodes are not wrapped into a reactive object by Vue ", () => {
  const node = useRootNode().getChildren(toRoleJS("children1"))[0];

  const reactiveData = reactive({ node });

  expect(reactiveData.node).toBe(node);
  expect(isReactive(reactiveData.node)).toBeFalsy();
});

test("change to property is reactivly updated", () => {
  const node = useRootNode().getChildren(toRoleJS("children1"))[0];

  // We use `computed` to test the reactivity with Vue.
  // Accessing the property directly would circumvent Vue
  // and make this test useless.
  const computedProperty = computed(() =>
    node.getPropertyValue(toRoleJS("aProperty")),
  );
  expect(computedProperty.value).toBe("aValue");

  node.setPropertyValue(toRoleJS("aProperty"), "aValue2");
  expect(computedProperty.value).toBe("aValue2");
});

test("change to reference is reactivly updated", () => {
  const [child0, child1, child2] = useRootNode().getChildren(
    toRoleJS("children1"),
  );

  // We use `computed` to test the reactivity with Vue.
  // Accessing the property directly would circumvent Vue
  // and make this test useless.
  const computedReferenceTargetRef = computed(() =>
    child0.getReferenceTargetRef(toRoleJS("aReference")),
  );
  const computedReferenceTargetNode = computed(() =>
    child0.getReferenceTargetNode(toRoleJS("aReference")),
  );

  expect(computedReferenceTargetRef.value).toBe(null);
  // Acording to the declared types, we would expect `undefined`
  // but the declerations are wrong regarding `undefined`/`null`
  // see. https://issues.modelix.org/issue/MODELIX-567/
  expect(computedReferenceTargetNode.value).toBe(null);

  child0.setReferenceTargetRef(toRoleJS("aReference"), child1.getReference());
  expect(computedReferenceTargetRef.value).toBe(child1.getReference());
  expect(computedReferenceTargetNode.value).toBe(child1);

  child0.setReferenceTargetNode(toRoleJS("aReference"), child2);
  expect(computedReferenceTargetRef.value).toBe(child2.getReference());
  expect(computedReferenceTargetNode.value).toBe(child2);
});

test("change to children with role is reactive", () => {
  const rootNode = useRootNode();

  const computedChildNames = computed(() =>
    rootNode
      .getChildren(toRoleJS("children1"))
      .map((child) => child.getPropertyValue(toRoleJS("name"))),
  );
  expect(computedChildNames.value).toEqual(["child0", "child1", "child2"]);

  const child3 = rootNode.addNewChild(toRoleJS("children1"), -1, undefined);
  child3.setPropertyValue(toRoleJS("name"), "child3");
  expect(computedChildNames.value).toEqual([
    "child0",
    "child1",
    "child2",
    "child3",
  ]);

  const child2 = rootNode.getChildren(toRoleJS("children1"))[2];
  rootNode.removeChild(child2);
  expect(computedChildNames.value).toEqual(["child0", "child1", "child3"]);

  const child1 = rootNode.getChildren(toRoleJS("children1"))[1];
  rootNode.moveChild(toRoleJS("children1"), -1, child1);
  expect(computedChildNames.value).toEqual(["child0", "child3", "child1"]);
});

test("change to children with no role is reactive", () => {
  const rootNode = useRootNode();

  const computedChildNames = computed(() =>
    rootNode
      .getChildren(undefined)
      .map((child) => child.getPropertyValue(toRoleJS("name"))),
  );
  expect(computedChildNames.value).toEqual([]);

  const child1 = rootNode.addNewChild(undefined, -1, undefined);
  child1.setPropertyValue(toRoleJS("name"), "child1");
  expect(computedChildNames.value).toEqual(["child1"]);
});

test("change to all children is reactive", () => {
  const rootNode = useRootNode();

  const computedChildNames = computed(() =>
    rootNode
      .getAllChildren()
      .map((child) => child.getPropertyValue(toRoleJS("name"))),
  );
  expect(computedChildNames.value).toEqual(["child0", "child1", "child2"]);

  const child3 = rootNode.addNewChild(undefined, -1, undefined);
  child3.setPropertyValue(toRoleJS("name"), "child3");
  expect(computedChildNames.value).toEqual([
    "child0",
    "child1",
    "child2",
    "child3",
  ]);
});

test("removing a node is reactive", () => {
  const rootNode = useRootNode();
  const childCount = rootNode.getChildren(toRoleJS("children1")).length;
  const node = rootNode.getChildren(toRoleJS("children1"))[0];

  // We use `computed` to test the reactivity with Vue.
  // Accessing the property directly would circumvent Vue
  // and make this test useless.
  const computedProperty = computed(() =>
    rootNode.getChildren(toRoleJS("children1")),
  );
  expect(computedProperty.value).toHaveLength(childCount);

  node.remove();
  expect(computedProperty.value).toHaveLength(childCount - 1);
});

test("garbage collection does not break reactivity", async () => {
  const rootNode = useRootNode();
  // Do not assign the child object to a variable because this would prevent GC from collecting.
  // MODELIX-1041 was caused by child object being garbage collected even when Vue components were subscribed to their properties.
  function getChild() {
    return rootNode.getAllChildren()[0];
  }
  getChild().setPropertyValue(toRoleJS("name"), "firstName");
  const computedChildNames = computed(() =>
    getChild().getPropertyValue(toRoleJS("name")),
  );
  expect(computedChildNames.value).toEqual("firstName");

  await runGarbageCollection();
  getChild().setPropertyValue(toRoleJS("name"), "secondName");

  expect(computedChildNames.value).toEqual("secondName");
});
