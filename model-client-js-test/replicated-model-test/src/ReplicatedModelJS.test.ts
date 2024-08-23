import { org } from "@modelix/model-client";
import { randomUUID } from "crypto";

const MODEL_SERVER_URL = "http://localhost:28103/v2";

const connectClient = org.modelix.model.client2.connectClient;
type ClientJS = org.modelix.model.client2.ClientJS;
type ReplicatedModelJS = org.modelix.model.client2.ReplicatedModelJS;

let client: ClientJS | undefined;
let replicatedModel: ReplicatedModelJS | undefined;

beforeEach(async () => {
  const repositoryId = randomUUID();
  client = await connectClient(MODEL_SERVER_URL);
  await client.initRepository(repositoryId);
  replicatedModel = await client.startReplicatedModel(repositoryId, "master");
});

afterEach(() => {
  client?.dispose();
  replicatedModel?.dispose();
});

test("replicated model uses user set in client", async () => {
  const userId = "aAuthor";
  client!.setClientProvidedUserId(userId);
  const rootNode = replicatedModel!.getBranch().rootNode;

  rootNode.setPropertyValue("aProperty", "aValue");

  const versionInformation =
    await replicatedModel!.getCurrentVersionInformation();
  const lastAuthor = versionInformation.author;
  expect(lastAuthor).toBe(userId);
});

test("replicated model returns user set in client", async () => {
  const versionInformation =
    await replicatedModel!.getCurrentVersionInformation();
  const lastTimestamp = versionInformation.time;
  expect(lastTimestamp).toBeTruthy();
});
