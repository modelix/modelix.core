import { org } from "@modelix/model-client";
import type { ReactiveINodeJS } from "./ReactiveINodeJS";
import type { Cache } from "./Cache";
import { toRoleJS } from "@modelix/ts-model-api";

const { PropertyChanged, ReferenceChanged, ChildrenChanged } =
  org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;

export function handleChange(change: ChangeJS, cache: Cache<ReactiveINodeJS>) {
  const unreactiveNode = change.node;
  const reactiveNode = cache.get(unreactiveNode);
  if (reactiveNode === undefined) {
    return;
  }
  if (change instanceof PropertyChanged) {
    reactiveNode.triggerChangeInProperty(toRoleJS(change.role));
  } else if (change instanceof ReferenceChanged) {
    reactiveNode.triggerChangeInReference(toRoleJS(change.role));
  } else if (change instanceof ChildrenChanged) {
    reactiveNode.triggerChangeInChild(toRoleJS(change.role));
  }
}
