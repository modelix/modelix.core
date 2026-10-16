import type { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { useLastPromiseEffect } from "./internal/useLastPromiseEffect";
import type { MaybeRefOrGetter, Ref } from "vue";
import { shallowRef, toValue } from "vue";
import { ReactiveModel } from "./internal/ReactiveModel";

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
      const reactiveModel = new ReactiveModel();
      return clientValue
        .loadReadonlyVersion(repositoryIdValue, versionHashValue)
        .then(({ version, tree }) => ({
          versionInfo: version,
          tree,
          reactiveModel,
        }));
    },
    ({ versionInfo, tree, reactiveModel }, isResultOfLastStartedPromise) => {
      if (isResultOfLastStartedPromise) {
        tree.addListener((change: ChangeJS) => {
          reactiveModel.handleChange(change);
        });
        const unreactiveRootNode = tree.rootNode;
        const reactiveRootNode = reactiveModel.wrap(unreactiveRootNode);
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
