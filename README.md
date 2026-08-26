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

# Quietly

Quietly is an open-source Maven plugin for Quarkus and Hibernate projects. It discovers JPA entities and Hibernate
filters, diagnoses whether filter tests can be generated, and creates incremental JUnit/RestAssured integration tests.

Quietly works on the current Maven module. It is module-aware, but it is not a reactor aggregator plugin.

## Quick start

Quietly 0.2.0 requires Java 17 and Maven 3.9.6 or newer.

Add the generated-test runtime support:

```xml
<dependency>
    <groupId>io.github.quietly-official</groupId>
    <artifactId>quietly-test-support</artifactId>
    <version>0.2.0</version>
    <scope>test</scope>
</dependency>
```

Add the plugin to the concrete Quarkus application/test module:

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
    <configuration>
        <basePackage>com.acme.quietlydemo</basePackage>
        <entityPackagePattern>${basePackage}.model</entityPackagePattern>
        <servicePackagePattern>${basePackage}.services.rs</servicePackagePattern>
        <serviceNamePattern>${entitySimpleName}ServiceRs</serviceNamePattern>
    </configuration>
</plugin>
```

Then run the normal Maven lifecycle:

```bash
mvn test
```

Quietly generates tests under `target/generated-test-sources/quietly`, registers that directory as a test source root,
and lets Maven compile and execute the tests in the same invocation.

For a guided first run, see [Getting started](docs/getting-started.md).

## Maven goals

| Goal | Purpose |
| --- | --- |
| [`quietly:scan`](docs/scan.md) | Inventory Hibernate filters without evaluating generation readiness. |
| [`quietly:doctor`](docs/doctor.md) | Diagnose service, field, fixture, module, and existing-test readiness. |
| [`quietly:filter-tests`](docs/filter-tests.md) | Generate or update filter integration tests. |
| [`quietly:crud-tests`](docs/crud-tests.md) | Generate experimental, convention-based CRUD smoke tests. |

## Documentation

- [Documentation index](docs/index.md)
- [Concepts and status model](docs/concepts.md)
- [Configuration reference](docs/configuration.md)
- [Multi-module projects](docs/multi-module.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Architecture](docs/architecture.md)

## Project links

- [Website](https://quietly.cloud)
- [GitHub repository](https://github.com/quietly-official/quietly)
- [Maven Central](https://central.sonatype.com/artifact/io.github.quietly-official/quietly-maven-plugin/0.2.0)
- [Consumer demo](https://github.com/quietly-official/quietly-demo)

Quietly is licensed under the [Apache License 2.0](LICENSE).
