import { INodeJS } from "@modelix/ts-model-api";
import { Cache } from "./cache"

describe('test cache', () => {
    test('wrapper is added to cache', () => {
      const cache = new Cache<{id: string}>()
      const node = {
        getReference: () => "aReferenceId"
      } as INodeJS
      const wrap1 = () => ({
        id: "aWrapper1"
      })
      const wrap2 = () => ({
        id: "aWrapper2"
      })
      const wrapped1 = cache.memoize(node, wrap1)
      expect(wrapped1.id).toBe("aWrapper1")
      const wrapped2 = cache.memoize(node, wrap2)
      expect(wrapped2.id).toBe("aWrapper1")
    });
    // TODO Olekz try to test GC behaviour somehow
  });
