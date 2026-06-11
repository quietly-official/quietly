<p align="center">
  <img src="docs/img/quietly_logo_upscaled.png" style="width: 500px; border: 1px solid #ccc;" />
</p>

<h2 align="center"><i>Tests that grow with your code - incremental, safe, automated.</i></h2>

<p align="center">
  <a href="https://github.com/quietly-official/quietly/actions/workflows/build.yml">
    <img src="https://github.com/quietly-official/quietly/actions/workflows/build.yml/badge.svg" alt="Build" />
  </a>
</p>

Quietly is a Maven plugin for Quarkus/Hibernate projects. It scans JPA entities, reads Hibernate `@Filter` and
`@FilterDef` metadata, diagnoses missing test prerequisites, and generates JUnit/RestAssured integration tests for REST
filters.

The first beta release candidate uses groupId `io.github.quietly-official` and version `0.1.0-beta.1`.

Start here:

- [English documentation](docs/eng_.md)
- [Documentazione italiana](docs/it_.md)

Quick local install:

```bash
mvn clean install
```

Quietly requires Java 17 and is built against Maven 3.9.6 as its minimum documented Maven baseline.

For regular builds, bind Quietly to Maven's `generate-test-sources` phase and run:

```bash
mvn test
```

This generates tests under `target/generated-test-sources/quietly`, registers that directory as a test source root,
compiles the generated classes, and runs them in the same Maven lifecycle.

Recommended first run:

1. Run `quietly:scan` to inventory filters.
2. Run `quietly:doctor` to find missing services, unresolved fields, fixtures, and stale generated tests.
3. Run `quietly:filter-tests` once the report looks sane.
4. Run `quietly:crud-tests` to generate baseline REST CRUD smoke tests for conventional services.

See the language-specific documentation for the lifecycle-bound plugin configuration.

Generated tests are active by default, idempotent, and reported in both Markdown and JSON:

```text
target/quietly/filters-report.md
target/quietly/filters-report.json
target/quietly/crud-report.md
target/quietly/crud-report.json
```

Report percentages describe generation readiness, not runtime test coverage. Quietly reports execution as
`NOT_MEASURED`; Maven/Surefire is the source of truth for whether generated tests ran and passed.

The main CI also checks out
[`quietly-demo`](https://github.com/quietly-official/quietly-demo) and runs its
plain `mvn clean test` lifecycle against the Quietly commit or pull request under test. The build fails if the generated
test source or its Surefire execution evidence is missing.

Because the demo repository is private, the Quietly repository must define the Actions secret
`QUIETLY_INTEGRATION_DEMO_TOKEN`. Use a fine-grained read-only token with access only to
`quietly-demo` and the `Contents: read` permission. This secret is used exclusively by
`actions/checkout`. GitHub does not expose repository secrets to forked or Dependabot pull requests, so the external
consumer job is skipped for those events while the normal Maven verification still runs.

For release-package verification without publishing:

```bash
mvn -Prelease clean verify
```

The `release` profile attaches source and Javadoc JARs without requiring GPG or Central credentials. Signing and
Central Portal upload live in the separate `central-release` profile and are never activated by the normal build.
See [RELEASE.md](RELEASE.md) for the manual release procedure, required secrets and safe local settings template.

Current scope: Quarkus, Hibernate ORM/Panache, REST endpoints, and integration tests. Spring support and HTML reports
are intentionally out of scope for now.
