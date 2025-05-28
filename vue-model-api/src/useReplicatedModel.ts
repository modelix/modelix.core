import type { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { useLastPromiseEffect } from "./internal/useLastPromiseEffect";
import type { MaybeRefOrGetter, Ref } from "vue";
import { shallowRef, toValue } from "vue";
import type { ReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { toReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { Cache } from "./internal/Cache";
import { handleChange } from "./internal/handleChange";

type ClientJS = org.modelix.model.client2.ClientJS;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;
type ChangeJS = org.modelix.model.client2.ChangeJS;

function isDefined<T>(value: T | null | undefined): value is T {
  return value !== null && value !== undefined;
}

/**
 * Creates a replicated model for a given repository and branch.
 * A replicated model exposes a branch that can be used to read and write model data.
 * The written model data is automatically synced to the model server.
 * Changed from the model server are automatically synced to the branch in the replicated model
 *
 * Also creates root node that uses Vues reactivity and can be used in Vue like a reactive object.
 * Changes to model data trigger recalculation of computed properties or re-rendering of components using that data.
 *
 * Calling the returned dispose function stops syncing the root node to the underlying branch on the server.
 *
 * @param client - Reactive reference of a client to a model server.
 * @param repositoryId - Reactive reference of a repositoryId on the model server.
 * @param branchId - Reactive reference of a branchId in the repository of the model server.
 *
 * @returns {Object} values Wrapper around different returned values.
 * @returns {Ref<ReplicatedModelJS | null>} values.rootNode  Reactive reference to the replicated model for the specified branch.
 * @returns {Ref<INodeJS | null>} values.rootNode  Reactive reference to the root node with Vue.js reactivity for the specified branch.
 * @returns {() => void} values.dispose A function to manually dispose the root node.
 * @returns {Ref<unknown>} values.error Reactive reference to a connection error.
 */
export function useReplicatedModel(
  client: MaybeRefOrGetter<ClientJS | null | undefined>,
  repositoryId: MaybeRefOrGetter<string | null | undefined>,
  branchId: MaybeRefOrGetter<string | null | undefined>,
  idScheme: MaybeRefOrGetter<
    org.modelix.model.client2.IdSchemeJS | null | undefined
  >,
): {
  replicatedModel: Ref<ReplicatedModelJS | null>;
  rootNode: Ref<INodeJS | null>;
  dispose: () => void;
  error: Ref<unknown>;
} {
  // Use `replicatedModel` to access the replicated model without tracking overhead of Vue.js.
  let replicatedModel: ReplicatedModelJS | null = null;
  const replicatedModelRef: Ref<ReplicatedModelJS | null> = shallowRef(null);
  const rootNodeRef: Ref<INodeJS | null> = shallowRef(null);
  const errorRef: Ref<unknown> = shallowRef(null);

  const dispose = () => {
    // Using `replicatedModelRef.value` here would create a circular dependency.
    // `toRaw` does not work on `Ref<>`.
    if (replicatedModel !== null) {
      replicatedModel.dispose();
    }
    replicatedModelRef.value = null;
    rootNodeRef.value = null;
    errorRef.value = null;
  };

  useLastPromiseEffect(
    () => {
      dispose();
      const clientValue = toValue(client);
      if (!isDefined(clientValue)) {
        return;
      }
      const repositoryIdValue = toValue(repositoryId);
      if (!isDefined(repositoryIdValue)) {
        return;
      }
      const branchIdValue = toValue(branchId);
      if (!isDefined(branchIdValue)) {
        return;
      }
      const idSchemeValue = toValue(idScheme);
      if (!isDefined(idSchemeValue)) {
        return;
      }
      const cache = new Cache<ReactiveINodeJS>();
      return clientValue
        .startReplicatedModel(repositoryIdValue, branchIdValue, idSchemeValue)
        .then((replicatedModel) => ({ replicatedModel, cache }));
    },
    (
      { replicatedModel: connectedReplicatedModel, cache },
      isResultOfLastStartedPromise,
    ) => {
      if (isResultOfLastStartedPromise) {
        replicatedModel = connectedReplicatedModel;
        const branch = replicatedModel.getBranch();
        branch.addListener((change: ChangeJS) => {
          if (cache === null) {
            throw Error("The cache is unexpectedly not set up.");
          }
          handleChange(change, cache);
        });
        const unreactiveRootNode = branch.rootNode;
        const reactiveRootNode = toReactiveINodeJS(unreactiveRootNode, cache);
        replicatedModelRef.value = replicatedModel;
        rootNodeRef.value = reactiveRootNode;
      } else {
        connectedReplicatedModel.dispose();
      }
    },
    (reason, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        errorRef.value = reason;
      }
    },
  );

  return {
    replicatedModel: replicatedModelRef,
    rootNode: rootNodeRef,
    dispose,
    error: errorRef,
  };
}
