# Changelog

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
