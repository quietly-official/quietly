# `quietly:doctor`

`doctor` analyzes discovered filter metadata and writes diagnostics without generating or modifying test sources.

## Run doctor

```bash
mvn compile quietly:doctor
```

Its default phase is `process-classes`. The application classes must already be compiled.

To make diagnostic problems fail Maven:

```xml
<configuration>
    <failOnProblems>true</failOnProblems>
</configuration>
```

Doctor writes the report before throwing the build failure.

## What doctor evaluates

For each filtered entity, doctor plans and reports:

- expected REST service package and class name;
- exact or fuzzy entity field resolution;
- public `TABLE_NAME` availability;
- SQL fixture at `src/test/resources/sql/<TABLE_NAME>.sql`;
- existing Quietly-marked filter methods;
- stale marked methods whose filter is no longer discovered;
- whether an expected generated test file can be parsed;
- current Maven module context.

REST service and field resolution determine filter generation readiness. Fixture findings are separate diagnostics:
they matter to the generated setup and runtime, but a missing fixture is not currently included in the filter readiness
percentage.

## Ready and blocked filters

- `OK` means the service and field resolved.
- `SKIPPED_MISSING_SERVICE` means the expected REST service class was not loadable.
- `SKIPPED_UNRESOLVED_FIELD` means no deterministic field match was accepted.
- `EXISTING` means a method with a matching `@quietly-generated filter="..."` marker exists.
- `STALE_GENERATED_TEST` means a marked method refers to a filter no longer discovered.

Fixture diagnostics use `OK_SQL_FIXTURE`, `MISSING_TABLE_NAME`, `MISSING_SQL_FIXTURE`, or `ERROR_SQL_FIXTURE`.
`STALE_GENERATED_TEST` is a report finding; doctor does not delete the method.

## Output

```text
target/quietly/filters-report.md
target/quietly/filters-report.json
```

The summary separates discovered, generatable, and blocked filters. `generationReadinessPercent` is a source-generation
metric, not runtime coverage. Execution remains `NOT_MEASURED`; inspect Maven and Surefire for actual test results.

## Doctor versus generation

Doctor and `filter-tests` share the same `DoctorPlanner` and filter `GenerationPlan`. Doctor renders plan decisions into
diagnostics; `filter-tests` consumes them when creating or updating Java source. Doctor never registers a test source
root and never writes Java files.

Run doctor before generation when onboarding a project or after changing entities, filters, service conventions, or
fixtures.

## Aggregator modules

On `packaging=pom`, doctor emits `WARNING_AGGREGATOR_MODULE` and returns a module-context report. This is not a reactor
scan. Move the plugin configuration to the concrete Quarkus application/test module. See [Multi-module projects](multi-module.md).
