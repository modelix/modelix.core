import type {GeneratedLanguage} from "./GeneratedLanguage";
import type {INodeJS} from "./INodeJS";
import {ITypedNode, TypedNode} from "./TypedNode";
import type {IConceptJS} from "./IConceptJS";

export class LanguageRegistry {
  public static INSTANCE: LanguageRegistry = new LanguageRegistry();
  private languages: Map<string, GeneratedLanguage> = new Map();
  private nodeWrappers: Map<string, (node: INodeJS) => TypedNode> | undefined = undefined
  private concepts: Map<string, IConceptJS> | undefined = undefined
  public wrapperCache: ((node: ITypedNode) => ITypedNode) | undefined = undefined

  public register(lang: GeneratedLanguage): void {
    this.languages.set(lang.name, lang);
    this.nodeWrappers = undefined
    this.concepts = undefined
  }

  public unregister(lang: GeneratedLanguage): void {
    this.languages.delete(lang.name);
    this.nodeWrappers = undefined
    this.concepts = undefined
  }

  public isRegistered(lang: GeneratedLanguage): boolean {
    return this.languages.has(lang.name)
  }

  public getAll(): GeneratedLanguage[] {
    return Array.from(this.languages.values());
  }

  public wrapNode(node: INodeJS): ITypedNode {
    if (this.nodeWrappers === undefined) {
      this.nodeWrappers = new Map()
      for (let lang of this.languages.values()) {
        for (let entry of lang.nodeWrappers.entries()) {
          this.nodeWrappers.set(entry[0], entry[1])
        }
      }
    }
    let conceptUID = node.getConceptUID();
    if (conceptUID === undefined) return new TypedNode(node)
    let wrapper = this.nodeWrappers.get(conceptUID)
    if (wrapper === undefined) {
      throw Error("No node wrapper found for concept " + conceptUID)
    }
    let wrapped = wrapper(node);
    return this.wrapperCache ? this.wrapperCache(wrapped) : wrapped
  }

  public resolveConcept(uid: string): IConceptJS | undefined {
    if (this.concepts === undefined) {
      this.concepts = new Map()
      for (let language of this.getAll()) {
        for (let entry of language.concepts.entries()) {
          this.concepts.set(entry[0], entry[1])
        }
      }
    }
    return this.concepts.get(uid)
  }
}
