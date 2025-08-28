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
type ChangeJS = org.modelix.model.client2.ChangeJS;
type VersionInformationJS = org.modelix.model.client2.VersionInformationJS;

function isDefined<T>(value: T | null | undefined): value is T {
  return value !== null && value !== undefined;
}

/**
 *
 * */
export function useReadonlyVersion(
  client: MaybeRefOrGetter<ClientJS | null | undefined>,
  repositoryId: MaybeRefOrGetter<string | null | undefined>,
  versionHash: MaybeRefOrGetter<string | null | undefined>,
): {
  versionInformation: Ref<VersionInformationJS | null>;
  rootNode: Ref<INodeJS | null>;
  dispose: () => void;
  error: Ref<unknown>;
} {
  const versionInformationRef: Ref<VersionInformationJS | null> =
    shallowRef(null);
  const rootNodeRef: Ref<INodeJS | null> = shallowRef(null);
  const errorRef: Ref<unknown> = shallowRef(null);

  const dispose = () => {
    versionInformationRef.value = null;
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
      const versionHashValue = toValue(versionHash);
      if (!isDefined(versionHashValue)) {
        return;
      }
      const cache = new Cache<ReactiveINodeJS>();
      return clientValue
        .loadReadonlyVersion(repositoryIdValue, versionHashValue)
        .then(({ version, tree }) => ({
          versionInfo: version,
          tree,
          cache,
        }));
    },
    ({ versionInfo, tree, cache }, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        tree.addListener((change: ChangeJS) => {
          if (cache === null) {
            throw Error("The cache is unexpectedly not set up.");
          }
          handleChange(change, cache);
        });
        const unreactiveRootNode = tree.rootNode;
        const reactiveRootNode = toReactiveINodeJS(unreactiveRootNode, cache);
        versionInformationRef.value = versionInfo;
        rootNodeRef.value = reactiveRootNode;
      }
    },
    (reason, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        errorRef.value = reason;
      }
    },
  );

  return {
    versionInformation: versionInformationRef,
    rootNode: rootNodeRef,
    dispose,
    error: errorRef,
  };
}
