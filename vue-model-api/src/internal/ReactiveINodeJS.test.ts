import { useModelsFromJson } from "../useModelsFromJson";
import { computed, isReactive, reactive } from "vue";
import { runGarbageCollection } from "./runGarbageCollection";
import type { INodeJS } from "@modelix/ts-model-api";
import { toRoleJS } from "@modelix/ts-model-api";
import { ReadOnlyNodeJS } from "../ReadonlyNodeJS";
import { org } from "@modelix/model-client";
import { Cache } from "./Cache";
import { handleChange } from "./handleChange";
import type { ReactiveINodeJS } from "./ReactiveINodeJS";
import { toReactiveINodeJS } from "./ReactiveINodeJS";
import type { ReactiveModelOptions } from "../ReactiveModelOptions";

const { NodeAdded, NodeRemoved } = org.modelix.model.client2;

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

test("can setReferenceTargetNode to a readonly node", async () => {
  const rootNode = useRootNode();
  const child0 = rootNode.getChildren(toRoleJS("children1"))[0];
  const child2 = rootNode.getChildren(toRoleJS("children1"))[1];
  const readonlyNode = new ReadOnlyNodeJS(child2, () => {});

  child0.setReferenceTargetNode(toRoleJS("aReference"), readonlyNode);

  expect(
    child0.getReferenceTargetNode(toRoleJS("aReference"))?.getReference(),
  ).toEqual(child2.getReference());
});

const modelWithReference = {
  root: {
    children: [
      {
        role: "holders",
        references: { aReference: "target" },
      },
      {
        role: "holders",
        properties: { name: "otherHolder" },
      },
      {
        role: "containers",
        children: [
          {
            id: "target",
            role: "items",
            properties: { name: "target" },
          },
        ],
      },
    ],
  },
};

const updateReferencesToRemovedNodes = { updateReferencesToRemovedNodes: true };

// The holder is read through its parent, the target only through the reference,
// so the target's parent "container" is never cached.
function useModelWithReference(
  options: ReactiveModelOptions = updateReferencesToRemovedNodes,
) {
  const rootNode = useModelsFromJson(
    [JSON.stringify(modelWithReference)],
    options,
  );
  const [holder, otherHolder] = rootNode.getChildren(toRoleJS("holders"));
  return { rootNode, holder, otherHolder };
}

test("reference to a deleted node reads as unset", () => {
  const { holder } = useModelWithReference();
  const computedTargetName = computed(() =>
    holder
      .getReferenceTargetNode(toRoleJS("aReference"))
      ?.getPropertyValue(toRoleJS("name")),
  );
  expect(computedTargetName.value).toBe("target");

  holder.getReferenceTargetNode(toRoleJS("aReference"))!.remove();

  expect(computedTargetName.value).toBeUndefined();
  expect(holder.getReferenceTargetNode(toRoleJS("aReference"))).toBeNull();
});

test("without the option, a reference to a deleted node keeps the deleted node", () => {
  const { holder } = useModelWithReference({});
  const computedTargetNode = computed(() =>
    holder.getReferenceTargetNode(toRoleJS("aReference")),
  );
  const target = computedTargetNode.value!;

  target.remove();

  expect(computedTargetNode.value).toBe(target);
});

test("reference into a deleted subtree reads as unset", () => {
  const { rootNode, holder } = useModelWithReference();
  const computedTargetNode = computed(() =>
    holder.getReferenceTargetNode(toRoleJS("aReference")),
  );
  expect(computedTargetNode.value).not.toBeNull();

  rootNode.getChildren(toRoleJS("containers"))[0].remove();

  expect(computedTargetNode.value).toBeNull();
});

test("unrelated change to children does not update a valid reference", () => {
  const { rootNode, holder } = useModelWithReference();
  let evaluations = 0;
  const computedTargetNode = computed(() => {
    evaluations++;
    return holder.getReferenceTargetNode(toRoleJS("aReference"));
  });
  const target = computedTargetNode.value;
  expect(evaluations).toBe(1);

  rootNode.addNewChild(toRoleJS("holders"), -1, undefined).remove();

  expect(computedTargetNode.value).toBe(target);
  expect(evaluations).toBe(1);
});

test("removing a holder together with its target reads the references as unset", () => {
  const rootNode = useModelsFromJson(
    [
      JSON.stringify({
        root: {
          children: [
            {
              role: "holders",
              references: { aReference: "target" },
            },
            {
              role: "subtrees",
              children: [
                {
                  role: "holders",
                  references: { aReference: "target" },
                },
                { id: "target", role: "items" },
              ],
            },
          ],
        },
      }),
    ],
    updateReferencesToRemovedNodes,
  );
  const outerHolder = rootNode.getChildren(toRoleJS("holders"))[0];
  const subtree = rootNode.getChildren(toRoleJS("subtrees"))[0];
  const innerHolder = subtree.getChildren(toRoleJS("holders"))[0];
  const computedOuterTarget = computed(() =>
    outerHolder.getReferenceTargetNode(toRoleJS("aReference")),
  );
  const computedInnerTarget = computed(() =>
    innerHolder.getReferenceTargetNode(toRoleJS("aReference")),
  );
  expect(computedOuterTarget.value).not.toBeNull();
  expect(computedInnerTarget.value).toBe(computedOuterTarget.value);

  subtree.remove();

  expect(computedOuterTarget.value).toBeNull();
  expect(computedInnerTarget.value).toBeNull();
});

