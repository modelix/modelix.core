import { org } from "@modelix/model-client";
import type { ReactiveINodeJS } from "./ReactiveINodeJS";
import type { Cache } from "./Cache";

const { PropertyChanged, ReferenceChanged, ChildrenChanged } =
  org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;

export function handleChange(change: ChangeJS, cache: Cache<ReactiveINodeJS>) {
  const unreactiveNode = change.node;
  const reacitveNode = cache.get(unreactiveNode);
  if (reacitveNode === undefined) {
    return;
  }
  if (change instanceof PropertyChanged) {
    reacitveNode.triggerChangeInProperty(change.role);
  } else if (change instanceof ReferenceChanged) {
    reacitveNode.triggerChangeInReference(change.role);
  } else if (change instanceof ChildrenChanged) {
    reacitveNode.triggerChangeInChild(change.role);
  }
}
