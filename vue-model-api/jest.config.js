/** @type {import('ts-jest').JestConfigWithTsJest} */
module.exports = {
  transform: {
    "^.+\\.(t|j)sx?$": ["ts-jest", { tsconfig: { allowJs: true } }],
  },
  moduleNameMapper: {
    "^@modelix/ts-model-api$": "<rootDir>/../ts-model-api/src/index.ts",
    "^@modelix/ts-model-api/(.*)\\.js$": "<rootDir>/../ts-model-api/src/$1",
    "^@modelix/ts-model-api/(.*)$": "<rootDir>/../ts-model-api/src/$1",
    "^(\\..*)\\.js$": "$1",
  },
  modulePathIgnorePatterns: ["<rootDir>/dist/"],
  testEnvironment: "node",
  transformIgnorePatterns: ["node_modules/(?!(@modelix)/)"],
};
