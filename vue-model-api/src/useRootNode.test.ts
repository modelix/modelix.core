import { org } from "@modelix/model-client";
import { INodeJS } from "@modelix/ts-model-api";
import { watchEffect } from "vue";
import { useModelClient } from "./useModelClient";
import { useRootNode } from "./useRootNode";

type BranchJS = org.modelix.model.client2.BranchJS;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;
type ClientJS = org.modelix.model.client2.ClientJS;

const { loadModelsFromJson } = org.modelix.model.client2;

test("test branch connects", (done) => {
  class SuccessfulBranchJS {
    public rootNode: INodeJS;

    constructor(branchId: string) {
      const root = {
        root: {},
      };

      this.rootNode = loadModelsFromJson([JSON.stringify(root)]);
      this.rootNode.setPropertyValue("branchId", branchId);
    }

    addListener = jest.fn();
  }

  class SuccessfulReplicatedModelJS {
    private branch: BranchJS;
    constructor(branchId: string) {
      this.branch = new SuccessfulBranchJS(branchId) as unknown as BranchJS;
    }

    getBranch() {
      return this.branch;
    }
  }

  class SuccessfulClientJS {
    startReplicatedModel(
      _repositoryId: string,
      branchId: string,
    ): Promise<ReplicatedModelJS> {
      return Promise.resolve(
        new SuccessfulReplicatedModelJS(
          branchId,
        ) as unknown as ReplicatedModelJS,
      );
    }
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new SuccessfulClientJS() as unknown as ClientJS),
  );

  const { rootNode } = useRootNode(client, "aRepository", "aBranch");

  watchEffect(() => {
    if (rootNode.value !== null) {
      expect(rootNode.value.getPropertyValue("branchId")).toBe("aBranch");
      done();
    }
  });
});

test("test branch connection error is exposed", (done) => {
  class FailingClientJS {
    startReplicatedModel(
      _repositoryId: string,
      _branchId: string,
    ): Promise<BranchJS> {
      return Promise.reject("Could not connect branch.");
    }
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new FailingClientJS() as unknown as ClientJS),
  );

  const { error } = useRootNode(client, "aRepository", "aBranch");

  watchEffect(() => {
    if (error.value !== null) {
      expect(error.value).toBe("Could not connect branch.");
      done();
    }
  });
});
