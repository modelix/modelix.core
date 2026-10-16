import type {
  IConceptJS,
  INodeJS,
  ChildRole,
  ReferenceRole,
  PropertyRole,
} from "@modelix/ts-model-api";
import type { ComputedRef } from "vue";
import { computed, effectScope, markRaw } from "vue";
import type { Nullable } from "@modelix/model-client";
import { org } from "@modelix/model-client";
import type { DependencyKey } from "./DependencyKey";
import { TrackedValue } from "./DependencyKey";
import type { ReactiveModel } from "./ReactiveModel";

// This declaration specifies the types of the implementation in `unwrapReactiveINodeJS` further.
// It declares, that when
// * an `INodeJS` is given an `INodeJS` is returned
// * an `undefined` is given an `undefined` is returned
// * an `null` is given an `null` is returned
// This so called conditional types help avoid unneeded assertions about types on the usage site.
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

// `any` is correctly provided as the type for references from `@modelix/ts-model-api`,
// so we have to work with it for now.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type INodeReferenceJS = any;

interface ReferenceTargetNode {
  value: ComputedRef<INodeJS | undefined>;
  // The keys for the existence of the target, which `value` depends on.
  // They are kept here, so that they stay reachable as long as `value`.
  existenceKeys: DependencyKey[];
}

/**
 * A node that can be read in Vue effects, like a reactive object.
 *
 * Every read value is cached in a {@link TrackedValue} until the part of the model it was read from changes.
 * The {@link ReactiveModel} invalidates the values when it handles a change.
 */
export class ReactiveINodeJS implements INodeJS {
  private readonly properties =
    new org.modelix.model.api.MapWithPropertyRoleKey<
      TrackedValue<string | undefined>
    >();
  private readonly referenceTargetRefs =
    new org.modelix.model.api.MapWithReferenceRoleKey<
      TrackedValue<INodeReferenceJS | undefined>
    >();
  private readonly referenceTargetNodes =
    new org.modelix.model.api.MapWithReferenceRoleKey<ReferenceTargetNode>();
  private readonly children = new org.modelix.model.api.MapWithChildRoleKey<
    TrackedValue<INodeJS[]>
  >();
  private allChildren: TrackedValue<INodeJS[]> | undefined;
  private propertyRoles: TrackedValue<PropertyRole[]> | undefined;
  private referenceRoles: TrackedValue<ReferenceRole[]> | undefined;
  private parent: TrackedValue<INodeJS | undefined> | undefined;
  private roleInParent: TrackedValue<ChildRole | undefined> | undefined;
  private concept: TrackedValue<IConceptJS | undefined> | undefined;
  private conceptUID: TrackedValue<string | undefined> | undefined;
  // A removed node cannot be read from the model anymore.
  // Its cached values are kept, because they are not invalidated while it is removed.
  private removed = false;

  constructor(
    public readonly unreactiveNode: INodeJS,
    private readonly model: ReactiveModel,
  ) {
    // markRaw ensures, that Vue.js does not wrap this object again in a proxy
    // see. https://vuejs.org/api/reactivity-advanced.html#markraw
    markRaw(this);
  }

  private wrap(node: INodeJS): ReactiveINodeJS;
  private wrap(node: INodeJS | undefined): ReactiveINodeJS | undefined;
  private wrap(node: INodeJS | undefined): ReactiveINodeJS | undefined {
    // Keeps `null`, because the unreactive node returns `null` where `undefined` is declared.
    // See https://issues.modelix.org/issue/MODELIX-567/
    return node ? this.model.wrap(node) : node;
  }

  getConcept(): IConceptJS | undefined {
    this.concept ??= new TrackedValue(() => this.unreactiveNode.getConcept());
    return this.concept.value;
  }

  getConceptUID(): string | undefined {
    this.conceptUID ??= new TrackedValue(() =>
      this.unreactiveNode.getConceptUID(),
    );
    return this.conceptUID.value;
  }

