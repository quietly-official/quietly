# Concepts and mental model

Quietly turns compiled project metadata into explicit generation decisions. Its model is intentionally staged:

1. **Discover** compiled JPA entities and Hibernate filter metadata.
2. **Analyze** module context and resolve services, fields, fixtures, and existing generated tests.
3. **Diagnose** missing or ambiguous prerequisites.
4. **Plan** filter-test decisions in a `GenerationPlan`.
5. **Generate** source only for decisions the generator accepts.

`doctor` and `filter-tests` use the same filter `GenerationPlan`, so their service and field decisions share one source
of truth. Fixture diagnostics are also included in the plan, but a missing fixture or `TABLE_NAME` is currently reported
separately and does not itself stop `filter-tests`; compilation or runtime can still fail until the consumer fixes it.

## What Quietly does

- Scans compiled application classes in the current Maven module.
- Extracts Hibernate `@Filter` and `@FilterDef` metadata from `@Entity` classes.
- Resolves a REST service by package and class-name conventions.
- Resolves filter fields with deterministic `STRICT` matching or opt-in `FUZZY` matching.
- Diagnoses expected SQL fixture and existing generated-test state.
- Generates incremental filter tests and experimental CRUD smoke tests.
- Writes Markdown and JSON reports.

## What Quietly does not do

- It does not traverse every child in a Maven reactor from an aggregator POM.
- It does not invent unrecognized REST endpoints or payload semantics.
- It does not invent SQL fixtures or business data.
- It does not guarantee runtime success without the consumer's database, security, fixtures, and environment.
- It does not measure whether generated tests ran or passed.
- It does not replace custom integration tests for project-specific behavior.

## Discovery and readiness

These terms describe different stages and must not be treated as synonyms:

| Term | Meaning |
| --- | --- |
| **Discovered** | Quietly found filter metadata on a compiled entity. `scan` stops at this level. |
| **Ready / generatable** | The filter's REST service and entity field were resolved for generation. |
| **Blocked** | A required service or deterministic field resolution is missing or ambiguous. |
| **Existing** | A Quietly-marked method for the current filter already exists in the expected test class. |
| **Stale** | A marked generated method refers to a filter/operation that is no longer discovered/generated. |
| **Generated** | The generation goal added a source artifact in this run. |
| **Execution not measured** | Quietly did not inspect Surefire results; Maven owns runtime pass/fail. |

Readiness is not runtime coverage. A filter can be ready for source generation while its test still needs valid fixture
data, database configuration, security setup, and a compatible endpoint.

## Report statuses

Common statuses are:

| Status | Interpretation |
| --- | --- |
| `DISCOVERED` | Found by `scan`; readiness was not evaluated. |
| `OK` | Service and field are ready in a doctor/generation plan. |
| `GENERATED` | A method or CRUD operation was generated. |
| `WOULD_GENERATE` | Filter generation was evaluated in dry-run without writing source. |
| `EXISTING` | A recognized generated method already exists. |
| `UPDATED_MARKER` | A matching method existed and the generator added its Quietly marker. |
| `STALE_GENERATED_TEST` | A marked method no longer corresponds to current discovery/generation. |
| `SKIPPED_MISSING_SERVICE` | The configured REST service convention did not resolve. |
| `SKIPPED_UNRESOLVED_FIELD` | No acceptable field match was found. |
| `MISSING_TABLE_NAME` | The entity does not expose the expected public constant. |
| `MISSING_SQL_FIXTURE` | The expected SQL fixture file is absent. |
| `ERROR_SQL_FIXTURE` | Quietly could not inspect the fixture convention. |
| `SKIPPED_INVALID_EXISTING_FILE` | The expected generated test class could not be found or parsed in its file. |
| `WARNING_AGGREGATOR_MODULE` | The goal ran on `packaging=pom`; configure a concrete module instead. |

Generation leaves stale methods in place for human review. Quietly does not silently delete them.

## Report metrics

- `discoveredFilters`: logical filters found on entities.
- `generatableFilters`: discovered filters with resolved service and field prerequisites.
- `blockedFilters`: discovered filters blocked by service or field resolution.
- `generationReadinessPercent`: generatable filters divided by discovered filters.
- `generatedTestClasses` / `generatedTestMethods`: recognized or produced Quietly artifacts.

Counts use `(entity, capability, subject)` as the logical identity, so repeated events do not inflate totals. A zero
denominator produces `0.00%`.

Legacy JSON fields containing `coverage` remain for compatibility. They describe generation state, not executed test
coverage. The report explicitly uses execution status `NOT_MEASURED`.