test("removing the former target of a reference does not update the reference", () => {
  const { rootNode, holder } = useModelWithReference();
  const container = rootNode.getChildren(toRoleJS("containers"))[0];
  const oldTarget = container.getChildren(toRoleJS("items"))[0];
  const newTarget = container.addNewChild(toRoleJS("items"), -1, undefined);
  let evaluations = 0;
  const computedTargetNode = computed(() => {
    evaluations++;
    return holder.getReferenceTargetNode(toRoleJS("aReference"));
  });
  expect(computedTargetNode.value).toBe(oldTarget);
  holder.setReferenceTargetNode(toRoleJS("aReference"), newTarget);
  expect(computedTargetNode.value).toBe(newTarget);
  expect(evaluations).toBe(2);

  oldTarget.remove();

  expect(computedTargetNode.value).toBe(newTarget);
  expect(evaluations).toBe(2);

  newTarget.remove();

  expect(computedTargetNode.value).toBeNull();
  expect(evaluations).toBe(3);
});

test("changes that neither add nor remove nodes do not update a valid reference", () => {
  const { rootNode, holder, otherHolder } = useModelWithReference();
  let evaluations = 0;
  const computedTargetNode = computed(() => {
    evaluations++;
    return holder.getReferenceTargetNode(toRoleJS("aReference"));
  });
  const target = computedTargetNode.value;

  rootNode.moveChild(toRoleJS("holders"), -1, holder);
  otherHolder.setPropertyValue(toRoleJS("name"), "renamed");
  otherHolder.setReferenceTargetNode(toRoleJS("aReference"), holder);

  expect(rootNode.getChildren(toRoleJS("holders"))).toEqual([
    otherHolder,
    holder,
  ]);
  expect(computedTargetNode.value).toBe(target);
  expect(evaluations).toBe(1);
});

test("moving the target of a reference does not update the reference", () => {
  const { rootNode, holder } = useModelWithReference();
  let evaluations = 0;
  const computedTargetNode = computed(() => {
    evaluations++;
    return holder.getReferenceTargetNode(toRoleJS("aReference"));
  });
  const target = computedTargetNode.value!;

  rootNode.moveChild(toRoleJS("items"), -1, target);

  expect(rootNode.getChildren(toRoleJS("items"))).toEqual([target]);
  expect(computedTargetNode.value).toBe(target);
  expect(evaluations).toBe(1);
});

test("a reference read outside a computed reads as unset in a computed after its target is removed", () => {
  const { holder } = useModelWithReference();
  holder.getReferenceTargetNode(toRoleJS("aReference"))!.remove();

  const computedTargetNode = computed(() =>
    holder.getReferenceTargetNode(toRoleJS("aReference")),
  );

  expect(computedTargetNode.value).toBeNull();
});

test("a new target that is removed before the reference is read again reads as unset", () => {
  const { rootNode, holder } = useModelWithReference();
  const computedTargetNode = computed(() =>
    holder.getReferenceTargetNode(toRoleJS("aReference")),
  );
  expect(computedTargetNode.value).not.toBeNull();
  const newTarget = rootNode.addNewChild(toRoleJS("items"), -1, undefined);
  holder.setReferenceTargetNode(toRoleJS("aReference"), newTarget);

  // The new target was never read through the reference before its removal.
  newTarget.remove();

  expect(computedTargetNode.value).toBeNull();
});

// The JS API cannot add a node with the reference of a removed node, as an undo does.
// So these tests use fake nodes and pass the changes to `handleChange` directly.
function useFakeModelWithReference() {
  const nodesInModel = new Set(["target"]);
  const target = { getReference: () => "target" } as unknown as INodeJS;
  const holder = {
    getReference: () => "holder",
    getReferenceTargetNode: () => (nodesInModel.has("target") ? target : null),
  } as unknown as INodeJS;
  const cache = new Cache<ReactiveINodeJS>(true);
  const reactiveHolder = toReactiveINodeJS(holder, cache);
  const computedTargetReference = computed(() =>
    reactiveHolder
      .getReferenceTargetNode(toRoleJS("aReference"))
      ?.getReference(),
  );
  function remove(node: INodeJS) {
    nodesInModel.delete(node.getReference());
    handleChange(new NodeRemoved(node), cache);
  }
  function add(node: INodeJS) {
    nodesInModel.add(node.getReference());
    handleChange(new NodeAdded(node), cache);
  }
  return { target, computedTargetReference, remove, add };
}

test("reference to a removed node resolves again when the node is added again", () => {
  const { target, computedTargetReference, remove, add } =
    useFakeModelWithReference();
  expect(computedTargetReference.value).toBe("target");

  remove(target);

  expect(computedTargetReference.value).toBeUndefined();

  add(target);

  expect(computedTargetReference.value).toBe("target");
});

test("known limit: a reference first read while its target is removed stays unset when the target is added again", () => {
  const { target, computedTargetReference, remove, add } =
    useFakeModelWithReference();
  remove(target);
  expect(computedTargetReference.value).toBeUndefined();

  add(target);

  expect(computedTargetReference.value).toBeUndefined();
});
