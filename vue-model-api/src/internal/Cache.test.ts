import type { INodeJS } from "@modelix/ts-model-api";
import { Cache } from "./Cache";
import { runGarbageCollection } from "./runGarbageCollection";

test("wrapper is added to cache", () => {
  const cache = new Cache<{ id: string }>();
  const node = {
    getReference: () => "aReferenceId",
  } as INodeJS;
  const wrap1 = () => ({
    id: "aWrapper1",
  });
  const wrap2 = () => ({
    id: "aWrapper2",
  });
  const wrapped1 = cache.memoize(node, wrap1);
  expect(wrapped1.id).toBe("aWrapper1");
  const wrapped2 = cache.memoize(node, wrap2);
  expect(wrapped2.id).toBe("aWrapper1");
});

test("wrapper is removed from cache after garbage collection", async () => {
  const iNode = {
    getReference() {
      return "myKey";
    },
  } as INodeJS;
  const cache = new Cache();
  cache.memoize(iNode, () => ({}));
  expect(cache.get(iNode)).not.toBeUndefined();
  await runGarbageCollection();
  expect(cache.get(iNode)).toBeUndefined();
});