  getReference(): INodeReferenceJS {
    // The reference of a node never changes.
    return this.unreactiveNode.getReference();
  }

  getRoleInParent(): ChildRole | undefined {
    this.roleInParent ??= new TrackedValue(() =>
      this.unreactiveNode.getRoleInParent(),
    );
    return this.roleInParent.value;
  }

  getParent(): INodeJS | undefined {
    this.parent ??= new TrackedValue(() =>
      this.wrap(this.unreactiveNode.getParent()),
    );
    return this.parent.value;
  }

  remove(): void {
    this.unreactiveNode.remove();
  }

  getChildren(role: ChildRole | undefined): INodeJS[] {
    return this.getOrCreate(
      this.children,
      role,
      () =>
        new TrackedValue(() =>
          this.unreactiveNode
            .getChildren(role)
            .map((node) => this.model.wrap(node)),
        ),
    ).value;
  }

  getAllChildren(): INodeJS[] {
    this.allChildren ??= new TrackedValue(() =>
      this.unreactiveNode.getAllChildren().map((node) => this.model.wrap(node)),
    );
    return this.allChildren.value;
  }

  moveChild(role: ChildRole | undefined, index: number, child: INodeJS): void {
    this.unreactiveNode.moveChild(role, index, unwrapReactiveINodeJS(child));
  }

  addNewChild(
    role: ChildRole | undefined,
    index: number,
    concept: IConceptJS | undefined,
  ): INodeJS {
    // The cached values do not need to be invalidated here.
    // They will be invalidated through the change listener of the branch.
    const unreactiveNode = this.unreactiveNode.addNewChild(
      role,
      index,
      concept,
    );
    return this.model.wrap(unreactiveNode);
  }

  removeChild(child: INodeJS): void {
    return this.unreactiveNode.removeChild(unwrapReactiveINodeJS(child));
  }

  getReferenceRoles(): ReferenceRole[] {
    this.referenceRoles ??= new TrackedValue(() =>
      this.unreactiveNode.getReferenceRoles(),
    );
    return this.referenceRoles.value;
  }

  getReferenceTargetNode(role: ReferenceRole): INodeJS | undefined {
    return this.getOrCreate(this.referenceTargetNodes, role, () => {
      const targetNode: ReferenceTargetNode = {
        // The `computed` is shared by every component that reads this reference.
        // Before Vue 3.5, a `computed` belongs to the effect scope it is created in,
        // for example, the component whose `setup()` reads the reference first.
        // The component would stop it on unmount, and it would never be updated again.
        // A detached scope keeps it independent of the component.
        value: effectScope(true).run(() =>
          computed(() => this.readReferenceTargetNode(role, targetNode)),
        )!,
        existenceKeys: [],
      };
      return targetNode;
    }).value.value;
  }

  // The target node depends on the reference and on whether the target exists.
  // A removed or added target does not change the reference,
  // so its existence is a key of its own, like the `UnclassifiedNodeDependency` in `IncrementalBranch`.
  // This also covers a target that does not exist yet when the reference is read.
  private readReferenceTargetNode(
    role: ReferenceRole,
    targetNode: ReferenceTargetNode,
  ): INodeJS | undefined {
    const targetRef = this.getReferenceTargetRef(role);
    // The value is read again while this node is removed, when its target is removed or added.
    // For example, when this node and its target are removed together.
    // A reference of a removed node is not resolved.
    // Unset references are `null`, even though the declared type says `undefined`.
    // See https://issues.modelix.org/issue/MODELIX-567/
    const unreactiveTargetNode = this.removed
      ? (null as unknown as undefined)
      : this.unreactiveNode.getReferenceTargetNode(role);
    const existenceKeys: DependencyKey[] = [];
    if (targetRef) {
      existenceKeys.push(this.model.existenceKey(targetRef));
    }
    // The stored reference might be serialized differently than the reference of the resolved node.
    // Changes are reported with the reference of the node.
    const resolvedRef = unreactiveTargetNode?.getReference();
    if (resolvedRef && resolvedRef !== targetRef) {
      existenceKeys.push(this.model.existenceKey(resolvedRef));
    }
    existenceKeys.forEach((key) => key.track());
    targetNode.existenceKeys = existenceKeys;
    return this.wrap(unreactiveTargetNode);
  }

