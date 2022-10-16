import type {GeneratedLanguage} from "./GeneratedLanguage";

export class LanguageRegistry {
  public static INSTANCE: LanguageRegistry = new LanguageRegistry();
  private languages: Map<String, GeneratedLanguage> = new Map();

  public register(lang: GeneratedLanguage): void {
    this.languages.set(lang.name, lang);
  }

  public unregister(lang: GeneratedLanguage): void {
    this.languages.delete(lang.name);
  }

  public getAll(): GeneratedLanguage[] {
    return Array.from(this.languages.values());
  }
}
