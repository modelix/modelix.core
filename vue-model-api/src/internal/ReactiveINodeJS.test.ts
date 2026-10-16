import { useModelsFromJson } from "../useModelsFromJson";
import { computed, effectScope, isReactive, reactive } from "vue";
import { runGarbageCollection } from "./runGarbageCollection";
import { toRoleJS } from "@modelix/ts-model-api";
import { ReadOnlyNodeJS } from "../ReadonlyNodeJS";

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

describe("reference to a removed node", () => {
  // A removed node is only reported as a change of the children of its parent.
  // The references to it do not change, because MPS keeps dangling references on purpose.
  const modelWithReferences = {
    root: {
      children: [
        {
          role: "holders",
          properties: { name: "holder" },
        },
        {
          role: "targets",
          properties: { name: "target" },
          children: [
            {
              role: "targets",
              properties: { name: "nestedTarget" },
            },
          ],
        },
        {
          role: "targets",
          properties: { name: "otherTarget" },
        },
      ],
    },
  };

  function useModelWithReferences() {
    const rootNode = useModelsFromJson([JSON.stringify(modelWithReferences)]);
    const [holder] = rootNode.getChildren(toRoleJS("holders"));
    const [target, otherTarget] = rootNode.getChildren(toRoleJS("targets"));
    const [nestedTarget] = target.getChildren(toRoleJS("targets"));
    return { rootNode, holder, target, otherTarget, nestedTarget };
  }

  const reference = toRoleJS("aReference");

  test("is unset after the target is removed", () => {
    const { holder, target } = useModelWithReferences();
    holder.setReferenceTargetNode(reference, target);
    const targetName = computed(() =>
      holder
        .getReferenceTargetNode(reference)
        ?.getPropertyValue(toRoleJS("name")),
    );
    expect(targetName.value).toBe("target");

    target.remove();

    expect(targetName.value).toBeUndefined();
    expect(holder.getReferenceTargetNode(reference)).toBe(null);
  });

  test("keeps its target reference after the target is removed", () => {
    const { holder, target } = useModelWithReferences();
    holder.setReferenceTargetNode(reference, target);
    const targetRef = target.getReference();
    const computedTargetRef = computed(() =>
      holder.getReferenceTargetRef(reference),
    );
    expect(computedTargetRef.value).toBe(targetRef);

    target.remove();

    expect(computedTargetRef.value).toBe(targetRef);
  });

  test("is unset after the parent of the target is removed", () => {
    const { holder, target, nestedTarget } = useModelWithReferences();
    holder.setReferenceTargetNode(reference, nestedTarget);
    const computedTarget = computed(() =>
      holder.getReferenceTargetNode(reference),
    );
    expect(computedTarget.value).toBe(nestedTarget);

    target.remove();

    expect(computedTarget.value).toBe(null);
  });

  test("is unset when holder and target are removed together", () => {
    const { rootNode, target, nestedTarget } = useModelWithReferences();
    nestedTarget.setReferenceTargetNode(reference, target);
    const computedTarget = computed(() =>
      nestedTarget.getReferenceTargetNode(reference),
    );
    expect(computedTarget.value).toBe(target);

    rootNode.removeChild(target);

    expect(computedTarget.value).toBe(null);
  });

  test("is still set after another node is removed", () => {
    const { holder, target, otherTarget } = useModelWithReferences();
    holder.setReferenceTargetNode(reference, target);
    let evaluations = 0;
    const computedTarget = computed(() => {
      evaluations++;
      return holder.getReferenceTargetNode(reference);
    });
    expect(computedTarget.value).toBe(target);

    otherTarget.remove();

    expect(computedTarget.value).toBe(target);
    expect(evaluations).toBe(1);
  });

  test("is updated after it is changed from a removed node to an existing node", () => {
    const { holder, target, otherTarget } = useModelWithReferences();
    holder.setReferenceTargetNode(reference, target);
    const computedTarget = computed(() =>
      holder.getReferenceTargetNode(reference),
    );
    target.remove();
    expect(computedTarget.value).toBe(null);

    holder.setReferenceTargetNode(reference, otherTarget);

    expect(computedTarget.value).toBe(otherTarget);
  });

  test("is not affected by removing its former target", () => {
    const { holder, target, otherTarget } = useModelWithReferences();
    holder.setReferenceTargetNode(reference, target);
    const computedTarget = computed(() =>
      holder.getReferenceTargetNode(reference),
    );
    expect(computedTarget.value).toBe(target);
    holder.setReferenceTargetNode(reference, otherTarget);
    expect(computedTarget.value).toBe(otherTarget);

    target.remove();

    expect(computedTarget.value).toBe(otherTarget);
  });

  test("still resolves after the target is moved", () => {
    const { holder, otherTarget, nestedTarget } = useModelWithReferences();
    holder.setReferenceTargetNode(reference, nestedTarget);
    const computedTarget = computed(() =>
      holder.getReferenceTargetNode(reference),
    );
    expect(computedTarget.value).toBe(nestedTarget);

    otherTarget.moveChild(toRoleJS("targets"), -1, nestedTarget);

    expect(computedTarget.value).toBe(nestedTarget);
  });

  test("is unset after the target is removed, even after garbage collection", async () => {
    const { holder } = useModelWithReferences();
    // Only keep references, so that garbage collection can collect every reactive node,
    // that is not reachable from the root node.
    const targetRef = holder
      .getParent()!
      .getChildren(toRoleJS("targets"))[0]
      .getReference();
    holder.setReferenceTargetRef(reference, targetRef);
    const targetName = computed(() =>
      holder
        .getReferenceTargetNode(reference)
        ?.getPropertyValue(toRoleJS("name")),
    );
    expect(targetName.value).toBe("target");

    await runGarbageCollection();
    holder.getReferenceTargetNode(reference)!.remove();

    expect(targetName.value).toBeUndefined();
  });
});

