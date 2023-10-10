import { org } from "@modelix/model-client";
import { INodeJS } from "@modelix/ts-model-api";
import { watchEffect } from "vue";
import { useModelClient } from "./useModelClient";
import { useRootNode } from "./useRootNode";

type BranchJS = org.modelix.model.client2.BranchJS;
type ClientJS = org.modelix.model.client2.ClientJS;
type ChangeJS = org.modelix.model.client2.ChangeJS;
const { loadModelsFromJson } = org.modelix.model.client2;

const root = {
  root: {},
};

function getClient(_url: string): Promise<ClientJS> {
  return Promise.resolve(new TestClientJS() as unknown as ClientJS);
}

class TestBranchJS implements BranchJS {
  public disposed = false;
  public rootNode: INodeJS;

  constructor(branchId: string, changeCallback: (change: ChangeJS) => void) {
    this.rootNode = loadModelsFromJson([JSON.stringify(root)], changeCallback);
    this.rootNode.setPropertyValue("branchId", branchId);
  }

  dispose(): void {
    this.disposed = true;
  }

  // @ts-ignore It is fine to be undefined, because we do not pass it back to Kotlin code.
  __doNotUseOrImplementIt: undefined;
}

class TestClientJS implements ClientJS {
  public disposed = false;

  dispose(): void {
    this.disposed = true;
  }
  connectBranch(
    _repositoryId: string,
    branchId: string,
    changeCallback: (change: ChangeJS) => void,
  ): Promise<BranchJS> {
    return Promise.resolve(
      new TestBranchJS(branchId, changeCallback) as unknown as BranchJS,
    );
  }
  fetchBranches(_repositoryId: string): Promise<string[]> {
    throw new Error("Method not implemented.");
  }
  fetchRepositories(): Promise<string[]> {
    throw new Error("Method not implemented.");
  }

  // @ts-ignore It is fine to be undefined, because we do not pass it back to Kotlin code.
  __doNotUseOrImplementIt: undefined;
}

test("test branch connects", (done) => {
  const { client } = useModelClient("anURL", getClient);
  const { rootNode } = useRootNode(client, "aRepository", "aBranch");
  watchEffect(() => {
    if (rootNode.value !== null) {
      expect(rootNode.value.getPropertyValue("branchId")).toBe("aBranch");
      done();
    }
  });
});
