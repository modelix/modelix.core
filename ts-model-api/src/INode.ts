
export interface INode {
  getConcept(): IConcept | undefined
  getConceptReference(): IConceptReference | undefined

  getReference(): INodeReference
  getRoleInParent(): string | undefined
  getParent(): INode | undefined

  getChildren(role: string | undefined): Array<INode>
  getAllChildren(): Array<INode>
  moveChild(role: string | undefined, index: number, child: INode): void
  addNewChild(role: string | undefined, index: number, concept: IConcept | undefined): INode
  removeChild(child: INode): void

  getReferenceRoles(): Array<string>
  getReferenceTargetNode(role: string): INode | undefined
  getReferenceTargetRef(role: string): INodeReference | undefined
  setReferenceTargetNode(role: string, target: INode | undefined): void
  setReferenceTargetRef(role: string, target: INodeReference | undefined): void

  getPropertyRoles(): Array<string>
  getPropertyValue(role: string): string | undefined
  setPropertyValue(role: string, value: string | undefined): void
}

export interface INodeReference {}
export interface IConcept {}
export interface IConceptReference {}
