export interface IConceptJS {
  getUID(): string
  getDirectSuperConcepts(): Array<IConceptJS>
}
