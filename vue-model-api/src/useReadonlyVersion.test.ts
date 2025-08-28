import { org } from "@modelix/model-client";
import { toRoleJS } from "@modelix/ts-model-api";
import { watchEffect, type Ref } from "vue";
import { useModelClient } from "./useModelClient";
import { useReadonlyVersion } from "./useReadonlyVersion";
const { loadModelsFromJson } = org.modelix.model.client2;

type VersionInformationWithModelTree =
  org.modelix.model.client2.VersionInformationWithModelTree;
type MutableModelTreeJs = org.modelix.model.client2.MutableModelTreeJs;
type ClientJS = org.modelix.model.client2.ClientJS;
type VersionInformationJS = org.modelix.model.client2.VersionInformationJS;

class SuccessfulVersionInformationWithModelTree {
  public version: VersionInformationJS;
  public tree: MutableModelTreeJs;

  constructor(versionHash: string) {
    const modelData = {
      root: {},
    };

    const rootNode = loadModelsFromJson([JSON.stringify(modelData)]);
    rootNode.setPropertyValue(toRoleJS("versionHash"), versionHash);
    this.tree = {
      rootNode,
      addListener: jest.fn(),
    } as unknown as MutableModelTreeJs;
    this.version = {
      author: "basti",
      time: new Date(),
      versionHash: "SQnQb*ZS1Vi8Mss2HAu0ENbcpstmqb5E6TuZvpSH1TW4",
    } as unknown as VersionInformationJS;
  }

  addListener = jest.fn();
}

test("test version is loaded", (done) => {
  class SuccessfulClientJS {
    loadReadonlyVersion(
      _repositoryId: string,
      _versionHash: string,
    ): Promise<VersionInformationWithModelTree> {
      return Promise.resolve(
        new SuccessfulVersionInformationWithModelTree(
          _versionHash,
        ) as unknown as VersionInformationWithModelTree,
      );
    }

    dispose = jest.fn();
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new SuccessfulClientJS() as unknown as ClientJS),
  );
  const { rootNode, versionInformation } = useReadonlyVersion(
    client,
    "aRepository",
    "aVersionHash",
  );
  watchEffect(() => {
    if (rootNode.value !== null && versionInformation.value !== null) {
      expect(rootNode.value.getPropertyValue(toRoleJS("versionHash"))).toBe(
        "aVersionHash",
      );
      done();
    }
  });
});

test("test connection error is exposed", (done) => {
  class FailingClientJS {
    loadReadonlyVersion(
      _repositoryId: string,
      _versionHash: string,
    ): Promise<VersionInformationWithModelTree> {
      return Promise.reject("Could not connect.");
    }
  }

  const { client } = useModelClient("anURL", () =>
    Promise.resolve(new FailingClientJS() as unknown as ClientJS),
  );

  const { error } = useReadonlyVersion(client, "aRepository", "aVersionHash");

  watchEffect(() => {
    if (error.value !== null) {
      expect(error.value).toBe("Could not connect.");
      done();
    }
  });
});

describe("does not try loading", () => {
  const loadReadonlyVersion = jest.fn((repositoryId, versionHash) =>
    Promise.resolve(
      new SuccessfulVersionInformationWithModelTree(
        versionHash,
      ) as unknown as VersionInformationWithModelTree,
    ),
  );

  let client: Ref<ClientJS | null>;
  class MockClientJS {
    loadReadonlyVersion(
      _repositoryId: string,
      _versionHash: string,
    ): Promise<VersionInformationWithModelTree> {
      return loadReadonlyVersion(_repositoryId, _versionHash);
    }
  }

  beforeEach(() => {
    jest.clearAllMocks();
    client = useModelClient("anURL", () =>
      Promise.resolve(new MockClientJS() as unknown as ClientJS),
    ).client;
  });

  test("if client is undefined", () => {
    useReadonlyVersion(undefined, "aRepository", "aVersionHash");
    expect(loadReadonlyVersion).not.toHaveBeenCalled();
  });

  test("if repositoryId is undefined", () => {
    useReadonlyVersion(client, undefined, "aVersionHash");
    expect(loadReadonlyVersion).not.toHaveBeenCalled();
  });

  test("if branchId is undefined", () => {
    useReadonlyVersion(client, "aRepository", undefined);
    expect(loadReadonlyVersion).not.toHaveBeenCalled();
  });
});
