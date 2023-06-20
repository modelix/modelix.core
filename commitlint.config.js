module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "scope-enum": [
      2,
      "always",
      [
        "deps",
        "light-model-client",
        "model-server-lib",
        "metamodel-export",
        "model-api-gen",
        "model-api-gen-gradle",
        "model-api",
        "model-client",
        "model-server",
        "modelql",
        "ts-model-api",
      ],
    ],
    "subject-case": [0, 'never']
  },
};
