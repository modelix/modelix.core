import { org } from "@modelix/model-client";
import { randomUUID } from "crypto";
import { GenericContainer } from "testcontainers";
import type { StartedTestContainer } from "testcontainers";
import IdSchemeJS = org.modelix.model.client2.IdSchemeJS;

const connectClient = org.modelix.model.client2.connectClient
type ClientJS = org.modelix.model.client2.ClientJS
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS

let client: ClientJS | undefined;
let replicatedModel: ReplicatedModelJS | undefined;
let container: StartedTestContainer;

jest.setTimeout(60000)

beforeAll(async () => {
  container = await new GenericContainer("modelix/model-server:test")
      .withExposedPorts(28101)
      .withCommand(["--inmemory"])
      .start();
})
afterAll(async () => {
  await container.stop();
})

beforeEach(async () => {
  const repositoryId = randomUUID()
  client = await connectClient(`http://localhost:${container.getMappedPort(28101)}/v2`)
  await client.initRepository(repositoryId)
  replicatedModel = await client.startReplicatedModel(repositoryId, "master", IdSchemeJS.READONLY)
})

afterEach(() => {
  client?.dispose()
  replicatedModel?.dispose()
})


test("replicated model uses user set in client", async () => {
  const userId = "aAuthor"
  client!.setClientProvidedUserId(userId)
  const rootNode = replicatedModel!.getBranch().rootNode

  rootNode.setPropertyValue("aProperty", "aValue")

  const versionInformation = await replicatedModel!.getCurrentVersionInformation()
  const lastAuthor = versionInformation.author
  expect(lastAuthor).toBe(userId);
}, );

test("replicated model returns user set in client", async () => {
  const versionInformation = await replicatedModel!.getCurrentVersionInformation()
  const lastTimestamp = versionInformation.time
  expect(lastTimestamp).toBeTruthy()
});
