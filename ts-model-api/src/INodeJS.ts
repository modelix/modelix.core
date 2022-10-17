
export interface INodeJS {
  getConcept(): IConceptJS | undefined
  getConceptReference(): IConceptReferenceJS | undefined

  getReference(): INodeReferenceJS
  getRoleInParent(): string | undefined
  getParent(): INodeJS | undefined

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

export interface INodeReferenceJS {}
export interface IConceptJS {}
export interface IConceptReferenceJS {
  getUID(): String
}
