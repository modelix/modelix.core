import { runGarbageCollection } from "./runGarbageCollection";

test("test garbage collection invocation", async () => {
  const weakRef = new WeakRef({});
  await runGarbageCollection();
  expect(weakRef.deref()).toBeUndefined();
});
