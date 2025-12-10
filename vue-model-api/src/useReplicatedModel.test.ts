import { org } from "@modelix/model-client";
import { toRoleJS } from "@modelix/ts-model-api";
import { watchEffect } from "vue";
import { useModelClient } from "./useModelClient";
import { useReplicatedModel } from "./useReplicatedModels";
import IdSchemeJS = org.modelix.model.client2.IdSchemeJS;

type ClientJS = org.modelix.model.client2.ClientJS;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;

const { loadModelsFromJson } = org.modelix.model.client2;

import ReplicatedModelParameters = org.modelix.model.client2.ReplicatedModelParameters;

test("test wrapper backwards compatibility", (done) => {
  class SuccessfulClientJS {
    startReplicatedModels(
      parameters: ReplicatedModelParameters[],
    ): Promise<ReplicatedModelJS> {
      // Mock implementation that returns a dummy object with a branch
      const branchId = parameters[0].branchId;
      const rootNode = loadModelsFromJson([JSON.stringify({ root: {} })]);
      rootNode.setPropertyValue(toRoleJS("branchId"), branchId);

      const branch = {
        rootNode,
        getRootNodes: () => [rootNode],
        addListener: jest.fn(),
        removeListener: jest.fn(),
        resolveNode: jest.fn(),
      };

      const replicatedModel = {
        getBranch: () => branch,
        dispose: jest.fn(),
        getCurrentVersionInformation: jest.fn(),
      } as unknown as ReplicatedModelJS;

      return Promise.resolve(replicatedModel);
    }
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new SuccessfulClientJS() as unknown as ClientJS),
  );

  const { rootNode, replicatedModel } = useReplicatedModel(
    client,
    "aRepository",
    "aBranch",
    IdSchemeJS.MODELIX,
  );

  watchEffect(() => {
    if (rootNode.value !== null && replicatedModel.value !== null) {
      expect(rootNode.value.getPropertyValue(toRoleJS("branchId"))).toBe(
        "aBranch",
      );
      done();
    }
  });
});
