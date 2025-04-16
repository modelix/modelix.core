module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "scope-enum": [
      2,
      "always",
      [
        "authorization",
        "bulk-model-sync",
        "bulk-model-sync-gradle",
        "datastructures",
        "deps",
        "git-import",
        "metamodel-export",
        "model-api-gen",
        "model-api-gen-gradle",
        "model-api",
        "model-client",
        "model-datastructure",
        "model-server",
        "model-server-openapi",
        "modelql",
        "mps-model-adapters",
        "mps-model-server",
        "mps-sync-plugin",
        "openapi",
        "streams",
        "ts-model-api",
        "vue-model-api",
      ],
    ],
    "subject-case": [0, 'never'],
    // No need to restrict the body and footer line length. That only gives issues with URLs etc.
    "body-max-line-length": [0, 'always'],
    "footer-max-line-length": [0, 'always']
  },
  ignores: [
    (message) => message.includes('skip-lint')
  ],
};
