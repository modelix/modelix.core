import { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { useLastPromiseEffect } from "./internal/useLastPromiseEffect";
import type { MaybeRefOrGetter, Ref } from "vue";
import { shallowRef, toValue, computed } from "vue";
import type { ReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { toReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { Cache } from "./internal/Cache";
import { handleChange } from "./internal/handleChange";

type ClientJS = org.modelix.model.client2.ClientJS;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;
type ChangeJS = org.modelix.model.client2.ChangeJS;
type ReplicatedModelParameters =
  org.modelix.model.client2.ReplicatedModelParameters;

function isDefined<T>(value: T | null | undefined): value is T {
  return value !== null && value !== undefined;
}

/**
 * Creates replicated models for the given repositories and branches.
 * A replicated model exposes a branch that can be used to read and write model data.
 * The written model data is automatically synced to the model server.
 * Changes from the model server are automatically synced to the branch in the replicated model.
 *
 * Also creates root nodes that use Vue's reactivity and can be used in Vue like reactive objects.
 * Changes to model data trigger recalculation of computed properties or re-rendering of components using that data.
 *
 * Calling the returned dispose function stops syncing the root nodes to the underlying branches on the server.
 *
 * @param client - Reactive reference of a client to a model server.
 * @param models - Reactive reference to an array of ReplicatedModelParameters.
 *
 * @returns {Object} values Wrapper around different returned values.
 * @returns {Ref<ReplicatedModelJS | null>} values.replicatedModel  Reactive reference to the replicated model for the specified branches.
 * @returns {Ref<INodeJS[]>} values.rootNodes  Reactive reference to an array of root nodes with Vue.js reactivity for the specified branches.
 * @returns {() => void} values.dispose A function to manually dispose the root nodes.
 * @returns {Ref<unknown>} values.error Reactive reference to a connection error.
 */
export function useReplicatedModels(
  client: MaybeRefOrGetter<ClientJS | null | undefined>,
  models: MaybeRefOrGetter<ReplicatedModelParameters[] | null | undefined>,
): {
  replicatedModel: Ref<ReplicatedModelJS | null>;
  rootNodes: Ref<INodeJS[]>;
  dispose: () => void;
  error: Ref<unknown>;
} {
  // Use `replicatedModel` to access the replicated model without tracking overhead of Vue.js.
  let replicatedModel: ReplicatedModelJS | null = null;
  const replicatedModelRef: Ref<ReplicatedModelJS | null> = shallowRef(null);
  const rootNodesRef: Ref<INodeJS[]> = shallowRef([]);
  const errorRef: Ref<unknown> = shallowRef(null);

  const dispose = () => {
    // Using `replicatedModelRef.value` here would create a circular dependency.
    // `toRaw` does not work on `Ref<>`.
    if (replicatedModel !== null) {
      replicatedModel.dispose();
    }
    replicatedModelRef.value = null;
    rootNodesRef.value = [];
    errorRef.value = null;
  };

  useLastPromiseEffect<{
    replicatedModel: ReplicatedModelJS;
    cache: Cache<ReactiveINodeJS>;
  }>(
    () => {
      dispose();
      const clientValue = toValue(client);
      if (!isDefined(clientValue)) {
        return;
      }
      const modelsValue = toValue(models);
      if (!isDefined(modelsValue)) {
        return;
      }
      const cache = new Cache<ReactiveINodeJS>();
      return clientValue
        .startReplicatedModels(modelsValue)
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
        const unreactiveRootNodes = branch.getRootNodes();
        const reactiveRootNodes = unreactiveRootNodes.map((node) =>
          toReactiveINodeJS(node, cache),
        );
        replicatedModelRef.value = replicatedModel;
        rootNodesRef.value = reactiveRootNodes;
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
    rootNodes: rootNodesRef,
    dispose,
    error: errorRef,
  };
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
 *
 * @deprecated Use {@link useReplicatedModels} instead.
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
  const models = computed(() => {
    const repositoryIdValue = toValue(repositoryId);
    const branchIdValue = toValue(branchId);
    const idSchemeValue = toValue(idScheme);
    if (!repositoryIdValue || !branchIdValue || !idSchemeValue) {
      return null;
    }
    return [
      new org.modelix.model.client2.ReplicatedModelParameters(
        repositoryIdValue,
        branchIdValue,
        idSchemeValue,
      ),
    ];
  });

  const result = useReplicatedModels(client, models);

  // Extract the single root node from the array for backward compatibility
  const rootNode = computed(() => result.rootNodes.value[0] ?? null);

  return {
    replicatedModel: result.replicatedModel,
    rootNode: rootNode,
    dispose: result.dispose,
    error: result.error,
  };
}
