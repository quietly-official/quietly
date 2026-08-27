# `quietly:scan`

`scan` inventories Hibernate filters declared on compiled application entities. It does not generate test sources and
does not evaluate whether a test is ready to generate.

## When to use it

Use `scan` as the first project check:

```bash
mvn compile quietly:scan
```

Its default lifecycle phase is `process-classes`, and it requires the compile/runtime dependency scope. When invoked
directly, run `compile` first so the current module output contains the application entities.

## What it discovers

- Classes annotated with JPA `@Entity` in the current module's compiled output.
- Hibernate `@Filter` and `@FilterDef` metadata, including their container annotations.
- Filter names normalized into a prefix and final field segment.

By default, only entities with Hibernate filters appear in the scan result. Use `entityPackagePattern` to restrict the
eligible package. An exact pattern matches one package; a pattern ending in `.*` includes subpackages.

## Output

Default files:

```text
target/quietly/filters-report.md
target/quietly/filters-report.json
```

Each discovered logical filter receives `DISCOVERED`. The report intentionally says that generatable filters, blocked
filters, and readiness were not evaluated. Module context and execution status `NOT_MEASURED` are also recorded.

## Relevant configuration

- `entityPackagePattern`
- `basePackage` when referenced by that pattern
- `reportFile`

Other common parameters may appear in the generated Maven descriptor because the Mojo shares configuration plumbing,
but scan does not resolve REST services, inspect fixtures, write tests, or use generation failure settings.

## Limits

- Scan only sees compiled application classes in the current `MavenProject` output.
- It does not traverse child modules from an aggregator POM.
- It does not prove that a service, field, fixture, or endpoint is usable.
- Zero discovered filters is a valid inventory result, not a successful readiness check.

If scan returns zero unexpectedly, see [Troubleshooting](troubleshooting.md#zero-entities-or-filters-discovered).
