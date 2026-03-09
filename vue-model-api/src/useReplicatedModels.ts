import { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { useLastPromiseEffect } from "./internal/useLastPromiseEffect";
import type { MaybeRefOrGetter, Ref } from "vue";
import { shallowRef, toValue } from "vue";
import type { ReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { toReactiveINodeJS } from "./internal/ReactiveINodeJS";
import { Cache } from "./internal/Cache";
import { handleChange } from "./internal/handleChange";

const { connectClient } = org.modelix.model.client2;

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
 * When a server URL string is passed as `client`, a single {@link ClientJS} is created and
 * **shared across all models and across branch switches**. This preserves the version cache so
 * that switching branches only fetches the delta. If `getToken` is also supplied, it is called
 * with the first model's {@link ReplicatedModelParameters} before every call to
 * {@link ClientJS.startReplicatedModels}, ensuring a fresh token is used even after the
 * parameters change (e.g. a branch switch). The token is provided to the underlying HTTP client
 * as a dynamic bearer-token callback, so it is also picked up by the Ktor refresh flow on 401.
 *
 * @param client - Reactive reference of a client to a model server, or a server URL string.
 *   When a URL string is provided a {@link ClientJS} is created internally and reused until the
 *   URL changes.
 * @param models - Reactive reference to an array of {@link ReplicatedModelParameters}.
 * @param getToken - Optional callback that returns a bearer token for a given
 *   {@link ReplicatedModelParameters}. Only used when `client` is a URL string. Called before
 *   each connection attempt so the token is always fresh.
 * @param createClient - Internal seam for testing; defaults to {@link connectClient}.
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
  createClient: (
    url: string,
    tokenProvider?: () => Promise<string | null>,
  ) => Promise<ClientJS> = connectClient,
): {
  replicatedModel: Ref<ReplicatedModelJS | null>;
  rootNodes: Ref<INodeJS[]>;
  dispose: () => void;
  error: Ref<unknown>;
} {
  // ---------------------------------------------------------------------------
  // URL-based client state
  // A single ClientJS is kept per URL and reused across model changes so that
  // the version cache is preserved (branch switches only fetch deltas).
  // ---------------------------------------------------------------------------
  const urlClientRef: Ref<ClientJS | null> = shallowRef(null);

  // The token provider reads this mutable variable so every Ktor request
  // automatically uses the token for the currently active models.
  let currentModelsForToken: ReplicatedModelParameters[] = [];
  const tokenProvider = (): Promise<string | null> => {
    if (currentModelsForToken.length === 0) return Promise.resolve(null);
    return getToken!(currentModelsForToken[0]);
  };

  // ---------------------------------------------------------------------------
  // Replicated-model state
  // ---------------------------------------------------------------------------
  // Use a plain variable (not a ref) to avoid Vue reactivity overhead on writes.
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
    if (urlClientRef.value !== null) {
      urlClientRef.value.dispose();
      urlClientRef.value = null;
    }
  };

  // ---------------------------------------------------------------------------
  // Effect 1: manage the URL → ClientJS lifecycle.
  // Re-runs when `client` changes. When the URL changes the old ClientJS is
  // disposed and a new one is created (with the same token-provider closure).
  // ---------------------------------------------------------------------------
  useLastPromiseEffect<ClientJS>(
    () => {
      // Dispose old URL client (the replicated model will be cleaned up by
      // effect 2 when it observes urlClientRef becoming null).
      if (urlClientRef.value !== null) {
        urlClientRef.value.dispose();
        urlClientRef.value = null;
      }

      const clientOrUrl = toValue(client);
      if (!isDefined(clientOrUrl) || typeof clientOrUrl !== "string") {
        return;
      }

      return createClient(
        clientOrUrl,
        getToken !== undefined ? tokenProvider : undefined,
      );
    },
    (createdClient, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        urlClientRef.value = createdClient;
      } else {
        // A newer effect superseded this one; discard the client.
        createdClient.dispose();
      }
    },
    (reason, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        errorRef.value = reason;
      }
    },
  );

  // ---------------------------------------------------------------------------
  // Effect 2: connect/reconnect the replicated model.
  // Re-runs when `client`, `models`, or `urlClientRef` (the resolved URL
  // client) change.
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
          return; // Client not yet ready; will re-run once effect 1 resolves.
        }
        // Update the token context so the provider returns the right token for
        // these models on every subsequent Ktor request.
        currentModelsForToken = modelsValue;
        resolvedClient = urlClient;
      } else {
        resolvedClient = clientOrUrl;
      }

      const cache = new Cache<ReactiveINodeJS>();
      return resolvedClient
        .startReplicatedModels(modelsValue)
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
