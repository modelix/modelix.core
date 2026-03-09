import { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { toRoleJS } from "@modelix/ts-model-api";
import { watchEffect, type Ref, ref } from "vue";
import { useModelClient } from "./useModelClient";
import { useReplicatedModels } from "./useReplicatedModels";
import { ReadOnlyNodeJS } from "./ReadonlyNodeJS";
import IdSchemeJS = org.modelix.model.client2.IdSchemeJS;
import ReplicatedModelParameters = org.modelix.model.client2.ReplicatedModelParameters;

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
    this.rootNode.setPropertyValue(toRoleJS("branchId"), branchId);
  }

  getRootNodes() {
    return [this.rootNode];
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
    startReplicatedModels(
      parameters: ReplicatedModelParameters[],
    ): Promise<ReplicatedModelJS> {
      // For this test, we assume only one model is requested and we use its branchId
      const branchId = parameters[0].branchId;
      return Promise.resolve(
        new SuccessfulReplicatedModelJS(
          branchId!,
        ) as unknown as ReplicatedModelJS,
      );
    }
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new SuccessfulClientJS() as unknown as ClientJS),
  );
  const { rootNodes, replicatedModel } = useReplicatedModels(client, [
    new ReplicatedModelParameters("aRepository", "aBranch", IdSchemeJS.MODELIX),
  ]);
  watchEffect(() => {
    if (rootNodes.value.length > 0 && replicatedModel.value !== null) {
      expect(rootNodes.value[0].getPropertyValue(toRoleJS("branchId"))).toBe(
        "aBranch",
      );
      done();
    }
  });
});

test("test branch connection error is exposed", (done) => {
  class FailingClientJS {
    startReplicatedModels(
      _parameters: ReplicatedModelParameters[],
    ): Promise<BranchJS> {
      return Promise.reject("Could not connect branch.");
    }
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new FailingClientJS() as unknown as ClientJS),
  );

  const { error } = useReplicatedModels(client, [
    new ReplicatedModelParameters("aRepository", "aBranch", IdSchemeJS.MODELIX),
  ]);

  watchEffect(() => {
    if (error.value !== null) {
      expect(error.value).toBe("Could not connect branch.");
      done();
    }
  });
});

describe("does not start model", () => {
  const startReplicatedModels = jest.fn((parameters) => {
    // Return a dummy replicated model
    const branchId = parameters[0]?.branchId ?? "defaultBranch";
    return Promise.resolve(
      new SuccessfulReplicatedModelJS(branchId) as unknown as ReplicatedModelJS,
    );
  });

  let client: Ref<ClientJS | null>;
  class MockClientJS {
    startReplicatedModels(
      parameters: ReplicatedModelParameters[],
    ): Promise<ReplicatedModelJS> {
      return startReplicatedModels(parameters);
    }
  }

  beforeEach(() => {
    jest.clearAllMocks();
    client = useModelClient("anURL", () =>
      Promise.resolve(new MockClientJS() as unknown as ClientJS),
    ).client;
  });

  test("if client is undefined", () => {
    useReplicatedModels(undefined, [
      new ReplicatedModelParameters(
        "aRepository",
        "aBranch",
        IdSchemeJS.MODELIX,
      ),
    ]);
    expect(startReplicatedModels).not.toHaveBeenCalled();
  });

  test("if models is undefined", () => {
    useReplicatedModels(client, undefined);
    expect(startReplicatedModels).not.toHaveBeenCalled();
  });

  test("if models switches to another value", async () => {
    const models = ref<ReplicatedModelParameters[] | undefined>([
      new ReplicatedModelParameters(
        "aRepository",
        "aBranch",
        IdSchemeJS.MODELIX,
      ),
    ]);
    useReplicatedModels(client, models);
    expect(startReplicatedModels).toHaveBeenCalled();

    startReplicatedModels.mockClear();
    models.value = [
      new ReplicatedModelParameters(
        "aNewRepository",
        "aNewBranch",
        IdSchemeJS.MODELIX,
      ),
    ];
    await new Promise(process.nextTick);
    expect(startReplicatedModels).toHaveBeenCalled();
  });

  test("if models switches to undefined", async () => {
    const models = ref<ReplicatedModelParameters[] | undefined>([
      new ReplicatedModelParameters(
        "aRepository",
        "aBranch",
        IdSchemeJS.MODELIX,
      ),
    ]);
    useReplicatedModels(client, models);
    expect(startReplicatedModels).toHaveBeenCalled();

    startReplicatedModels.mockClear();
    models.value = undefined;
    await new Promise(process.nextTick);
    // It should not call startReplicatedModels, but it might trigger dispose()
    expect(startReplicatedModels).not.toHaveBeenCalled();
  });
});

test("rootNodes are returned in order and wrapped if readonly", (done) => {
  class MultiRootBranchJS {
    private roots: INodeJS[];
    constructor(params: ReplicatedModelParameters[]) {
      this.roots = params.map((p) => {
        const node = loadModelsFromJson([JSON.stringify({ root: {} })]);
        node.setPropertyValue(toRoleJS("branchId"), p.branchId ?? undefined);
        return node;
      });
    }
    getRootNodes() {
      return this.roots;
    }
    addListener = jest.fn();
    resolveNode = jest.fn();
    removeListener = jest.fn();
    get rootNode() {
      return this.roots[0];
    }
  }

  class MultiRootReplicatedModelJS {
    private branch: BranchJS;
    constructor(params: ReplicatedModelParameters[]) {
      this.branch = new MultiRootBranchJS(params) as unknown as BranchJS;
    }
    getBranch() {
      return this.branch;
    }
    dispose = jest.fn();
  }

  class MultiRootClientJS {
    startReplicatedModels(
      params: ReplicatedModelParameters[],
    ): Promise<ReplicatedModelJS> {
      return Promise.resolve(
        new MultiRootReplicatedModelJS(params) as unknown as ReplicatedModelJS,
      );
    }
  }

  const { client } = useModelClient("url", () =>
    Promise.resolve(new MultiRootClientJS() as unknown as ClientJS),
  );

  const param1 = new ReplicatedModelParameters(
    "repo",
    "branch1",
    IdSchemeJS.MODELIX,
  );
  const param2 = new ReplicatedModelParameters(
    "repo",
    "branch2",
    IdSchemeJS.MODELIX,
    true,
  );
  const param3 = new ReplicatedModelParameters(
    "repo3",
    "branch3",
    IdSchemeJS.MODELIX,
  );
  const { rootNodes } = useReplicatedModels(client, [param1, param2, param3]);

  watchEffect(() => {
    if (rootNodes.value.length === 3) {
      expect(rootNodes.value[0].getPropertyValue(toRoleJS("branchId"))).toBe(
        "branch1",
      );
      expect(rootNodes.value[1].getPropertyValue(toRoleJS("branchId"))).toBe(
        "branch2",
      );
      expect(rootNodes.value[2].getPropertyValue(toRoleJS("branchId"))).toBe(
        "branch3",
      );

      expect(rootNodes.value[0] instanceof ReadOnlyNodeJS).toBe(false);
      expect(rootNodes.value[1] instanceof ReadOnlyNodeJS).toBe(true);
      expect(rootNodes.value[2] instanceof ReadOnlyNodeJS).toBe(false);
      done();
    }
  });
}, 30000);
