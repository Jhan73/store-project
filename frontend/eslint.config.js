// @ts-check
const eslint = require("@eslint/js");
const { defineConfig, globalIgnores } = require("eslint/config");
const tseslint = require("typescript-eslint");
const angular = require("angular-eslint");

module.exports = defineConfig([
  globalIgnores(["src/app/core/api/schema.d.ts"]),
  {
    files: ["**/*.ts"],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      "@angular-eslint/directive-selector": [
        "error",
        {
          type: "attribute",
          prefix: "app",
          style: "camelCase",
        },
      ],
      "@angular-eslint/component-selector": [
        "error",
        {
          type: "element",
          prefix: "app",
          style: "kebab-case",
        },
      ],
    },
  },
  {
    files: ["**/*.html"],
    extends: [
      angular.configs.templateRecommended,
      angular.configs.templateAccessibility,
    ],
    rules: {
      "@angular-eslint/template/i18n": [
        "error",
        {
          checkId: true,
          // Identifiers, enumerations and test hooks, not text a user reads.
          ignoreAttributes: [
            "severity",
            "optionLabel",
            "optionValue",
            "filterBy",
            "inputId",
            "ariaCurrentWhenActive",
            "data-testid",
          ],
        },
      ],
    },
  },
  {
    // Test harness templates are never shown to users.
    files: ["**/*.spec.ts/*.html"],
    rules: { "@angular-eslint/template/i18n": "off" },
  }
]);
