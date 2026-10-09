import { org } from "@modelix/model-client";
import type { ReactiveINodeJS } from "./ReactiveINodeJS";
import type { Cache } from "./Cache";
import { toRoleJS } from "@modelix/ts-model-api";

const {
  PropertyChanged,
  ReferenceChanged,
  ChildrenChanged,
  NodeAdded,
  NodeRemoved,
} = org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;
type MutableModelTreeJs = org.modelix.model.client2.MutableModelTreeJs;

// Without `updateReferencesToRemovedNodes`, the handler is added with `addListener`,
// so that no changes for added and removed nodes are created.
export function addChangeHandler(
  tree: MutableModelTreeJs,
  cache: Cache<ReactiveINodeJS>,
  handler: (change: ChangeJS) => void,
) {
  if (cache.updateReferencesToRemovedNodes) {
    tree.addListenerIncludingAddedAndRemovedNodes(handler);
  } else {
    tree.addListener(handler);
  }
}

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
  } else if (change instanceof NodeRemoved || change instanceof NodeAdded) {
    // A node can be added again with the same reference, for example, by an undo.
    // Then a reference to it resolves again,
    // but only if the reference was read while the node existed.
    // Known limit: a reference that is first read, or changed, while the node is removed
    // stays unset after the node is added again, until the reference changes again.
    reactiveNode.triggerChangeInRemoved(change instanceof NodeRemoved);
  }
}
