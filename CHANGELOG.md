# Changelog

## 0.2.0

### Changed

- Introduced shared project discovery and generation planning architecture.
- Added explicit Maven module context to diagnostics and reports.
- Improved multi-module Maven diagnostics and documentation.
- Centralized discovery through `ProjectDiscovery` and `DiscoveredProject`.
- Centralized service, fixture and field resolution through shared resolver components.
- Made `FilterTestAstBuilder` renderer-only with field readiness decided before AST generation.
- Added internal `GenerationPlan` support for `FILTER_TEST` decisions shared by `doctor` and `filter-tests`.
- Kept CRUD behavior and generated Java output intentionally compatible.

### Compatibility

- Quietly remains scoped to the current concrete Maven module.
- Quietly is not a reactor aggregator plugin.
- Public report/status semantics remain unchanged.
- CRUD generation remains experimental.

## 0.1.0-beta.1

First public beta release candidate.

### Included

- Maven goals: `scan`, `doctor`, `filter-tests` and `crud-tests`.
- Generated tests under `target/generated-test-sources/quietly`.
- Recommended Maven lifecycle binding at `generate-test-sources`.
- Markdown and JSON reports with explicit generation readiness metrics.
- Execution status reported as `NOT_MEASURED`; Maven/Surefire remains the runtime execution authority.
- Quarkus end-to-end consumer validation through `quietly-demo`.
- Maven Central metadata, sources, Javadoc and signing configuration.

### Known Limitations

- Java packages intentionally remain under `ua.quietly...` for this beta.
- Quietly does not measure runtime test execution coverage.
- CRUD test generation is experimental and convention-based.
