<p align="center">
  <img src="docs/img/quietly_logo_upscaled.png" style="width: 500px; border: 1px solid #ccc;" />
</p>

<h2 align="center"><i>Tests that grow with your code - incremental, safe, automated.</i></h2>

<p align="center">
<a href="https://central.sonatype.com/artifact/io.github.quietly-official/quietly-maven-plugin">
  <img src="https://img.shields.io/maven-central/v/io.github.quietly-official/quietly-maven-plugin?label=Maven%20Central&color=informational" alt="Maven Central" />
</a>

  <a href="https://github.com/quietly-official/quietly/actions/workflows/build.yml">
    <img src="https://github.com/quietly-official/quietly/actions/workflows/build.yml/badge.svg" alt="Build" />
  </a>
</p>

Quietly is a Maven plugin for Quarkus/Hibernate projects. It scans JPA entities, reads Hibernate `@Filter` and
`@FilterDef` metadata, diagnoses missing test prerequisites, and generates JUnit/RestAssured integration tests for REST
filters.

Current Maven coordinates use groupId `io.github.quietly-official` and version `0.1.0-beta.1`.

Start here:

- [English documentation](docs/eng_.md)
- [Documentazione italiana](docs/it_.md)

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

Current scope: Quarkus, Hibernate ORM/Panache, REST endpoints, and integration tests. Spring support and HTML reports
are intentionally out of scope for now.
