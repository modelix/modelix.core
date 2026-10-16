import { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { toRoleJS } from "@modelix/ts-model-api";
import { computed } from "vue";
import { ReactiveModel } from "./ReactiveModel";

const { NodeAdded, NodeRemoved, PropertyChanged } = org.modelix.model.client2;

// A model, in which nodes can be added with a given reference,
// like a node that is added again by an undo or a node that is synchronized later.
// The model client cannot add a node with a given reference from JS.
// Like the nodes of the model client, a node only holds its reference and reads its data from the model.
interface NodeData {
  properties: Map<string, string>;
  references: Map<string, string>;
}

class FakeModel {
  private readonly nodes = new Map<string, NodeData>();

  add(reference: string, data: Partial<NodeData> = {}): FakeNode {
    this.nodes.set(reference, {
      properties: data.properties ?? new Map(),
      references: data.references ?? new Map(),
    });
    return new FakeNode(this, reference);
  }

  remove(reference: string) {
    this.nodes.delete(reference);
  }

  data(reference: string): NodeData {
    const data = this.nodes.get(reference);
    if (!data) {
      throw Error(`Node doesn't exist: ${reference}`);
    }
    return data;
  }

  exists(reference: string) {
    return this.nodes.has(reference);
  }
}

class FakeNode {
  constructor(
    private readonly model: FakeModel,
    private readonly reference: string,
  ) {}

  getReference() {
    return this.reference;
  }

  getPropertyRoles() {
    return [...this.model.data(this.reference).properties.keys()];
  }

  getPropertyValue(role: string) {
    return this.model.data(this.reference).properties.get(role);
  }

  getReferenceTargetRef(role: string) {
    return this.model.data(this.reference).references.get(role) ?? null;
  }

  getReferenceTargetNode(role: string) {
    const targetRef = this.getReferenceTargetRef(role);
    return targetRef && this.model.exists(targetRef)
      ? new FakeNode(this.model, targetRef)
      : null;
  }

  asINodeJS() {
    return this as unknown as INodeJS;
  }
}

const name = toRoleJS("name");
const reference = toRoleJS("aReference");

function addTarget(model: FakeModel, targetName: string) {
  return model.add("target", { properties: new Map([["name", targetName]]) });
}

function setUp() {
  const model = new FakeModel();
  const reactiveModel = new ReactiveModel();
  const holder = model.add("holder", {
    references: new Map([["aReference", "target"]]),
  });
  const reactiveHolder = reactiveModel.wrap(holder.asINodeJS());
  const targetName = computed(() =>
    reactiveHolder.getReferenceTargetNode(reference)?.getPropertyValue(name),
  );
  return { model, reactiveModel, targetName };
}

test("reference resolves after its target is added again", () => {
  const { model, reactiveModel, targetName } = setUp();
  const target = addTarget(model, "aTarget");
  expect(targetName.value).toBe("aTarget");

  model.remove("target");
  reactiveModel.handleChange(new NodeRemoved(target.asINodeJS()));
  expect(targetName.value).toBeUndefined();

  addTarget(model, "aTarget");
  reactiveModel.handleChange(new NodeAdded(target.asINodeJS()));
  expect(targetName.value).toBe("aTarget");
});

test("reference resolves after its target is added later", () => {
  const { model, reactiveModel, targetName } = setUp();
  expect(targetName.value).toBeUndefined();

  const target = addTarget(model, "aTarget");
  reactiveModel.handleChange(new NodeAdded(target.asINodeJS()));

  expect(targetName.value).toBe("aTarget");
});

test("values of a node that is added again are read again", () => {
  const { model, reactiveModel, targetName } = setUp();
  const target = addTarget(model, "aTarget");
  expect(targetName.value).toBe("aTarget");

  model.remove("target");
  reactiveModel.handleChange(new NodeRemoved(target.asINodeJS()));
  // A different node with the same reference, for example, from a model that was imported again.
  addTarget(model, "aNewTarget");
  reactiveModel.handleChange(new NodeAdded(target.asINodeJS()));

  expect(targetName.value).toBe("aNewTarget");
});

test("a change is not read from the model before the value is read again", () => {
  const { model, reactiveModel, targetName } = setUp();
  const target = addTarget(model, "aTarget");
  expect(targetName.value).toBe("aTarget");
  const unreactiveTarget = reactiveModel.wrap(
    target.asINodeJS(),
  ).unreactiveNode;
  const getPropertyValue = jest.spyOn(unreactiveTarget, "getPropertyValue");

  model.data("target").properties.set("name", "aChangedTarget");
  reactiveModel.handleChange(new PropertyChanged(target.asINodeJS(), "name"));
  expect(getPropertyValue).not.toHaveBeenCalled();

  expect(targetName.value).toBe("aChangedTarget");
  expect(getPropertyValue).toHaveBeenCalledTimes(1);
});
