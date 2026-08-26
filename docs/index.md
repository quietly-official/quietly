# Quietly documentation

Quietly is a Maven plugin for Quarkus and Hibernate projects. It discovers project metadata, diagnoses filter-test
readiness, and incrementally generates JUnit/RestAssured tests where its conventions can resolve the required inputs.

## Start here

1. [Getting started](getting-started.md) — install Quietly and reach the first generated test.
2. [Concepts](concepts.md) — understand discovery, planning, generation, and report statuses.
3. [Configuration](configuration.md) — reference every user-configurable plugin parameter.
4. [Multi-module projects](multi-module.md) — configure Quietly in the correct Maven module.

## Goal reference

- [`quietly:scan`](scan.md)
- [`quietly:doctor`](doctor.md)
- [`quietly:filter-tests`](filter-tests.md)
- [`quietly:crud-tests`](crud-tests.md)

## Operations and internals

- [Troubleshooting](troubleshooting.md)
- [Architecture](architecture.md)
- [Consumer lifecycle integration](integration-demo.md)

## Current scope

Quietly targets Quarkus, Hibernate ORM/Panache, REST endpoints, and integration tests. CRUD generation is experimental
and convention-based. Quietly does not aggregate child modules, measure test execution, invent project-specific data,
or replace manually designed tests.
