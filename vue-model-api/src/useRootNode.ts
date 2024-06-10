import { org } from "@modelix/model-client";
import { INodeJS } from "@modelix/ts-model-api";
import { useLastPromiseEffect } from "./internal/useLastPromiseEffect";
import { MaybeRefOrGetter, Ref, shallowRef, toValue } from "vue";
import { ReactiveINodeJS, toReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { Cache } from "./internal/Cache";
import { handleChange } from "./internal/handleChange";

type ClientJS = org.modelix.model.client2.ClientJS;
type BranchJS = org.modelix.model.client2.BranchJS;
type ChangeJS = org.modelix.model.client2.ChangeJS;

/**
 * Creates a reactive root node from a client for a given repository and branch.
 *
 * The returned root node uses Vues reactivity and can be used in Vue like an reactive object.
 * Changes to the returned node or its descendants are synced to the branch on the model server.
 *
 * Calling the returned dispose function stops syncing the root node to the underlying branch on the serever.
 *
 * @experimental This feature is expected to be finalized with https://issues.modelix.org/issue/MODELIX-500.
 *
 * @param client - Reactive reference of a client to a model server.
 * @param repositoryId - Reactive reference of a repositoryId on the model server.
 * @param branchId - Reactive reference of a branchId in the repository of the model server.
 *
 * @returns {Object} values Wrapper around diffrent returned values.
 * @returns {Ref<INodeJS | null>} values.rootNode  Reactive reference to a reactive root node.
 * @returns {Ref<BranchJS | null>} values.branch  Reactive reference to the branch.
 * @returns {() => void} values.dispose A function to manually dispose the root node.
 * @returns {Ref<unknown>} values.error Reactive reference to a connection error.
 */
export function useRootNode(
  client: MaybeRefOrGetter<ClientJS | null>,
  repositoryId: MaybeRefOrGetter<string | null>,
  branchId: MaybeRefOrGetter<string | null>,
): {
  rootNode: Ref<INodeJS | null>;
  branch: Ref<BranchJS | null>;
  dispose: () => void;
  error: Ref<unknown>;
} {
  const branchRef: Ref<BranchJS | null> = shallowRef(null);
  const rootNodeRef: Ref<INodeJS | null> = shallowRef(null);
  const errorRef: Ref<unknown> = shallowRef(null);

  const dispose = () => {
    if (branchRef.value !== null) {
      branchRef.value.dispose();
    }
    branchRef.value = null;
    rootNodeRef.value = null;
    errorRef.value = null;
  };

  useLastPromiseEffect(
    () => {
      dispose();
      const clientValue = toValue(client);
      if (clientValue === null) {
        return;
      }
      const repositoryIdValue = toValue(repositoryId);
      if (repositoryIdValue === null) {
        return;
      }
      const branchIdValue = toValue(branchId);
      if (branchIdValue === null) {
        return;
      }
      const cache = new Cache<ReactiveINodeJS>();
      return clientValue
        .connectBranch(repositoryIdValue, branchIdValue)
        .then((branch) => ({ branch, cache }));
    },
    ({ branch: connectedBranch, cache }, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        branchRef.value = connectedBranch;
        branchRef.value.addListener((change: ChangeJS) => {
          if (cache === null) {
            throw Error("The cache is unexpectedly not set up.");
          }
          handleChange(change, cache);
        });
        const unreactiveRootNode = branchRef.value.rootNode;
        const reactiveRootNode = toReactiveINodeJS(unreactiveRootNode, cache);
        rootNodeRef.value = reactiveRootNode;
      } else {
        connectedBranch.dispose();
      }
    },
    (reason, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        errorRef.value = reason;
      }
    },
  );

  return {
    rootNode: rootNodeRef,
    branch: branchRef,
    dispose,
    error: errorRef,
  };
}
