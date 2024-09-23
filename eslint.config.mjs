import globals from "globals";
import pluginJs from "@eslint/js";
import tseslint from "typescript-eslint";
import eslintConfigPrettier from "eslint-config-prettier";

export default [
  { files: ["**/*.{js,mjs,cjs,ts}"] },
  { ignores: ["**/.gradle", "**/dist/", "**/build/"] },
  {
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: {
          ...globals.browser,
          ...globals.node,
          ...globals.es2021
      },
    },
  },
  pluginJs.configs.recommended,
  ...tseslint.configs.recommended,
  eslintConfigPrettier, // > Turns off all rules that are unnecessary or might conflict with Prettier.
  {
    // The Redocly plugin uses CommonJS syntax for imports and exports as in the Redocly documentation.
    files: ["model-server-openapi/**/*"],
    rules: {
      "@typescript-eslint/no-require-imports": "off"
    }
  },
  {
    rules: {
      "@typescript-eslint/consistent-type-imports": "error",
      "@typescript-eslint/no-import-type-side-effects": "error",
      "@typescript-eslint/no-unused-vars": [
        "error",
        {
          vars: "all",
          args: "after-used",
          varsIgnorePattern: "^_.+",
          argsIgnorePattern: "^_.+",
        },
      ],
      "@typescript-eslint/ban-ts-comment": [
        "error",
        { "ts-ignore": "allow-with-description" },
      ],
    },
  },
];
