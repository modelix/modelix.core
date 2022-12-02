import type {ILanguage} from "./ILanguage";
import {LanguageRegistry} from "./LanguageRegistry";
import type {INodeJS} from "./INodeJS";
import type {TypedNode} from "./TypedNode";

export abstract class GeneratedLanguage implements ILanguage {

  public nodeWrappers: Map<string, (node: INodeJS) => TypedNode> = new Map()

  constructor(readonly name: string) {}

  public register() {
    LanguageRegistry.INSTANCE.register(this)
  }

  public unregister() {
    LanguageRegistry.INSTANCE.unregister(this)
  }

  public isRegistered(): boolean {
    return LanguageRegistry.INSTANCE.isRegistered(this)
  }

  public assertRegistered() {
    if (!this.isRegistered()) throw Error("Language " + this.name + " is not registered")
  }

  public getName(): string {
    return this.name
  }

  public getUID(): string {
    return this.name
  }
}
