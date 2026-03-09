import { org } from "@modelix/model-client";
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
type MutableModelTreeJs = org.modelix.model.client2.MutableModelTreeJs;
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
 * When using the URL string form together with `getToken`, a separate client is created for each
 * model so that each model can authenticate with its own token. The `getToken` callback is
 * called with the {@link ReplicatedModelParameters} of each model before the connection is
 * established, ensuring a fresh token is used even after the parameters change (e.g. a branch
 * switch).
 *
 * @param client - Reactive reference of a client to a model server, or a server URL string when
 *   used together with `getToken`.
 * @param models - Reactive reference to an array of ReplicatedModelParameters.
 * @param getToken - Optional callback that returns a bearer token for a given
 *   {@link ReplicatedModelParameters}. When provided together with a server URL as `client`, a
 *   dedicated client with this token is created for every model. The callback is invoked again
 *   each time the models change, so a fresh token is always used before connecting.
 *
 * @returns {Object} values Wrapper around different returned values.
 * @returns {Ref<ReplicatedModelJS | null>} values.replicatedModel  Reactive reference to the replicated model for the specified branches.
 * @returns {Ref<INodeJS[]>} values.rootNodes  Reactive reference to an array of root nodes with Vue.js reactivity for the specified branches.
 * @returns {() => void} values.dispose A function to manually dispose the root nodes.
 * @returns {Ref<unknown>} values.error Reactive reference to a connection error.
 */
export function useReplicatedModels(
  client: MaybeRefOrGetter<ClientJS | string | null | undefined>,
  models: MaybeRefOrGetter<ReplicatedModelParameters[] | null | undefined>,
  getToken?: (
    params: ReplicatedModelParameters,
  ) => Promise<string | null>,
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
    replicatedModel = null;
    replicatedModelRef.value = null;
    rootNodesRef.value = [];
    errorRef.value = null;
  };

  useLastPromiseEffect<{
    replicatedModel: ReplicatedModelJS;
    branches: MutableModelTreeJs[];
    cache: Cache<ReactiveINodeJS>;
  }>(
    async () => {
      dispose();
      const clientOrUrl = toValue(client);
      if (!isDefined(clientOrUrl)) {
        return;
      }
      const modelsValue = toValue(models);
      if (!isDefined(modelsValue)) {
        return;
      }
      const cache = new Cache<ReactiveINodeJS>();

      if (typeof clientOrUrl === "string" && getToken !== undefined) {
        // Per-model client mode: each model gets its own dedicated client and token.
        // This ensures a fresh token is fetched before connecting, and that separate
        // models can use different tokens simultaneously.
        const serverUrl = clientOrUrl;
        const perModelClients: ClientJS[] = [];
        const perModelReplicatedModels: ReplicatedModelJS[] = [];

        for (const params of modelsValue) {
          const perModelClient =
            await org.modelix.model.client2.connectClient(
              serverUrl,
              () => getToken(params),
            );
          perModelClients.push(perModelClient);
          const replicatedModelForParams =
            await perModelClient.startReplicatedModel(
              params.repositoryId,
              params.branchId,
              params.idScheme,
            );
          perModelReplicatedModels.push(replicatedModelForParams);
        }

        const branches = perModelReplicatedModels.map((rm) => rm.getBranch());

        // Wrap all per-model instances so a single dispose() cleans them all up.
        const combinedReplicatedModel: ReplicatedModelJS = {
          getBranch: () => branches[0],
          dispose: () => {
            perModelReplicatedModels.forEach((rm) => rm.dispose());
            perModelClients.forEach((c) => c.dispose());
          },
          getCurrentVersionInformation: () =>
            perModelReplicatedModels[0].getCurrentVersionInformation(),
          getCurrentVersionInformations: () =>
            Promise.all(
              perModelReplicatedModels.map((rm) =>
                rm.getCurrentVersionInformations(),
              ),
            ).then((results) => {
              const combined: org.modelix.model.client2.VersionInformationJS[] =
                [];
              for (const result of results) {
                combined.push(...Array.from(result));
              }
              return combined as unknown as Array<org.modelix.model.client2.VersionInformationJS>;
            }),
        } as unknown as ReplicatedModelJS;

        return { replicatedModel: combinedReplicatedModel, branches, cache };
      }

      // Standard mode: use the provided ClientJS (existing behaviour).
      if (typeof clientOrUrl === "string") {
        // URL provided without getToken — cannot create a client without credentials.
        return;
      }

      return clientOrUrl
        .startReplicatedModels(modelsValue)
        .then((connectedReplicatedModel) => ({
          replicatedModel: connectedReplicatedModel,
          branches: [connectedReplicatedModel.getBranch()],
          cache,
        }));
    },
    (
      { replicatedModel: connectedReplicatedModel, branches, cache },
      isResultOfLastStartedPromise,
    ) => {
      if (isResultOfLastStartedPromise) {
        replicatedModel = connectedReplicatedModel;
        const allRootNodes: INodeJS[] = [];
        for (const branch of branches) {
          branch.addListener((change: ChangeJS) => {
            if (cache === null) {
              throw Error("The cache is unexpectedly not set up.");
            }
            handleChange(change, cache);
          });
          allRootNodes.push(...Array.from(branch.getRootNodes()));
        }
        const reactiveRootNodes = allRootNodes.map((node) =>
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
