module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "scope-enum": [
      2,
      "always",
      [
        "bulk-model-sync",
        "bulk-model-sync-gradle",
        "bulk-model-sync-lib",
        "bulk-model-sync-mps",
        "bulk-model-sync-solution",
        "deps",
        "light-model-client",
        "model-server-lib",
        "metamodel-export",
        "model-api-gen",
        "model-api-gen-gradle",
        "model-api",
        "model-client",
        "model-datastructure",
        "model-server",
        "modelql",
        "mps-legacy-sync-plugin",
        "mps-model-adapters",
        "mps-model-server",
        "mps-model-server-plugin",
        "ts-model-api",
        "vue-model-api",
      ],
    ],
    "subject-case": [0, 'never'],
    // No need to restrict the body line length. That only gives issues with URLs etc.
    "body-max-line-length": [0, 'always']
  },
};
