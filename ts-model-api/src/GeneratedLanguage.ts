import type {ILanguage} from "./ILanguage";

export abstract class GeneratedLanguage implements ILanguage {

  constructor(readonly name: string) {}

  public register() {
    //TypedLanguagesRegistry.register(this)
  }

  public unregister() {
    //TypedLanguagesRegistry.unregister(this)
  }

  public isRegistered(): boolean {
    //return TypedLanguagesRegistry.isRegistered(this)
    return true
  }

  public assertRegistered() {
    if (!this.isRegistered()) throw Error("Language " + this.name + " is not registered")
  }

  public getName(): String {
    return this.name
  }

  public getUID(): String {
    return this.name
  }


}
