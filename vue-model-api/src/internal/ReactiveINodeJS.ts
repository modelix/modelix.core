import type {
  IConceptJS,
  INodeJS,
  ChildRole,
  ReferenceRole,
  PropertyRole,
} from "@modelix/ts-model-api";
import type { Ref } from "vue";
import { markRaw, shallowRef } from "vue";
import type { Cache } from "./Cache";
import type { Nullable } from "@modelix/model-client";
import { org } from "@modelix/model-client";

export function toReactiveINodeJS(
  node: INodeJS,
  cache: Cache<ReactiveINodeJS>,
): ReactiveINodeJS {
  return cache.memoize(node, () => new ReactiveINodeJS(node, cache));
}

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

export class ReactiveINodeJS implements INodeJS {
  private byRoleRefToProperty =
    new org.modelix.model.api.MapWithPropertyRoleKey<Ref<string | undefined>>();
  private byRoleRefToReferenceTargetNode =
    new org.modelix.model.api.MapWithReferenceRoleKey<
      Ref<INodeJS | undefined>
    >();
  private byRoleRefToReferenceTargetRef =
    new org.modelix.model.api.MapWithReferenceRoleKey<
      Ref<INodeReferenceJS | undefined>
    >();
  private byRoleRefToChildren = new org.modelix.model.api.MapWithChildRoleKey<
    Ref<INodeJS[]>
  >();
  private refForAllChildren: Ref<INodeJS[]> | undefined = undefined;

  constructor(
    public readonly unreactiveNode: INodeJS,
    private readonly cache: Cache<ReactiveINodeJS>,
  ) {
    // markRaw ensures, that Vue.js does not wrap this object again in a proxy
    // see. https://vuejs.org/api/reactivity-advanced.html#markraw
    markRaw(this);
  }

  private propertyGetter = (role: PropertyRole) =>
    this.unreactiveNode.getPropertyValue(role);

  private referenceTargetRefGetter = (role: ReferenceRole) =>
    this.unreactiveNode.getReferenceTargetRef(role);

  private referenceTargetNodeGetter = (role: ReferenceRole) => {
    const unreactiveTargetNode =
      this.unreactiveNode.getReferenceTargetNode(role);
    return unreactiveTargetNode
      ? toReactiveINodeJS(unreactiveTargetNode, this.cache)
      : unreactiveTargetNode;
  };

  private childrenGetter = (role: ChildRole | undefined) =>
    this.unreactiveNode
      .getChildren(role)
      .map((node) => toReactiveINodeJS(node, this.cache));

  private allChildrenGetter = () =>
    this.unreactiveNode
      .getAllChildren()
      .map((node) => toReactiveINodeJS(node, this.cache));

  getConcept(): IConceptJS | undefined {
    return this.unreactiveNode.getConcept();
  }

  getConceptUID(): string | undefined {
    return this.unreactiveNode.getConceptUID();
  }

  getReference(): INodeReferenceJS {
    return this.unreactiveNode.getReference();
  }

  getRoleInParent(): ChildRole | undefined {
    // The role of the own node does not need to be reactive,
    // because the node could have only been obtained by accessing it from its parent.
    // Whenever the role in the parent or the parent changes,
    // the reactivity in the parent triggers again.
    return this.unreactiveNode.getRoleInParent();
  }

  getParent(): INodeJS | undefined {
    // This does not need to be made reacitve for the same reason as getRoleInParent
    const unreactiveNode = this.unreactiveNode.getParent();
    return unreactiveNode
      ? toReactiveINodeJS(unreactiveNode, this.cache)
      : unreactiveNode;
  }

  remove(): void {
    this.unreactiveNode.remove();
  }

  getChildren(role: ChildRole | undefined): INodeJS[] {
    const ref = this.getOrCreateRefForRole(
      this.byRoleRefToChildren,
      role,
      this.childrenGetter,
    );
    return ref.value;
  }

  getAllChildren(): INodeJS[] {
    if (this.refForAllChildren == undefined) {
      this.refForAllChildren = shallowRef(this.allChildrenGetter());
    }
    return this.refForAllChildren!.value;
  }

  moveChild(role: ChildRole | undefined, index: number, child: INodeJS): void {
    this.unreactiveNode.moveChild(role, index, unwrapReactiveINodeJS(child));
  }