  getReferenceTargetRef(role: ReferenceRole): INodeReferenceJS | undefined {
    return this.getOrCreate(
      this.referenceTargetRefs,
      role,
      () =>
        new TrackedValue(() => this.unreactiveNode.getReferenceTargetRef(role)),
    ).value;
  }

  setReferenceTargetNode(
    role: ReferenceRole,
    target: INodeJS | undefined,
  ): void {
    // The cached values do not need to be invalidated here.
    // They will be invalidated through the change listener of the branch.
    return this.unreactiveNode.setReferenceTargetNode(
      role,
      unwrapReactiveINodeJS(target),
    );
  }

  setReferenceTargetRef(
    role: ReferenceRole,
    target: INodeReferenceJS | undefined,
  ): void {
    // The cached values do not need to be invalidated here.
    // They will be invalidated through the change listener of the branch.
    this.unreactiveNode.setReferenceTargetRef(role, target);
  }

  getPropertyRoles(): PropertyRole[] {
    this.propertyRoles ??= new TrackedValue(() =>
      this.unreactiveNode.getPropertyRoles(),
    );
    return this.propertyRoles.value;
  }

  getPropertyValue(role: PropertyRole): string | undefined {
    return this.getOrCreate(
      this.properties,
      role,
      () => new TrackedValue(() => this.unreactiveNode.getPropertyValue(role)),
    ).value;
  }

  setPropertyValue(role: PropertyRole, value: string | undefined): void {
    // The cached values do not need to be invalidated here.
    // They will be invalidated through the change listener of the branch.
    this.unreactiveNode.setPropertyValue(role, value);
  }

  private getOrCreate<ValueT extends object, RoleT>(
    byRole: org.modelix.model.api.MapWithRoleKey<ValueT>,
    role: RoleT,
    create: () => ValueT,
  ): ValueT {
    return byRole.getOrPut((role as unknown as string) ?? "null", create);
  }

  private getExisting<ValueT extends object>(
    byRole: org.modelix.model.api.MapWithRoleKey<ValueT>,
    role: unknown,
  ): ValueT | undefined {
    return byRole.get(role as string) ?? undefined;
  }

  setRemoved(removed: boolean) {
    this.removed = removed;
  }

  invalidateProperty(role: PropertyRole) {
    this.getExisting(this.properties, role)?.invalidate();
    this.propertyRoles?.invalidate();
  }

  invalidateReference(role: ReferenceRole) {
    // The target node is a `computed` that depends on the target reference,
    // so it is invalidated with it.
    this.getExisting(this.referenceTargetRefs, role)?.invalidate();
    this.referenceRoles?.invalidate();
  }

  invalidateChildren(role: Nullable<ChildRole>) {
    this.getExisting(this.children, role)?.invalidate();
    this.allChildren?.invalidate();
  }

  invalidateContainment() {
    this.parent?.invalidate();
    this.roleInParent?.invalidate();
  }

  invalidateConcept() {
    this.concept?.invalidate();
    this.conceptUID?.invalidate();
  }

  /**
   * Invalidates every cached value of this node.
   * A node that is added again after it was removed can differ from the removed node,
   * for example, when a model is deleted and imported again with the same node IDs.
   */
  invalidateAll() {
    this.invalidateContainment();
    this.invalidateConcept();
    this.propertyRoles?.invalidate();
    this.referenceRoles?.invalidate();
    this.allChildren?.invalidate();
    this.properties.values().forEach((value) => value.invalidate());
    this.referenceTargetRefs.values().forEach((value) => value.invalidate());
    this.children.values().forEach((value) => value.invalidate());
  }

  get node() {
    return this.unreactiveNode;
  }
}
