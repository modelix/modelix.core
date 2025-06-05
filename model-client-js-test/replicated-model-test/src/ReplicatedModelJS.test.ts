import { org } from "@modelix/model-client";
import { randomUUID } from "crypto";
import { GenericContainer } from "testcontainers";
import type { StartedTestContainer } from "testcontainers";
import IdSchemeJS = org.modelix.model.client2.IdSchemeJS;

const connectClient = org.modelix.model.client2.connectClient;
type ClientJS = org.modelix.model.client2.ClientJS;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;
type VersionInformationJS = org.modelix.model.client2.VersionInformationJS;

let client: ClientJS | undefined;
let replicatedModel: ReplicatedModelJS | undefined;
let container: StartedTestContainer;

jest.setTimeout(60000);

beforeAll(async () => {
  container = await new GenericContainer("modelix/model-server:test")
    .withExposedPorts(28101)
    .withCommand(["--inmemory"])
    .start();
});
afterAll(async () => {
  await container.stop();
});

beforeEach(async () => {
  const repositoryId = randomUUID();
  client = await connectClient(
    `http://localhost:${container.getMappedPort(28101)}/v2`,
  );
  await client.initRepository(repositoryId);
  replicatedModel = await client.startReplicatedModel(
    repositoryId,
    "master",
    IdSchemeJS.READONLY,
  );
});

afterEach(() => {
  client?.dispose();
  replicatedModel?.dispose();
});

describe("a new merged version", () => {
  const userId = "aAuthor";
  let versionInformation: VersionInformationJS;
  beforeEach(async () => {
    client!.setClientProvidedUserId(userId);
    const rootNode = replicatedModel!.getBranch().rootNode;

    rootNode.setPropertyValue("aProperty", "aValue");

    versionInformation = await replicatedModel!.getCurrentVersionInformation();
  });

  test("uses user set in client", async () => {
    expect(versionInformation.author).toBe(userId);
  });

  test("has the current time", async () => {
    expect(versionInformation.time).toBeTruthy();
  });
});

test("replicated model returns time set in client", async () => {
  const versionInformation =
    await replicatedModel!.getCurrentVersionInformation();
  const lastTimestamp = versionInformation.time;
  expect(lastTimestamp).toBeTruthy();
});
