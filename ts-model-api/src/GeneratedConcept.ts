import type {IConceptJS} from "./IConceptJS.js";

export abstract class GeneratedConcept implements IConceptJS {

  protected constructor(private uid: string) {
  }

  abstract getDirectSuperConcepts(): Array<IConceptJS>

  getUID(): string {
    return this.uid;
  }

}
