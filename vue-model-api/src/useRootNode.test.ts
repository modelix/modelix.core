import { org } from "@modelix/model-client";
import { INodeJS } from "@modelix/model-client";
import { watchEffect } from "vue";
import { useModelClient } from "./useModelClient";
import { useRootNode } from "./useRootNode";

type BranchJS = org.modelix.model.client2.BranchJS;
type ClientJS = org.modelix.model.client2.ClientJS;
type ChangeJS = org.modelix.model.client2.ChangeJS;
const { loadModelsFromJson } = org.modelix.model.client2;

test("test branch connects", (done) => {
  class SuccessfulBranchJS {
    public rootNode: INodeJS;

    constructor(branchId: string, changeCallback: (change: ChangeJS) => void) {
      const root = {
        root: {},
      };

      this.rootNode = loadModelsFromJson(
        [JSON.stringify(root)],
        changeCallback,
      );
      this.rootNode.setPropertyValue("branchId", branchId);
    }
  }

  class SuccessfulClientJS {
    connectBranch(
      _repositoryId: string,
      branchId: string,
      changeCallback: (change: ChangeJS) => void,
    ): Promise<BranchJS> {
      return Promise.resolve(
        new SuccessfulBranchJS(branchId, changeCallback) as BranchJS,
      );
    }
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new SuccessfulClientJS() as ClientJS),
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
    connectBranch(
      _repositoryId: string,
      _branchId: string,
      _changeCallback: (change: ChangeJS) => void,
    ): Promise<BranchJS> {
      return Promise.reject("Could not connect branch.");
    }
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new FailingClientJS() as ClientJS),
  );

  const { error } = useRootNode(client, "aRepository", "aBranch");

  watchEffect(() => {
    if (error.value !== null) {
      expect(error.value).toBe("Could not connect branch.");
      done();
    }
  });
});
