import { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { toRoleJS } from "@modelix/ts-model-api";
import { Cache } from "./Cache";
import { DependencyKey } from "./DependencyKey";
import { ReactiveINodeJS } from "./ReactiveINodeJS";

const {
  PropertyChanged,
  ReferenceChanged,
  ChildrenChanged,
  ContainmentChanged,
  ConceptChanged,
  NodeAdded,
  NodeRemoved,
} = org.modelix.model.client2;

type ChangeJS = org.modelix.model.client2.ChangeJS;

/**
 * The reactive view on one model.
 *
 * Reading from a {@link ReactiveINodeJS} records which parts of the model were read,
 * and {@link handleChange} triggers the Vue effects that read a changed part.
 * The parts are tracked like the dependencies in `IncrementalBranch`:
 * - the property, the reference and the children of a node per role, all its children,
 *   its containment (parent and role in parent) and its concept,
 *   which are kept by the {@link ReactiveINodeJS} of the node,
 * - and whether a node with a given reference exists, which is kept here.
 *   It does not need a {@link ReactiveINodeJS}, because the node might not exist yet.
 */
export class ReactiveModel {
  private readonly nodes = new Cache<ReactiveINodeJS>();
  private readonly existenceKeys = new Cache<DependencyKey>();

  wrap(node: INodeJS): ReactiveINodeJS {
    return this.nodes.memoize(node, () => new ReactiveINodeJS(node, this));
  }

  /**
   * The key for whether a node with the given reference exists.
   * It must be kept reachable by whoever depends on it, see {@link DependencyKey}.
   */
  existenceKey(reference: string): DependencyKey {
    return this.existenceKeys.memoizeWithKey(
      reference,
      () => new DependencyKey(),
    );
  }

  handleChange(change: ChangeJS): void {
    if (change instanceof NodeAdded || change instanceof NodeRemoved) {
      const reference = change.node.getReference() as string;
      const reactiveNode = this.nodes.getWithKey(reference);
      if (change instanceof NodeAdded) {
        // A node that is added again can differ from the removed node, see `invalidateAll`.
        reactiveNode?.setRemoved(false);
        reactiveNode?.invalidateAll();
      } else {
        // Before triggering, so that values that are read again know that the node is removed.
        reactiveNode?.setRemoved(true);
      }
      this.existenceKeys.getWithKey(reference)?.trigger();
      return;
    }
    const reactiveNode = this.nodes.get(change.node);
    if (reactiveNode === undefined) {
      return;
    }
    if (change instanceof PropertyChanged) {
      reactiveNode.invalidateProperty(toRoleJS(change.role));
    } else if (change instanceof ReferenceChanged) {
      reactiveNode.invalidateReference(toRoleJS(change.role));
    } else if (change instanceof ChildrenChanged) {
      reactiveNode.invalidateChildren(toRoleJS(change.role));
    } else if (change instanceof ContainmentChanged) {
      reactiveNode.invalidateContainment();
    } else if (change instanceof ConceptChanged) {
      reactiveNode.invalidateConcept();
    }
  }
}
