import { INodeJS } from "@modelix/ts-model-api";
import { Cache } from "./Cache";
import { TypedVueNode } from "./TypedVueNode";

// It class has quite the same function as `LanguageRegistry` from ts-model-api, but it differs in three aspects:
// * It prescribes and controls its own memoization and caching mechanisms.
// ** Memoization is needed for the Vue.js bindings to work properly.
// ** The a specific caching for memoization should be used, that does not leak memory.
// * It does not handle adding languages in after construction.
// ** Registering / unregistering languages after some node was wrapped and the result was memoized,
//    would need invalidating the cache, which in turn would break the functionality of the bindings if done naively.
//    Because registering and unregistering after construction are no a use case, this registry is kept simple.
// * The registry has not global instance.
// ** A global instance is not needed.
// ** Relying on global instance complicates testing and reusability.

/**
 * The registry wraps {@link INodeJS} into objects consumable from Vue.js
 *
 * @param wrap The function that wraps an untyped node to a type node usable by Vue.js
 *         The function should be pure
 * @template WrapperT The type of the produced wrapper.
 * @experimental
 */
export class Registry {
  private cache: Cache<TypedVueNode>;

  constructor(
    private wrapper: (wrap: Registry, node: INodeJS) => TypedVueNode,
  ) {
    this.cache = new Cache();
  }

  wrap(node: INodeJS): TypedVueNode {
    return this.cache.memoize(node, () => this.wrapper(this, node));
  }
}
