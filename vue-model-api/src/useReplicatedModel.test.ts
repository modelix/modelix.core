import { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { watchEffect, type Ref, ref } from "vue";
import { useModelClient } from "./useModelClient";
import { useReplicatedModel } from "./useReplicatedModel";
import IdSchemeJS = org.modelix.model.client2.IdSchemeJS;

type BranchJS = org.modelix.model.client2.MutableModelTreeJs;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;
type ClientJS = org.modelix.model.client2.ClientJS;

const { loadModelsFromJson } = org.modelix.model.client2;

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
  dispose = jest.fn();
}

test("test branch connects", (done) => {
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
  const { rootNode, replicatedModel } = useReplicatedModel(
    client,
    "aRepository",
    "aBranch",
    IdSchemeJS.MODELIX,
  );
  watchEffect(() => {
    if (rootNode.value !== null && replicatedModel.value !== null) {
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

  const { error } = useReplicatedModel(
    client,
    "aRepository",
    "aBranch",
    IdSchemeJS.MODELIX,
  );

  watchEffect(() => {
    if (error.value !== null) {
      expect(error.value).toBe("Could not connect branch.");
      done();
    }
  });
});

describe("does not start model", () => {
  const startReplicatedModel = jest.fn((repositoryId, branchId) =>
    Promise.resolve(
      new SuccessfulReplicatedModelJS(branchId) as unknown as ReplicatedModelJS,
    ),
  );

  let client: Ref<ClientJS | null>;
  class MockClientJS {
    startReplicatedModel(
      _repositoryId: string,
      _branchId: string,
    ): Promise<ReplicatedModelJS> {
      return startReplicatedModel(_repositoryId, _branchId);
    }
  }

  beforeEach(() => {
    jest.clearAllMocks();
    client = useModelClient("anURL", () =>
      Promise.resolve(new MockClientJS() as unknown as ClientJS),
    ).client;
  });

  test("if client is undefined", () => {
    useReplicatedModel(undefined, "aRepository", "aBranch", IdSchemeJS.MODELIX);
    expect(startReplicatedModel).not.toHaveBeenCalled();
  });

  test("if repositoryId is undefined", () => {
    useReplicatedModel(client, undefined, "aBranch", IdSchemeJS.MODELIX);
    expect(startReplicatedModel).not.toHaveBeenCalled();
  });

  test("if branchId is undefined", () => {
    useReplicatedModel(client, "aRepository", undefined, IdSchemeJS.MODELIX);
    expect(startReplicatedModel).not.toHaveBeenCalled();
  });

  test("if idScheme is undefined", () => {
    useReplicatedModel(client, "aRepository", "aBranch", undefined);
    expect(startReplicatedModel).not.toHaveBeenCalled();
  });

  test("if repositoryId switches to another value", async () => {
    const repositoryId = ref<string | undefined>("aRepository");
    useReplicatedModel(client, repositoryId, "aBranch", IdSchemeJS.MODELIX);
    expect(startReplicatedModel).toHaveBeenCalled();

    startReplicatedModel.mockClear();
    repositoryId.value = "aNewValue";
    await new Promise(process.nextTick);
    expect(startReplicatedModel).toHaveBeenCalled();
  });

  test("if repositoryId switches to undefined", async () => {
    const repositoryId = ref<string | undefined>("aRepository");
    useReplicatedModel(client, repositoryId, "aBranch", IdSchemeJS.MODELIX);
    expect(startReplicatedModel).toHaveBeenCalled();

    startReplicatedModel.mockClear();
    repositoryId.value = undefined;
    await new Promise(process.nextTick);
    expect(startReplicatedModel).not.toHaveBeenCalled();
  });
});
