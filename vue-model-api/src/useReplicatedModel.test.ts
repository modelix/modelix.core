import { org } from "@modelix/model-client";
import { toRoleJS } from "@modelix/ts-model-api";
import { watchEffect } from "vue";
import { useModelClient } from "./useModelClient";
import { useReplicatedModel } from "./useReplicatedModel";
import IdSchemeJS = org.modelix.model.client2.IdSchemeJS;

type ClientJS = org.modelix.model.client2.ClientJS;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;

const { loadModelsFromJson } = org.modelix.model.client2;

import ReplicatedModelParameters = org.modelix.model.client2.ReplicatedModelParameters;

test("test wrapper backwards compatibility", (done) => {
  /* eslint-disable */
  class SuccessfulClientJS implements ClientJS {
    startReplicatedModel(
      repositoryId: string,
      branchId: string,
      idScheme: org.modelix.model.client2.IdSchemeJS,
    ): Promise<org.modelix.model.client2.ReplicatedModelJS> {
      // Mock implementation that returns a dummy object with a branch
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

    startReplicatedModels(
      parameters: ReplicatedModelParameters[],
    ): Promise<ReplicatedModelJS> {
      // Mock implementation that returns a dummy object with a branch
      const branchId = parameters[0].branchId;
      const rootNode = loadModelsFromJson([JSON.stringify({ root: {} })]);
      rootNode.setPropertyValue(toRoleJS("branchId"), branchId ?? undefined);

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

    readonly __doNotUseOrImplementIt: any;

    createBranch(
      repositoryId: string,
      branchId: string,
      versionHash: string,
    ): Promise<void> {
      throw Error("Not implemented");
    }

    deleteBranch(repositoryId: string, branchId: string): Promise<boolean> {
      throw Error("Not implemented");
    }

    diffAsMutationParameters(
      repositoryId: string,
      newVersion: string,
      oldVersion: string,
    ): Promise<Array<org.modelix.model.client2.MutationParametersJS>> {
      throw Error("Not implemented");
    }

    dispose(): void {
      throw Error("Not implemented");
    }

    fetchBranches(repositoryId: string): Promise<Array<string>> {
      throw Error("Not implemented");
    }

    fetchBranchesWithHashes(
      repositoryId: string,
    ): Promise<Array<org.modelix.model.server.api.BranchInfo>> {
      throw Error("Not implemented");
    }

    fetchRepositories(): Promise<Array<string>> {
      throw Error("Not implemented");
    }

    getHistoryForFixedIntervals(
      repositoryId: string,
      headVersion: string,
      intervalDurationSeconds: number,
      skip: number,
      limit: number,
    ): Promise<Array<org.modelix.model.client2.HistoryIntervalJS>> {
      throw Error("Not implemented");
    }

    getHistoryForFixedIntervalsForBranch(
      repositoryId: string,
      branchId: string,
      intervalDurationSeconds: number,
      skip: number,
      limit: number,
    ): Promise<Array<org.modelix.model.client2.HistoryIntervalJS>> {
      throw Error("Not implemented");
    }

    getHistoryForProvidedIntervals(
      repositoryId: string,
      headVersion: string,
      splitAt: Array<Date>,
    ): Promise<Array<org.modelix.model.client2.HistoryIntervalJS>> {
      throw Error("Not implemented");
    }

    getHistoryForProvidedIntervalsForBranch(
      repositoryId: string,
      branchId: string,
      splitAt: Array<Date>,
    ): Promise<Array<org.modelix.model.client2.HistoryIntervalJS>> {
      throw Error("Not implemented");
    }

    getHistoryRange(
      repositoryId: string,
      headVersion: string,
      skip: number,
      limit: number,
    ): Promise<Array<org.modelix.model.client2.VersionInformationJS>> {
      throw Error("Not implemented");
    }

    getHistoryRangeForBranch(
      repositoryId: string,
      branchId: string,
      skip: number,
      limit: number,
    ): Promise<Array<org.modelix.model.client2.VersionInformationJS>> {
      throw Error("Not implemented");
    }

    getHistorySessions(
      repositoryId: string,
      headVersion: string,
      delaySeconds: number,
      skip: number,
      limit: number,
    ): Promise<Array<org.modelix.model.client2.HistoryIntervalJS>> {
      throw Error("Not implemented");
    }

    getHistorySessionsForBranch(
      repositoryId: string,
      branchId: string,
      delaySeconds: number,
      skip: number,
      limit: number,
    ): Promise<Array<org.modelix.model.client2.HistoryIntervalJS>> {
      throw Error("Not implemented");
    }

    initRepository(repositoryId: string, useRoleIds?: boolean): Promise<void> {
      throw Error("Not implemented");
    }

    loadReadonlyVersion(
      repositoryId: string,
      versionHash: string,
    ): Promise<org.modelix.model.client2.VersionInformationWithModelTree> {
      throw Error("Not implemented");
    }

    revertTo(
      repositoryId: string,
      branchId: string,
      targetVersionHash: string,
    ): Promise<string> {
      throw Error("Not implemented");
    }

    setClientProvidedUserId(userId: string): void {}
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
