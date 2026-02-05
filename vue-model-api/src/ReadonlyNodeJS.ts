/* eslint-disable @typescript-eslint/no-unused-vars -- We can ignore unused variables of the super class */
import { org } from "@modelix/model-client";
import type {
  INodeJS,
  IConceptJS,
  ChildRole,
  ReferenceRole,
  PropertyRole,
} from "@modelix/ts-model-api";
const { PropertyChanged, ReferenceChanged, ChildrenChanged } =
  org.modelix.model.client2;

type INodeReferenceJS = ReturnType<INodeJS["getReference"]>;

export class ReadOnlyNodeJS implements INodeJS {
  constructor(
    public readonly node: INodeJS,
    private readonly notifyReadOnly: () => void,
  ) {}

  getConcept(): IConceptJS | undefined {
    return this.node.getConcept();
  }

  getConceptUID(): string | undefined {
    return this.node.getConceptUID();
  }

  getReference() {
    return this.node.getReference();
  }

  getRoleInParent(): ChildRole | undefined {
    return this.node.getRoleInParent();
  }

  getParent(): INodeJS | undefined {
    const rawParent = this.node.getParent();
    return rawParent
      ? new ReadOnlyNodeJS(rawParent, this.notifyReadOnly)
      : undefined;
  }

  remove(): void {
    this.notifyReadOnly();
  }

  getChildren(role: ChildRole | undefined): INodeJS[] {
    return this.node
      .getChildren(role)
      .map((rawChild) => new ReadOnlyNodeJS(rawChild, this.notifyReadOnly));
  }

  getAllChildren(): INodeJS[] {
    return this.node
      .getAllChildren()
      .map((rawChild) => new ReadOnlyNodeJS(rawChild, this.notifyReadOnly));
  }

  moveChild(role: ChildRole | undefined, index: number, child: INodeJS): void {
    this.notifyReadOnly();
  }

  addNewChild(
    role: ChildRole | undefined,
    index: number,
    concept: IConceptJS | undefined,
  ): INodeJS {
    this.notifyReadOnly();
    throw new Error("Cannot add child to readonly node");
  }

  removeChild(child: INodeJS): void {
    this.notifyReadOnly();
  }

  getReferenceRoles(): ReferenceRole[] {
    return this.node.getReferenceRoles();
  }

  getReferenceTargetNode(role: ReferenceRole): INodeJS | undefined {
    const rawTarget = this.node.getReferenceTargetNode(role);
    return rawTarget
      ? new ReadOnlyNodeJS(rawTarget, this.notifyReadOnly)
      : rawTarget;
  }

  getReferenceTargetRef(role: ReferenceRole): INodeReferenceJS | undefined {
    return this.node.getReferenceTargetRef(role);
  }

  setReferenceTargetNode(
    role: ReferenceRole,
    target: INodeJS | undefined,
  ): void {
    this.notifyReadOnly();
  }

  setReferenceTargetRef(
    role: ReferenceRole,
    target: INodeReferenceJS | undefined,
  ): void {
    this.notifyReadOnly();
  }

  getPropertyRoles(): PropertyRole[] {
    return this.node.getPropertyRoles();
  }

  getPropertyValue(role: PropertyRole): string | undefined {
    return this.node.getPropertyValue(role);
  }

  setPropertyValue(role: PropertyRole, value: string | undefined): void {
    this.notifyReadOnly();
  }
}
