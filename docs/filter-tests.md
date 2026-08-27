# `quietly:filter-tests`

`filter-tests` generates or updates JUnit/RestAssured integration tests for discovered Hibernate filters.

## Recommended lifecycle binding

```xml
<plugin>
    <groupId>io.github.quietly-official</groupId>
    <artifactId>quietly-maven-plugin</artifactId>
    <version>0.2.0</version>
    <executions>
        <execution>
            <id>generate-quietly-filter-tests</id>
            <phase>generate-test-sources</phase>
            <goals>
                <goal>filter-tests</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Then use one normal lifecycle invocation:

```bash
mvn test
```

The goal writes source under `target/generated-test-sources/quietly` by default and registers that directory with the
current `MavenProject`. Its declared default phase is `generate-test-sources`.

## What it generates

For each filtered entity with a resolved service and field, Quietly creates or updates `<Entity>FiltersTest`:

- `@QuarkusTest` and `@TestHTTPEndpoint(<Service>.class)`;
- an injected `EntityManager`;
- a `beforeEach()` method that resets the entity and loads its SQL fixture;
- one test method per accepted Hibernate filter;
- a Javadoc marker such as `@quietly-generated filter="obj.status"`.

Generated tests extend `FilterTestBase` from `quietly-test-support`.

## Generation prerequisites

Source generation requires:

- a matching REST service class;
- an accepted entity field match;
- a parseable existing target file, if one already exists.

The generated setup also expects the entity's Panache operations, a public `TABLE_NAME`, and a matching SQL fixture.
Doctor diagnoses those fixture requirements, but they do not currently block filter source generation. Missing consumer
prerequisites can therefore surface later as compilation or runtime failures.

With default safety settings, a missing service or unresolved field fails generation. To skip and report instead:

```xml
<failOnMissingService>false</failOnMissingService>
<failOnUnresolvedField>false</failOnUnresolvedField>
```

Use relaxed settings intentionally; they allow partial generation.

## Idempotency and existing source

Quietly is incremental:

- it does not add a duplicate method with the same generated method name;
- it adds a Quietly marker to a recognized matching method when the marker is absent;
- it adds `beforeEach()` when missing;
- it adds newly discovered filter methods;
- it preserves custom methods;
- it reports stale marked methods but does not remove them.

If the expected file does not contain the expected `<Entity>FiltersTest` class or cannot be parsed, Quietly reports
`SKIPPED_INVALID_EXISTING_FILE` and leaves it for manual repair.

Generated files are AST-rendered and may be reformatted when updated. Review generated-source diffs if those files are
persisted outside `target`.

## Dry-run

```xml
<dryRun>true</dryRun>
```

Dry-run performs discovery and planning and writes Markdown/JSON reports, but does not write Java files or register the
test source directory. Candidate filter methods are reported as `WOULD_GENERATE`. Dry-run does not compile or execute
the proposed source.

## Reports

```text
target/quietly/filters-report.md
target/quietly/filters-report.json
```

Important generation statuses include `GENERATED`, `WOULD_GENERATE`, `EXISTING`, `UPDATED_MARKER`,
`STALE_GENERATED_TEST`, `SKIPPED_MISSING_SERVICE`, and `SKIPPED_UNRESOLVED_FIELD`.

Execution remains `NOT_MEASURED`. A successful generation report does not prove that Surefire ran the test.

## Separate Maven invocations

Avoid:

```bash
mvn compile quietly:filter-tests
mvn test
```

The source root registered in the first Maven process is not retained by the second. Bind generation to
`generate-test-sources` so source generation, test compilation, and execution happen in one lifecycle.
