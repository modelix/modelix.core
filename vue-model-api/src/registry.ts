import { INodeJS } from "@modelix/ts-model-api";
import { Cache } from "./cache";

/**
 * The registry takes care of wrapping {@link INodeJS} into objects consumable from Vue.js
 *
 * @template WrapperT The type of the produced wrapper.
 * @experimental
 */
// TODO Olekz limit WrapperT to vue model objects
// TODO Olekz Comment,that wrap must be pure.
// It has quite the same function as `LanguageRegistry` from ts-model-api, but it differs in three aspects:
//
// * It prescribes and controls its own memoization and caching mechanisms.
// ** Memoization is needed for the bindings to work properly.
// ** The a specific caching for memoization should be used, that does not leak memory.
// * It does not handle adding languages in after construction.
// ** Registering / unregistering languages after some node was wrapped and the result was memoized,
//    would need invalidating the cache, which in turn would break the functionality of the bindings if done naively.
//    Because registering and unregistering after construction are no a use case, this registry is kept simple.
// * The registry has not global instance.
// ** A global instance is not needed.
// ** Relying on global instance complicates testing and reusibilty.
export class Registry<WrapperT extends object> {
  private cache: Cache<WrapperT>;

  constructor(
    private wrapper: (wrap: Registry<WrapperT>, node: INodeJS) => WrapperT,
  ) {
    this.cache = new Cache();
  }

  wrap(node: INodeJS): WrapperT {
    return this.cache.memoize(node, () => this.wrapper(this, node));
  }
}
