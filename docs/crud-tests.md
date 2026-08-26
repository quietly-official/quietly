# `quietly:crud-tests`

`crud-tests` is experimental and convention-based. It generates a narrow set of REST smoke tests; it is not a general
CRUD contract generator.

## Current behavior

For each compiled application entity with a matching REST service, Quietly creates or updates `<Entity>CrudTest` with:

- collection `GET`, expecting HTTP `200`;
- `GET /{id}` using `Long.MAX_VALUE`, expecting HTTP `404`.

Generated methods carry markers:

```text
@quietly-generated crud="list"
@quietly-generated crud="get-missing"
```

Quietly does not currently generate `POST`, `PUT`, or `DELETE` tests. It does not infer DTOs, valid payloads,
authorization rules, or project-specific endpoint semantics.

## Run it

Direct diagnostic/generation run:

```bash
mvn compile quietly:crud-tests
```

The goal's default phase is `generate-test-sources`, and it registers the generated-test directory when not in dry-run.
If CRUD tests are part of every build, bind the goal to `generate-test-sources` and run `mvn test` in one invocation,
just as for [filter tests](filter-tests.md#recommended-lifecycle-binding).

## Discovery and prerequisites

Unlike filter generation, CRUD generation discovers all compiled application `@Entity` classes selected by
`entityPackagePattern`, including entities without Hibernate filters.

Each entity needs:

- a REST service matching `servicePackagePattern` and `serviceNamePattern`;
- endpoint behavior compatible with the two fixed smoke-test assumptions;
- the entity/fixture conventions used by the generated `beforeEach()` setup.

The generated setup expects a public `TABLE_NAME`, a fixture under `src/test/resources/sql/<TABLE_NAME>.sql`, and
compatible Panache operations. CRUD does not plan endpoint capabilities from resource annotations; a resolved service
class does not guarantee that either endpoint exists or returns the expected status.

## Safety controls

```xml
<disabledByDefault>true</disabledByDefault>
<failOnMissingService>false</failOnMissingService>
<dryRun>true</dryRun>
```

- `disabledByDefault` adds `@Disabled` to generated methods.
- `failOnMissingService=false` skips entities without the conventional service.
- `dryRun=true` prevents source writes and source-root registration while still producing reports/logs.

Because CRUD generation is experimental, inspect generated source before enabling it in CI. Dry-run report statuses for
new CRUD operations currently use generation wording even though no source is written; verify the dry-run flag and the
filesystem rather than interpreting `GENERATED` as a write.

## Idempotency

Quietly recognizes the two fixed method names, adds missing markers/methods, and reports marked operations outside the
current set as `STALE_GENERATED_TEST`. It does not delete stale or custom methods. An existing file without the expected
`<Entity>CrudTest` class is reported as `SKIPPED_INVALID_EXISTING_FILE`.

## Output

Generated source:

```text
target/generated-test-sources/quietly
```

Default reports:

```text
target/quietly/crud-report.md
target/quietly/crud-report.json
```

CRUD coverage in these reports means generated logical operations, not endpoint coverage or runtime test coverage.
Maven/Surefire remains responsible for execution results.
