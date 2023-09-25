import type { ILanguage } from "./ILanguage.js";
import { LanguageRegistry } from "./LanguageRegistry.js";
import type { INodeJS } from "./INodeJS.js";
import type { TypedNode } from "./TypedNode.js";
import type { GeneratedConcept } from "./GeneratedConcept.js";

export abstract class GeneratedLanguage implements ILanguage {
  public nodeWrappers: Map<string, (node: INodeJS) => TypedNode> = new Map();

  constructor(readonly name: string) {}

  public abstract getConcepts(): Array<GeneratedConcept>;

  public register() {
    LanguageRegistry.INSTANCE.register(this);
  }

  public unregister() {
    LanguageRegistry.INSTANCE.unregister(this);
  }

  public isRegistered(): boolean {
    return LanguageRegistry.INSTANCE.isRegistered(this);
  }

  public assertRegistered() {
    if (!this.isRegistered())
      throw Error("Language " + this.name + " is not registered");
  }

  public getName(): string {
    return this.name;
  }

  public getUID(): string {
    return this.name;
  }
}
