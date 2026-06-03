<p align="center">
  <img src="docs/img/quietly_hd_dark_theme.png" style="width: 500px; border: 1px solid #ccc;" />
</p>

<h2 align="center"><i>Tests that grow with your code - incremental, safe, automated.</i></h2>

<p align="center">
  <a href="https://github.com/Quietly-Official/quietly-prototype/actions/workflows/build.yml">
    <img src="https://github.com/Quietly-Official/quietly-prototype/actions/workflows/build.yml/badge.svg" alt="Build" />
  </a>
</p>

Quietly is a Maven plugin for Quarkus/Hibernate projects. It scans JPA entities, reads Hibernate `@Filter` and `@FilterDef` metadata, diagnoses missing test prerequisites, and generates JUnit/RestAssured integration tests for REST filters.

Start here:

- [English documentation](docs/eng_.md)
- [Documentazione italiana](docs/it_.md)

Quick local install:

```bash
mvn clean install
```

Quick usage in another project:

```bash
mvn compile quietly:scan
mvn compile quietly:doctor
mvn compile quietly:filter-tests
```

Recommended first run:

1. Run `quietly:scan` to inventory filters.
2. Run `quietly:doctor` to find missing services, unresolved fields, fixtures, and stale generated tests.
3. Run `quietly:filter-tests` once the report looks sane.

Generated tests are active by default, idempotent, and reported in both Markdown and JSON:

```text
target/quietly/filters-report.md
target/quietly/filters-report.json
```

Current scope: Quarkus, Hibernate ORM/Panache, REST endpoints, and integration tests. Spring support and HTML reports are intentionally out of scope for now.