  addNewChild(
    role: ChildRole | undefined,
    index: number,
    concept: IConceptJS | undefined,
  ): INodeJS {
    // The related Vue-`Ref` does not need to be triggered.
    // It will be updated through the changed listener of the branch.
    const unreactiveNode = this.unreactiveNode.addNewChild(
      role,
      index,
      concept,
    );
    return toReactiveINodeJS(unreactiveNode, this.cache);
  }

  removeChild(child: INodeJS): void {
    return this.unreactiveNode.removeChild(unwrapReactiveINodeJS(child));
  }

  getReferenceRoles(): ReferenceRole[] {
    // We do not make the getPropertyRoles reactive,
    // because for a typed reactive API they are not relevant.
    return this.unreactiveNode.getReferenceRoles();
  }

  getReferenceTargetNode(role: ReferenceRole): INodeJS | undefined {
    const ref = this.getOrCreateRefForRole(
      this.byRoleRefToReferenceTargetNode,
      role,
      this.referenceTargetNodeGetter,
    );
    return ref.value;
  }

  getReferenceTargetRef(role: ReferenceRole): INodeReferenceJS | undefined {
    const ref = this.getOrCreateRefForRole(
      this.byRoleRefToReferenceTargetRef,
      role,
      this.referenceTargetRefGetter,
    );
    return ref.value;
  }

  setReferenceTargetNode(
    role: ReferenceRole,
    target: INodeJS | undefined,
  ): void {
    // The related Vue-`Ref` does not need to be triggered.
    // It will be updated through the changed listener of the branch.
    return this.unreactiveNode.setReferenceTargetNode(
      role,
      unwrapReactiveINodeJS(target),
    );
  }

  setReferenceTargetRef(
    role: ReferenceRole,
    target: INodeReferenceJS | undefined,
  ): void {
    // The related Vue-`Ref` does not need to be triggered.
    // It will be updated through the changed listener of the branch.
    this.unreactiveNode.setReferenceTargetRef(role, target);
  }

  getPropertyRoles(): PropertyRole[] {
    // We do not make the getPropertyRoles reactive,
    // because for a typed reactive API they are not relevant.
    return this.unreactiveNode.getPropertyRoles();
  }

  getPropertyValue(role: PropertyRole): string | undefined {
    const ref = this.getOrCreateRefForRole(
      this.byRoleRefToProperty,
      role,
      this.propertyGetter,
    );
    return ref.value;
  }

  setPropertyValue(role: PropertyRole, value: string | undefined): void {
    // The related Vue-`Ref` does not need to be triggered.
    // It will be updated through the changed listener of the branch.
    this.unreactiveNode.setPropertyValue(role, value);
  }

  getOrCreateRefForRole<ValueT, RoleT>(
    byRoleRefs: org.modelix.model.api.MapWithRoleKey<Ref<ValueT>>,
    role: RoleT,
    getValue: (role: RoleT) => ValueT,
  ): Ref<ValueT> {
    return byRoleRefs.getOrPut((role as unknown as string) ?? "null", () =>
      shallowRef(getValue(role)),
    );
  }

  triggerChangeInChild(role: Nullable<ChildRole>) {
    if (this.refForAllChildren) {
      this.refForAllChildren.value = this.allChildrenGetter();
    }

    const normalizedRole = role ?? undefined;
    const maybeRef = this.byRoleRefToChildren.get(role as unknown as string);
    if (maybeRef) {
      maybeRef.value = this.childrenGetter(normalizedRole);
    }
  }

  triggerChangeInReference(role: ReferenceRole) {
    const maybeTargetNodeRef = this.byRoleRefToReferenceTargetNode.get(
      role as unknown as string,
    );
    if (maybeTargetNodeRef) {
      maybeTargetNodeRef.value = this.referenceTargetNodeGetter(role);
    }
    const maybeTargetRefRef = this.byRoleRefToReferenceTargetRef.get(
      role as unknown as string,
    );
    if (maybeTargetRefRef) {
      maybeTargetRefRef.value = this.referenceTargetRefGetter(role);
    }
  }

  triggerChangeInProperty(role: PropertyRole) {
    const maybeRef = this.byRoleRefToProperty.get(role as unknown as string);
    if (maybeRef) {
      maybeRef.value = this.propertyGetter(role);
    }
  }
}
