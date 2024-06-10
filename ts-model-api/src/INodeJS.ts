import type {IConceptJS} from "./IConceptJS.js";
import type {ITypedNode} from "./TypedNode.js";

export interface INodeJS {
  getConcept(): IConceptJS | undefined
  getConceptUID(): string | undefined

  getReference(): INodeReferenceJS
  getRoleInParent(): string | undefined
  getParent(): INodeJS | undefined

  remove(): void
  wrap(): ITypedNode;

  getChildren(role: string | undefined): Array<INodeJS>
  getAllChildren(): Array<INodeJS>
  moveChild(role: string | undefined, index: number, child: INodeJS): void
  addNewChild(role: string | undefined, index: number, concept: IConceptJS | undefined): INodeJS
  removeChild(child: INodeJS): void

  getReferenceRoles(): Array<string>
  getReferenceTargetNode(role: string): INodeJS | undefined
  getReferenceTargetRef(role: string): INodeReferenceJS | undefined
  setReferenceTargetNode(role: string, target: INodeJS | undefined): void
  setReferenceTargetRef(role: string, target: INodeReferenceJS | undefined): void

  getPropertyRoles(): Array<string>
  getPropertyValue(role: string): string | undefined
  setPropertyValue(role: string, value: string | undefined): void
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars -- Keep for backward compatibility
type INodeReferenceJS = any
