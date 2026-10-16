import type { ShallowRef } from "vue";
import { shallowRef, triggerRef } from "vue";

/**
 * A part of the model that can change, for example, whether a node exists.
 *
 * Reading the key with {@link track} in a Vue effect subscribes the effect to it.
 * {@link trigger} re-runs the subscribed effects.
 * It is the Vue counterpart of the dependencies, that `IncrementalBranch` records with `accessed` and `modified`.
 *
 * A key must be strongly reachable as long as an effect depends on it.
 * Vue only keeps the internal dependency of the key alive, not the key itself.
 * A key that is garbage collected can not be triggered anymore,
 * so the effect would not be updated (see MODELIX-1041).
 */
export class DependencyKey {
  private readonly ref: ShallowRef<undefined> = shallowRef(undefined);

  track(): void {
    void this.ref.value;
  }

  trigger(): void {
    triggerRef(this.ref);
  }
}

/**
 * A value read from the model, that only depends on a single key.
 *
 * The value is cached until {@link invalidate} is called and is read again lazily on the next access.
 * Reading the value in a Vue effect subscribes the effect to the key.
 *
 * `read` must not read reactive values,
 * because they are only tracked by the effect that reads the value first.
 * Use `computed` for values that depend on more than one key.
 *
 * The cached value is strongly reachable from this object.
 * When it contains reactive nodes, they stay reachable, as long as the value is valid.
 * Effects that depend on these nodes can therefore still be triggered.
 */
export class TrackedValue<T> {
  private readonly ref: ShallowRef<undefined> = shallowRef(undefined);
  private valid = false;
  private cachedValue: T | undefined = undefined;

  constructor(private readonly read: () => T) {}

  get value(): T {
    void this.ref.value;
    if (!this.valid) {
      this.cachedValue = this.read();
      this.valid = true;
    }
    return this.cachedValue as T;
  }

  invalidate(): void {
    // The outdated value is kept until the value is read again,
    // so that the reactive nodes in it are not garbage collected and created again in between.
    this.valid = false;
    triggerRef(this.ref);
  }
}