describe("containment", () => {
  test("parent is reactive", () => {
    const rootNode = useRootNode();
    const [child0, child1] = rootNode.getChildren(toRoleJS("children1"));
    const parentName = computed(() =>
      child1.getParent()?.getPropertyValue(toRoleJS("name")),
    );
    expect(parentName.value).toBeUndefined();

    child0.moveChild(toRoleJS("children2"), -1, child1);

    expect(parentName.value).toBe("child0");
  });

  test("role in parent is reactive", () => {
    const rootNode = useRootNode();
    const [, child1] = rootNode.getChildren(toRoleJS("children1"));
    const roleInParent = computed(() => child1.getRoleInParent());
    expect(roleInParent.value).toBe("children1");

    rootNode.moveChild(toRoleJS("children2"), -1, child1);

    expect(roleInParent.value).toBe("children2");
  });
});

test("property roles are reactive", () => {
  const node = useRootNode().getChildren(toRoleJS("children1"))[1];
  const propertyRoles = computed(() => node.getPropertyRoles());
  expect(propertyRoles.value).toEqual(["name"]);

  node.setPropertyValue(toRoleJS("newProperty"), "aValue");

  expect(propertyRoles.value).toEqual(["name", "newProperty"]);
});

test("reference roles are reactive", () => {
  const [, child1, child2] = useRootNode().getChildren(toRoleJS("children1"));
  const referenceRoles = computed(() => child1.getReferenceRoles());
  expect(referenceRoles.value).toHaveLength(0);

  child1.setReferenceTargetNode(toRoleJS("newReference"), child2);

  expect(referenceRoles.value).toHaveLength(1);
});

test("a value is not read again from the model before it is accessed", () => {
  const node = useRootNode().getChildren(toRoleJS("children1"))[0];
  let evaluations = 0;
  const computedProperty = computed(() => {
    evaluations++;
    return node.getPropertyValue(toRoleJS("aProperty"));
  });
  expect(computedProperty.value).toBe("aValue");

  node.setPropertyValue(toRoleJS("aProperty"), "aValue2");
  node.setPropertyValue(toRoleJS("aProperty"), "aValue3");
  expect(evaluations).toBe(1);

  expect(computedProperty.value).toBe("aValue3");
  expect(evaluations).toBe(2);
});

test("a reference read first in a component that is unmounted stays reactive", () => {
  const [child0, child1, child2] = useRootNode().getChildren(
    toRoleJS("children1"),
  );
  const reference = toRoleJS("aReference");
  child0.setReferenceTargetNode(reference, child1);
  // Like the `setup()` of a component that is unmounted later.
  // Before Vue 3.5, a `computed` created in it belongs to the component and is stopped with it.
  const componentScope = effectScope();
  componentScope.run(() => child0.getReferenceTargetNode(reference));
  componentScope.stop();
  const computedTarget = computed(() =>
    child0.getReferenceTargetNode(reference),
  );
  expect(computedTarget.value).toBe(child1);

  child0.setReferenceTargetNode(reference, child2);
  expect(computedTarget.value).toBe(child2);

  child2.remove();
  expect(computedTarget.value).toBe(null);
});
