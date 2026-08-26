# Troubleshooting

Start with the Markdown report and Maven log. Filter goals default to:

```text
target/quietly/filters-report.md
target/quietly/filters-report.json
```

CRUD generation defaults to `target/quietly/crud-report.md` and `.json`. In a multi-module build, look under the module
where the goal actually ran.

## Zero entities or filters discovered

**Symptom:** scan reports `0` discovered filters, or CRUD finds no entities.

**Likely cause:** application classes were not compiled, the goal ran in the wrong module, the package pattern excludes
the entities, or the entities do not carry the expected annotations.

**Check:** run `mvn compile quietly:scan`; inspect the report's module context; confirm compiled `@Entity` classes exist
under that module's output; confirm filter entities have `@Filter`/`@FilterDef`.

**Fix:** compile first, run Quietly in the concrete application/test module, and correct or temporarily remove
`entityPackagePattern`.

## Plugin configured in the wrong module

**Symptom:** module context shows the parent or another artifact, and expected entities/services are absent.

**Likely cause:** Quietly is inherited or executed from a parent `packaging=pom` project.

**Check:** inspect `artifactId`, `packaging`, `basedir`, and `WARNING_AGGREGATOR_MODULE` in the report.

**Fix:** move the plugin execution to the module that owns the Quarkus test runtime. Follow the
[multi-module guide](multi-module.md).

## Entity package pattern is wrong

**Symptom:** compilation succeeds but discovery returns fewer entities than expected.

**Likely cause:** an exact pattern was used where subpackages were intended, or `${basePackage}` resolves incorrectly.

**Check:** compare the compiled entity package with `entityPackagePattern`. `com.example.model` is exact;
`com.example.model.*` includes that package and descendants.

**Fix:** correct `basePackage`/`entityPackagePattern`. If `${basePackage}` is present, configure `basePackage` explicitly.

## REST service not found

**Symptom:** `SKIPPED_MISSING_SERVICE`, often with an expected fully qualified class name.

**Likely cause:** the service is not on the current module classpath or does not match Quietly's package/name convention.

**Check:** compare the reported expected class with the real service and confirm its artifact is a dependency of the
executing module.

**Fix:** configure `servicePackagePattern` and `serviceNamePattern`, add the missing module dependency, or—only for
partial onboarding—set `failOnMissingService=false`.

## Filter field unresolved

**Symptom:** `SKIPPED_UNRESOLVED_FIELD` says no deterministic field match was found.

**Likely cause:** the final filter-name segment does not exactly match a field in the entity hierarchy.

**Check:** compare the reported subject, such as `obj.status`, with the entity field `status`.

**Fix:** correct the filter metadata or field name. Use `FUZZY` only as an explicit legacy diagnostic; keep `STRICT` for
deterministic generation.

## Field match is ambiguous

**Symptom:** `SKIPPED_UNRESOLVED_FIELD` mentions multiple exact fields in the hierarchy or ambiguous fuzzy matches.

**Likely cause:** a field is shadowed in a superclass/subclass, or multiple fields have the same best fuzzy distance.

**Check:** inspect all declared fields across the entity hierarchy. Re-run doctor with `STRICT` to distinguish exact
ambiguity from fuzzy ambiguity.

**Fix:** remove/rename the ambiguity or update filter metadata. Quietly intentionally refuses to guess.

## SQL fixture or `TABLE_NAME` is missing

**Symptom:** doctor reports `MISSING_TABLE_NAME`, `MISSING_SQL_FIXTURE`, or `ERROR_SQL_FIXTURE`.

**Likely cause:** the entity lacks a public static `TABLE_NAME`, or the expected SQL file is absent/unreadable.

**Check:** confirm the constant is public and inspect the exact fixture path reported by doctor:
`src/test/resources/sql/<TABLE_NAME>.sql` under the current module.

**Fix:** expose the correct constant and provide consumer-owned fixture data. Quietly does not create business fixtures.
Note that filter generation may still write source; the failure can appear later during compilation or runtime.

## Tests were generated but not executed

**Symptom:** Java files exist, but no corresponding Surefire test result exists.

**Likely cause:** generated sources were not registered in the Maven invocation that ran tests, tests are disabled, the
test naming/provider configuration excludes them, or the build did not reach the test phase.

**Check:** inspect `target/surefire-reports`, Maven's test count, `disabledByDefault`, and the effective lifecycle.

**Fix:** bind generation to `generate-test-sources`, run `mvn test` in the same invocation, enable the methods, and
check Surefire includes/provider configuration. Quietly's `NOT_MEASURED` status cannot confirm execution.

## Generated test sources are not compiled

**Symptom:** generated files exist but `target/test-classes` contains no generated test class.

**Likely cause:** the source root was not registered in this Maven process or a custom output directory/lifecycle bypassed
the expected phase ordering.

**Check:** run Maven with normal lifecycle logging and confirm `filter-tests`/`crud-tests` runs before `testCompile` in
the same process.

**Fix:** use the documented `generate-test-sources` execution and `mvn test`. Avoid invoking only the generator in a
previous process.

## Generation and test run used separate Maven processes

**Symptom:** `mvn compile quietly:filter-tests` writes files, then a separate `mvn test` ignores them.

**Likely cause:** `project.addTestCompileSourceRoot(...)` changes only the in-memory `MavenProject` for the current run.

**Check:** verify the commands were separate and the plugin is not lifecycle-bound.

**Fix:** bind the goal to `generate-test-sources` and run a single `mvn test`.

## Dry-run was misunderstood

**Symptom:** a report/log describes generation candidates, but no Java file or source root appears.

**Likely cause:** `dryRun=true` is working as designed.

**Check:** inspect the report header's `Dry run` value. Filter candidates use `WOULD_GENERATE`; experimental CRUD may
still use generation wording in its event rows.

**Fix:** review the plan, then set `dryRun=false` when ready. Dry-run never proves compilation or runtime behavior.

## Tests compile but fail at runtime

**Symptom:** Surefire reports database, fixture, security, endpoint, or assertion failures.

**Likely cause:** the consumer environment does not satisfy generated-test assumptions even though source generation
was possible.

**Check:** inspect the test failure, datasource/test profile, schema, SQL fixture data, security identity, REST path, and
actual response statuses.

**Fix:** correct the consumer test environment or replace/extend the generated smoke test with project-specific logic.
Quietly does not synthesize environment or business rules.

## Quietly bug or consumer problem?

**Symptom:** generated behavior is unexpected and ownership is unclear.

**Likely cause:** either Quietly made a discovery/planning/rendering error, or the consumer violates a documented
convention/runtime prerequisite.

**Check:** collect the Quietly version, goal/configuration, module context, Markdown/JSON report, generated source, Maven
error, and a minimal entity/service/fixture example. Compare the expected class/path in diagnostics with the consumer.

**Fix:** treat incorrect discovery, inconsistent doctor/generator service-field decisions, invalid Java rendering, or
non-idempotent duplication as likely Quietly defects. Treat missing classes, fixtures, endpoints, test data, database,
or security setup as consumer configuration until evidence shows otherwise. Report reproducible Quietly defects at the
[GitHub repository](https://github.com/quietly-official/quietly/issues).
