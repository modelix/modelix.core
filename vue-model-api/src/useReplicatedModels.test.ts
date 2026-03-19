import { org } from "@modelix/model-client";
import type { INodeJS } from "@modelix/ts-model-api";
import { toRoleJS } from "@modelix/ts-model-api";
import { watchEffect, type Ref, ref } from "vue";
import { useModelClient } from "./useModelClient";
import { useReplicatedModels } from "./useReplicatedModels";
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
          branchId,
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

describe("URL-based client with getToken", () => {
  // Shared mock for startReplicatedModels across all tests in this describe block.
  const startReplicatedModels = jest.fn((parameters: ReplicatedModelParameters[]) => {
    const branchId = parameters[0]?.branchId ?? "defaultBranch";
    return Promise.resolve(
      new SuccessfulReplicatedModelJS(branchId) as unknown as ReplicatedModelJS,
    );
  });

  class MockClientJS {
    startReplicatedModels(
      parameters: ReplicatedModelParameters[],
    ): Promise<ReplicatedModelJS> {
      return startReplicatedModels(parameters);
    }
  }

  let mockClientInstance: MockClientJS;

  // createClient mock — returns the same client instance every call (simulating
  // a shared, reused ClientJS per URL).
  let createClient: jest.Mock;

  beforeEach(() => {
    jest.clearAllMocks();
    mockClientInstance = new MockClientJS();
    createClient = jest.fn((_url: string) =>
      Promise.resolve(mockClientInstance as unknown as ClientJS),
    );
  });

  test("each model param gets its own tokenProvider wrapping getToken", async () => {
    const params = new ReplicatedModelParameters(
      "aRepository",
      "aBranch",
      IdSchemeJS.MODELIX,
    );
    const getToken = jest.fn(() => Promise.resolve("token-for-aBranch"));

    useReplicatedModels("https://model-server/v2", [params], getToken, createClient);

    // Wait for the async client creation and model connection to settle.
    await new Promise(process.nextTick);
    await new Promise(process.nextTick);

    expect(startReplicatedModels).toHaveBeenCalledTimes(1);
    const calledWith = startReplicatedModels.mock.calls[0][0];

    // Each param should have tokenProvider set.
    expect(typeof calledWith[0].tokenProvider).toBe("function");
    expect(calledWith[0].repositoryId).toBe("aRepository");
    expect(calledWith[0].branchId).toBe("aBranch");

    // Invoking tokenProvider should delegate to getToken with the original param.
    await calledWith[0].tokenProvider();
    expect(getToken).toHaveBeenCalledWith(params);
  });

  test("the same ClientJS instance is reused when only models change", async () => {
    const models = ref<ReplicatedModelParameters[]>([
      new ReplicatedModelParameters("aRepository", "aBranch", IdSchemeJS.MODELIX),
    ]);
    const getToken = jest.fn(() => Promise.resolve("a-token"));

    useReplicatedModels("https://model-server/v2", models, getToken, createClient);

    await new Promise(process.nextTick);
    await new Promise(process.nextTick);

    // createClient should have been called exactly once for the URL.
    expect(createClient).toHaveBeenCalledTimes(1);
    expect(startReplicatedModels).toHaveBeenCalledTimes(1);

    // Switch the branch — this should reuse the existing ClientJS.
    models.value = [
      new ReplicatedModelParameters(
        "aRepository",
        "aNewBranch",
        IdSchemeJS.MODELIX,
      ),
    ];

    await new Promise(process.nextTick);
    await new Promise(process.nextTick);

    // Still only one client creation — the existing client is shared.
    expect(createClient).toHaveBeenCalledTimes(1);
    // startReplicatedModels should be called again for the new branch.
    expect(startReplicatedModels).toHaveBeenCalledTimes(2);
    // The second call should carry the new branch params with tokenProvider.
    const secondCallParams = startReplicatedModels.mock.calls[1][0];
    expect(secondCallParams[0].branchId).toBe("aNewBranch");
    expect(typeof secondCallParams[0].tokenProvider).toBe("function");
  });

  test("a new ClientJS is created when the URL changes", async () => {
    const url = ref("https://model-server/v2");
    const getToken = jest.fn(() => Promise.resolve("a-token"));

    useReplicatedModels(
      url,
      [new ReplicatedModelParameters("aRepository", "aBranch", IdSchemeJS.MODELIX)],
      getToken,
      createClient,
    );

    await new Promise(process.nextTick);
    await new Promise(process.nextTick);

    expect(createClient).toHaveBeenCalledTimes(1);
    expect(createClient).toHaveBeenCalledWith("https://model-server/v2");

    url.value = "https://other-server/v2";

    await new Promise(process.nextTick);
    await new Promise(process.nextTick);

    expect(createClient).toHaveBeenCalledTimes(2);
    expect(createClient).toHaveBeenNthCalledWith(2, "https://other-server/v2");
  });
});
