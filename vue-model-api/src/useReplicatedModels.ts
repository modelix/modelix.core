import { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { useLastPromiseEffect } from "./internal/useLastPromiseEffect";
import type { MaybeRefOrGetter, Ref } from "vue";
import { shallowRef, toValue } from "vue";
import type { ReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { toReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { Cache } from "./internal/Cache";
import { handleChange } from "./internal/handleChange";
import { useModelClient } from "./useModelClient";

type ClientJS = org.modelix.model.client2.ClientJS;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;
type ChangeJS = org.modelix.model.client2.ChangeJS;
type ReplicatedModelParameters =
  org.modelix.model.client2.ReplicatedModelParameters;

const { ReplicatedModelParameters: ReplicatedModelParametersCtor } =
  org.modelix.model.client2;

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
 * When a server URL string is passed as `client`, a single {@link ClientJS} is created and
 * **shared across all models and across branch switches** via {@link useModelClient}. This
 * preserves the version cache so that switching branches only fetches the delta, and cross-model
 * references continue to resolve correctly.
 *
 * If `getToken` is also supplied, a fresh token is obtained by calling `getToken(params)` before
 * every HTTP request for that binding via {@link ReplicatedModelParameters.tokenProvider}. Each
 * model in the `models` array receives an independent token provider, so concurrent connections
 * to different repositories or branches each use their own credentials.
 *
 * @param client - Reactive reference of a client to a model server, or a server URL string.
 *   When a URL string is provided a {@link ClientJS} is created internally via
 *   {@link useModelClient} and reused until the URL changes.
 * @param models - Reactive reference to an array of {@link ReplicatedModelParameters}.
 * @param getToken - Optional callback that returns a bearer token for a given
 *   {@link ReplicatedModelParameters}. Only used when `client` is a URL string. Called on every
 *   HTTP request for the corresponding binding so the token is always fresh.
 * @param createClient - Internal seam for testing; defaults to {@link connectClient} via
 *   {@link useModelClient}.
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
  getToken?: (params: ReplicatedModelParameters) => Promise<string | null>,
  createClient?: (url: string) => Promise<ClientJS>,
): {
  replicatedModel: Ref<ReplicatedModelJS | null>;
  rootNodes: Ref<INodeJS[]>;
  dispose: () => void;
  error: Ref<unknown>;
} {
  // ---------------------------------------------------------------------------
  // URL-based client lifecycle (mirrors useModelClient internally).
  // When `client` is a URL string, useModelClient creates and disposes the
  // ClientJS automatically when the URL changes — preserving the version cache
  // across branch switches and enabling cross-model reference resolution.
  // When `client` is a ClientJS, the getter always returns null so no
  // extra client is managed here.
  // ---------------------------------------------------------------------------
  const { client: urlClientRef, dispose: disposeUrlClient } = useModelClient(
    () => {
      const c = toValue(client);
      return typeof c === "string" ? c : null;
    },
    createClient,
  );

  // ---------------------------------------------------------------------------
  // Replicated-model state
  // ---------------------------------------------------------------------------
  let replicatedModel: ReplicatedModelJS | null = null;
  const replicatedModelRef: Ref<ReplicatedModelJS | null> = shallowRef(null);
  const rootNodesRef: Ref<INodeJS[]> = shallowRef([]);
  const errorRef: Ref<unknown> = shallowRef(null);

  const disposeReplicatedModel = () => {
    if (replicatedModel !== null) {
      replicatedModel.dispose();
    }
    replicatedModel = null;
    replicatedModelRef.value = null;
    rootNodesRef.value = [];
    errorRef.value = null;
  };

  const dispose = () => {
    disposeReplicatedModel();
    disposeUrlClient();
  };

  // ---------------------------------------------------------------------------
  // Effect: connect/reconnect when client or models change.
  // Re-runs whenever `client`, `urlClientRef` (the resolved URL client), or
  // `models` changes.
  // ---------------------------------------------------------------------------
  useLastPromiseEffect<{
    replicatedModel: ReplicatedModelJS;
    cache: Cache<ReactiveINodeJS>;
  }>(
    () => {
      disposeReplicatedModel();

      const clientOrUrl = toValue(client);
      if (!isDefined(clientOrUrl)) {
        return;
      }

      const modelsValue = toValue(models);
      if (!isDefined(modelsValue)) {
        return;
      }

      let resolvedClient: ClientJS;

      if (typeof clientOrUrl === "string") {
        // Reactive read — re-runs this effect when the URL client becomes ready.
        const urlClient = urlClientRef.value;
        if (urlClient === null) {
          return; // Client not yet ready; will re-run once useModelClient resolves.
        }
        resolvedClient = urlClient;
      } else {
        resolvedClient = clientOrUrl;
      }

      // Attach per-binding token providers.  Each model receives an independent
      // provider callback so concurrent connections to different branches each
      // use their own fresh credentials.
      const paramsWithTokens =
        getToken !== undefined
          ? modelsValue.map(
              (p) =>
                new ReplicatedModelParametersCtor(
                  p.repositoryId,
                  p.branchId,
                  p.idScheme,
                  () => getToken(p),
                ),
            )
          : modelsValue;

      const cache = new Cache<ReactiveINodeJS>();
      return resolvedClient
        .startReplicatedModels(paramsWithTokens)
        .then((rm) => ({ replicatedModel: rm, cache }));
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
