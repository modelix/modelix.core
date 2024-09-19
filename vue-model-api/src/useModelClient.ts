import type { MaybeRefOrGetter, Ref } from "vue";
import { shallowRef, toValue } from "vue";
import { useLastPromiseEffect } from "./internal/useLastPromiseEffect";
import { org } from "@modelix/model-client";

const { connectClient } = org.modelix.model.client2;

type ClientJS = org.modelix.model.client2.ClientJS;

/**
 * Creates a model client for a given URL.
 *
 * The URL is reactive and if it changes, the client is automatically disposed and a new client for the updated URL is created.
 *
 * @experimental This feature is expected to be finalized with https://issues.modelix.org/issue/MODELIX-500.
 *
 * @param url - Reactive reference of an URL to a model server.
 * @param getClient - Function how to create a cliente given an URL.
 *
 * Defaults to connecting directly to the modelix model server under the given URL.
 *
 * @returns {Object} values Wrapper around diffrent returned values.
 * @returns {Ref<ClientJS | null>} values.client Reactive reference to a client.
 * @returns {() => void} values.dispose A function to manually dispose the client.
 * @returns {Ref<unknown>} values.error Reactive reference to a client connection error.
 */
export function useModelClient(
  url: MaybeRefOrGetter<string>,
  getClient: (url: string) => Promise<ClientJS> = connectClient,
): {
  client: Ref<ClientJS | null>;
  dispose: () => void;
  error: Ref<unknown>;
} {
  let client: ClientJS | null = null;
  const clientRef: Ref<ClientJS | null> = shallowRef(client);
  const errorRef: Ref<unknown> = shallowRef(null);
  const dispose = () => {
    if (client !== null) {
      client.dispose();
    }
    client = null;
    clientRef.value = null;
    errorRef.value = null;
  };
  useLastPromiseEffect(
    () => {
      dispose();
      return getClient(toValue(url));
    },
    (createdClient, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        client = createdClient;
        clientRef.value = client;
      } else {
        createdClient.dispose();
      }
    },
    (reason, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        errorRef.value = reason;
      }
    },
  );

  return {
    client: clientRef,
    dispose,
    error: errorRef,
  };
}
