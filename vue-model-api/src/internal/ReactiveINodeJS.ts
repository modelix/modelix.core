import { IConceptJS, INodeJS, ITypedNode } from "@modelix/ts-model-api";
import { customRef, markRaw } from "vue";
import { Cache } from "./Cache";

export function toReactiveINodeJS(
  node: INodeJS,
  cache: Cache<ReactiveINodeJS>,
): ReactiveINodeJS {
  return cache.memoize(node, () => new ReactiveINodeJS(node, cache));
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
  maybeReactive: INodeJS | null | undefined,
): INodeJS | null | undefined {
  // `undefined instanceof ReactiveINodeJS` and
  // `null instanceof ReactiveINodeJS` evaluates `false`
  if (maybeReactive instanceof ReactiveINodeJS) {
    return maybeReactive.unreactiveNode;
  } else {
    return maybeReactive;
  }
}

// `any` is currectly provided as the type for references from `@modelix/ts-model-api`,
// so we have to work with it for now.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type INodeReferenceJS = any;

interface TrackAndTrigger {
  track: () => void;
  trigger: () => void;
}

export class ReactiveINodeJS implements INodeJS {
  private byRoleTrackAndTrigger: Map<string | undefined, TrackAndTrigger> =
    new Map();
  private trackAndTriggerForAllChildren: TrackAndTrigger | undefined;

  constructor(
    public readonly unreactiveNode: INodeJS,
    private readonly cache: Cache<ReactiveINodeJS>,
  ) {
    // markRaw ensures, that Vue.js does not wrap this object again in a proxy
    // see. https://vuejs.org/api/reactivity-advanced.html#markraw
    markRaw(this);
  }

  getConcept(): IConceptJS | undefined {
    return this.unreactiveNode.getConcept();
  }

  getConceptUID(): string | undefined {
    return this.unreactiveNode.getConceptUID();
  }

  getReference(): INodeReferenceJS {
    return this.unreactiveNode.getReference();
  }

  getRoleInParent(): string | undefined {
    // The role of the own node does not need to be reactive,
    // because the node could have only been obtained by accessing it from its parent.
    // Whenever the role in the parent or the parent changes,
    // the reactivity in the parent triggers again.
    return this.unreactiveNode.getRoleInParent();
  }

  getParent(): INodeJS | undefined {
    // This does not need to be made reacitve for the same reason as getRoleInParent
    const unreacitveNode = this.unreactiveNode.getParent();
    return unreacitveNode
      ? toReactiveINodeJS(unreacitveNode, this.cache)
      : unreacitveNode;
  }

  remove(): void {
    this.unreactiveNode.remove();
  }

  wrap(): ITypedNode {
    return this.unreactiveNode.wrap();
  }

  getChildren(role: string | undefined): INodeJS[] {
    const { track } = this.getOrCreateTrackAndTriggerForRole(role);
    track();
    return this.unreactiveNode
      .getChildren(role)
      .map((node) => toReactiveINodeJS(node, this.cache));
  }

  getAllChildren(): INodeJS[] {
    const { track } = this.getOrCreateTrackAndTriggerForAllChildren();
    track();
    return this.unreactiveNode
      .getAllChildren()
      .map((node) => toReactiveINodeJS(node, this.cache));
  }

  moveChild(role: string | undefined, index: number, child: INodeJS): void {
    this.unreactiveNode.moveChild(role, index, unwrapReactiveINodeJS(child));
  }

  addNewChild(
    role: string | undefined,
    index: number,
    concept: IConceptJS | undefined,
  ): INodeJS {
    const unreacitveNode = this.unreactiveNode.addNewChild(
      role,
      index,
      concept,
    );
    return unreacitveNode
      ? toReactiveINodeJS(unreacitveNode, this.cache)
      : unreacitveNode;
  }

  removeChild(child: INodeJS): void {
    return this.unreactiveNode.removeChild(unwrapReactiveINodeJS(child));
  }

  getReferenceRoles(): string[] {
    // We do not make the getPropertyRoles reactive,
    // because for a typed reactive API they are not relevant.
    return this.unreactiveNode.getReferenceRoles();
  }

  getReferenceTargetNode(role: string): INodeJS | undefined {
    const { track } = this.getOrCreateTrackAndTriggerForRole(role);
    track();
    const unreacitveNode = this.unreactiveNode.getReferenceTargetNode(role);
    return unreacitveNode
      ? toReactiveINodeJS(unreacitveNode, this.cache)
      : unreacitveNode;
  }

  getReferenceTargetRef(role: string): INodeReferenceJS | undefined {
    const { track } = this.getOrCreateTrackAndTriggerForRole(role);
    track();
    return this.unreactiveNode.getReferenceTargetRef(role);
  }

  setReferenceTargetNode(role: string, target: INodeJS | undefined): void {
    // Do not call `this.unreactiveNode.setReferenceTargetNode` directly,
    // because the target is declared as `INodeJS`, but actuall an `NodeAdapterJS` is expected.
    // Just using the reference is cleaner then unwrapping
    // then checking for ReactiveINodeJS and eventuall unwrapping it
    return this.unreactiveNode.setReferenceTargetNode(
      role,
      unwrapReactiveINodeJS(target),
    );
  }

  setReferenceTargetRef(
    role: string,
    target: INodeReferenceJS | undefined,
  ): void {
    this.unreactiveNode.setReferenceTargetRef(role, target);
  }

  getPropertyRoles(): string[] {
    // We do not make the getPropertyRoles reactive,
    // because for a typed reactive API they are not relevant.
    return this.unreactiveNode.getPropertyRoles();
  }

  getPropertyValue(role: string): string | undefined {
    const { track } = this.getOrCreateTrackAndTriggerForRole(role);
    track();
    return this.unreactiveNode.getPropertyValue(role);
  }

  setPropertyValue(role: string, value: string | undefined): void {
    this.unreactiveNode.setPropertyValue(role, value);
  }

  private getOrCreateTrackAndTriggerForAllChildren(): TrackAndTrigger {
    if (this.trackAndTriggerForAllChildren === undefined) {
      customRef((track, trigger) => {
        this.trackAndTriggerForAllChildren = {
          track,
          trigger,
        };
        return {
          // Getter and setter ar empty for the same reason as in `getOrCreateTrackAndTriggerForRole`
          get() {},
          set() {},
        };
      });
    }
    // `this.trackAndTriggerForAllChildren` will always be set, because the `factory`
    // argument of `customRef` will be evaluated immedialty.
    return this.trackAndTriggerForAllChildren!;
  }

  private getOrCreateTrackAndTriggerForRole(
    role: string | undefined,
  ): TrackAndTrigger {
    const existing = this.byRoleTrackAndTrigger.get(role);
    if (existing !== undefined) {
      return existing;
    }
    let created;
    customRef((track, trigger) => {
      created = {
        track,
        trigger,
      };
      this.byRoleTrackAndTrigger.set(role, created);
      return {
        // The getters and setters will never be called directly
        // and therefore the are empty.
        // We use `customRef` to get access to a pair of `trigger` and `track`
        // to call them directly from outside.
        get() {},
        set() {},
      };
    });
    // `created` will always be set, because the `factory`
    // argument of `customRef` will be evaluated immedialty.
    return created!;
  }

  triggerChangeInRole(role: string | undefined | null) {
    const normalizedRole =
      role !== undefined && role !== null ? role : undefined;
    this.byRoleTrackAndTrigger.get(normalizedRole)?.trigger();
  }

  triggerChangeInAllChildren() {
    this.trackAndTriggerForAllChildren?.trigger();
  }
}
