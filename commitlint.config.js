module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "scope-enum": [
      2,
      "always",
      [
        "deps",
        "js-model-client",
        "light-model-client",
        "model-server-lib",
        "metamodel-export",
        "model-api-gen",
        "model-api-gen-gradle",
        "model-api",
        "model-client",
        "model-server",
        "model-sync-lib",
        "modelql",
        "mps-model-adapters",
        "mps-model-server",
        "mps-model-server-plugin",
        "ts-model-api",
      ],
    ],
    "subject-case": [0, 'never']
  },
};
