import type {GeneratedLanguage} from "./GeneratedLanguage.js";
import type {INodeJS} from "./INodeJS.js";
import type {ITypedNode} from "./TypedNode.js";
import { TypedNode, UnknownTypedNode} from "./TypedNode.js";
import type {IConceptJS} from "./IConceptJS.js";

const GLOBAL_INSTANCE_KEY = "__modelix_LanguageRegistry_INSTANCE__";

export class LanguageRegistry {
  public static readonly INSTANCE: LanguageRegistry = (() => {
    const g = globalThis as unknown as Record<string, LanguageRegistry | undefined>;
    let instance = g[GLOBAL_INSTANCE_KEY];
    if (!instance) {
      instance = new LanguageRegistry();
      g[GLOBAL_INSTANCE_KEY] = instance;
    }
    return instance;
  })();

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
      for (const lang of this.languages.values()) {
        for (const entry of lang.nodeWrappers.entries()) {
          this.nodeWrappers.set(entry[0], entry[1])
        }
      }
    }
    const conceptUID = node.getConceptUID();
    if (conceptUID === undefined) return new TypedNode(node)
    const wrapper = this.nodeWrappers.get(conceptUID)
    const wrapped = wrapper === undefined ? new UnknownTypedNode(node) : wrapper(node);
    return this.wrapperCache ? this.wrapperCache(wrapped) : wrapped
  }

  public resolveConcept(uid: string): IConceptJS | undefined {
    if (this.concepts === undefined) {
      this.concepts = new Map()
      for (const language of this.getAll()) {
        for (const concept of language.getConcepts()) {
          this.concepts.set(concept.getUID(), concept)
        }
      }
    }
    return this.concepts.get(uid)
  }
}
