import { org } from "@modelix/model-client";
import { ReactiveINodeJS } from "./ReactiveINodeJS";
import { Cache } from "./Cache";

const { PropertyChanged, ReferenceChanged, ChildrenChanged } =
  org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;

export function handleChange(change: ChangeJS, cache: Cache<ReactiveINodeJS>) {
  const unreactiveNode = change.node;
  const reacitveNode = cache.get(unreactiveNode);
  if (reacitveNode === undefined) {
    return;
  }
  if (change instanceof PropertyChanged || change instanceof ReferenceChanged) {
    reacitveNode.triggerChangeInRole(change.role);
  } else if (change instanceof ChildrenChanged) {
    reacitveNode.triggerChangeInRole(change.role);
    reacitveNode.triggerChangeInAllChildren();
  }
}
