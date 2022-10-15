
export interface INode {
  getConcept(): IConcept | null
  getConceptReference(): IConceptReference | null

  getReference(): INodeReference
  getRoleInParent(): string | null
  getParent(): INode | null

  getChildren(role: string | null): Iterable<INode>
  getAllChildren(): Iterable<INode>
  moveChild(role: string | null, index: number, child: INode): void
  addNewChild(role: string | null, index: number, concept: IConcept | null): INode
  removeChild(child: INode): void

  getReferenceRoles(): Array<string>
  getReferenceTargetNode(role: string): INode | null
  getReferenceTargetRef(role: string): INodeReference | null
  setReferenceTargetNode(role: string, target: INode | null): void
  setReferenceTargetRef(role: string, target: INodeReference | null): void

  getPropertyRoles(): Array<string>
  getPropertyValue(role: string): string | null
  setPropertyValue(role: string, value: string | null): void
}

export interface INodeReference {}
export interface IConcept {}
export interface IConceptReference {}
