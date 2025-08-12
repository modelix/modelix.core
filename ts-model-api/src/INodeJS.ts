import type { IConceptJS } from "./IConceptJS.js";

export type ChildRole = object;
export type ReferenceRole = object;
export type PropertyRole = object;

export function toRoleJS<T extends string | undefined | null>(
  str: T
):
  | (ChildRole & ReferenceRole & PropertyRole)
  | (T extends undefined ? undefined : never)
  | (T extends null ? null : never) {
  return str as unknown as ChildRole & ReferenceRole & PropertyRole;
}

export interface INodeJS {
  getConcept(): IConceptJS | undefined;
  getConceptUID(): string | undefined;

  getReference(): INodeReferenceJS;
  getRoleInParent(): ChildRole | undefined;
  getParent(): INodeJS | undefined;

  remove(): void;

  getChildren(role: ChildRole | undefined): Array<INodeJS>;
  getAllChildren(): Array<INodeJS>;
  moveChild(role: ChildRole | undefined, index: number, child: INodeJS): void;
  addNewChild(
    role: ChildRole | undefined,
    index: number,
    concept: IConceptJS | undefined
  ): INodeJS;
  removeChild(child: INodeJS): void;

  getReferenceRoles(): Array<ReferenceRole>;
  getReferenceTargetNode(role: ReferenceRole): INodeJS | undefined;
  getReferenceTargetRef(role: ReferenceRole): INodeReferenceJS | undefined;
  setReferenceTargetNode(
    role: ReferenceRole,
    target: INodeJS | undefined
  ): void;
  setReferenceTargetRef(
    role: ReferenceRole,
    target: INodeReferenceJS | undefined
  ): void;

  getPropertyRoles(): Array<PropertyRole>;
  getPropertyValue(role: PropertyRole): string | undefined;
  setPropertyValue(role: PropertyRole, value: string | undefined): void;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- Keep for backward compatibility
type INodeReferenceJS = any;
