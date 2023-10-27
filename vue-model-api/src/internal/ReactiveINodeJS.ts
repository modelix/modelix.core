import { INodeJS, startVuejsIntegration } from "@modelix/model-client";
import { customRef, markRaw } from "vue";
import { Cache } from "./Cache";

startVuejsIntegration(
  () => {
    let trackAndTrigger: TrackAndTrigger;
    customRef((track, trigger) => {
      trackAndTrigger = {
        track,
        trigger,
      };
      return {
        // The getters and setters will never be called directly
        // and therefore they are empty.
        // We use `customRef` to get access to a pair of `trigger` and `track`
        // to call them directly from outside.
        get() {},
        set() {},
      };
    });
    return trackAndTrigger!;
  },
  (ref: TrackAndTrigger) => ref.track(),
  (ref: TrackAndTrigger) => ref.trigger(),
);

export function toReactiveINodeJS(
  node: INodeJS,
  cache: Cache<INodeJS>,
): INodeJS {
  // while markRaw(node) only disables proxies for a single object, the following line disables it for all instances
  if (!Object.getPrototypeOf(node)["__v_skip"])
    Object.getPrototypeOf(node)["__v_skip"] = true;
  return node;
}

// This declaration specifies the types of the implemenation in `unwrapReactiveINodeJS` further.
// It declares, that when
// * an `INodeJS` is given an `INodeJS` is returned
// * an `undefined` is given an `undefined` is returned
// * an `null` is given an `null` is returned
// This so called conditional types help avoid unneed assertsion about types on the usage site.
// See. https://www.typescriptlang.org/docs/handbook/2/conditional-types.html
function unwrapReactiveINodeJS<T extends INodeJS | null | undefined>(
  maybeReactive: T,
): T extends INodeJS ? INodeJS : T extends null ? null : undefined;

function unwrapReactiveINodeJS(
  node: INodeJS | null | undefined,
): INodeJS | null | undefined {
  return node;
}

interface TrackAndTrigger {
  track: () => void;
  trigger: () => void;
}
