// The cache could be made independent from INodeJS. Currently this is not needed.
// The cache is a implemenation detail and can be refactored when needed for reuse.
import { INodeJS } from "@modelix/ts-model-api";

/**
 * Cache for memoizing values assoziated with {@link INodeJS}.
 *
 * Memoized value will eventually be garbage collected and removed from the cache,
 * when they are not referenced outside the cache.
 * This is can be implemented by watching for garbage collection of objects with `FinalizationRegistry`.
 *
 * @template CachedT The type of the cached value.
 */
export class Cache<CachedT extends object> {
  private finalizationRegistry: FinalizationRegistry<string>;
  private map: Map<string, WeakRef<CachedT>>;

  constructor() {
    this.map = new Map();
    this.finalizationRegistry = new FinalizationRegistry((key) =>
      this._remove(key),
    );
  }

  /**
   * This method is only public for test purposes.
   */
  public _remove(key: string) {
    const valueRef = this.map.get(key);
    if (valueRef === undefined) {
      // Value is already removed.
      // This is unusual, but might happen.
      // There is no guarantee when the callback of finalizationRegistry is triggered.
      // For example, callbacks for a wrapper with the same key could be triggered out of order.
      return;
    }
    const value = valueRef.deref();
    // The dereferenced value might be not undefined,
    // if in between the garbage collection and the callback
    // a new object for the same key was created and put into the cache.
    if (value === undefined) {
      this.map.delete(key);
    }
  }

  private getKey(node: INodeJS): string {
    return node.getReference() as string;
  }

  private getWithKey(key: string): CachedT | undefined {
    return this.map.get(key)?.deref();
  }

  get(node: INodeJS): CachedT | undefined {
    const key = this.getKey(node);
    return this.getWithKey(key);
  }

  memoize(node: INodeJS, supply: () => CachedT): CachedT {
    const key = this.getKey(node);
    const existingValue = this.getWithKey(key);
    if (existingValue !== undefined) {
      return existingValue;
    }
    const newValue = supply();
    this.finalizationRegistry.register(newValue, key);
    this.map.set(key, new WeakRef(newValue));
    return newValue;
  }
}
