import type { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { useLastPromiseEffect } from "./internal/useLastPromiseEffect";
import type { MaybeRefOrGetter, Ref } from "vue";
import { shallowRef, toValue } from "vue";
import type { ReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { toReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { Cache } from "./internal/Cache";
import { handleChange } from "./internal/handleChange";
import { ReadOnlyNodeJS } from "./ReadonlyNodeJS";

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
    params: ReplicatedModelParameters[];
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
        .then((replicatedModel) => ({
          replicatedModel,
          params: modelsValue,
          cache,
        }));
    },
    (
      { replicatedModel: connectedReplicatedModel, params, cache },
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
        const reactiveRootNodes = unreactiveRootNodes.map((node, index) => {
          const reactiveNode = toReactiveINodeJS(node, cache);
          const param = params[index];
          if (param && param.readonly) {
            return new ReadOnlyNodeJS(reactiveNode, () =>
              console.warn("Cannot modify a readonly node."),
            );
          }
          return reactiveNode;
        });
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
