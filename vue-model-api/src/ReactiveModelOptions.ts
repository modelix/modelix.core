/**
 * Options for the reactive nodes returned by `useModelsFromJson`, `useReplicatedModel`
 * and `useReplicatedModels`.
 */
export interface ReactiveModelOptions {
  /**
   * Make `getReferenceTargetNode` read a reference to a removed node as unset.
   * `getReferenceTargetRef` still returns the reference.
   * Without it, a view that shows such a reference keeps showing the removed node and does not update.
   * It costs work for every added or removed node. Default: `false`.
   */
  updateReferencesToRemovedNodes?: boolean;
}
