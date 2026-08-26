# Getting started

This guide takes a compiled Quarkus application from no Quietly configuration to a generated filter test that Maven
can compile and run.

## Prerequisites

- Java 17 or newer.
- Maven 3.9.6 or newer.
- A Quarkus application using Hibernate ORM or Panache.
- Compiled `@Entity` classes with Hibernate `@Filter`/`@FilterDef` metadata.
- A REST service that can be used with Quarkus `@TestHTTPEndpoint`.

Quietly scans compiled application classes. Run at least the `compile` phase before invoking `scan`, `doctor`, or a
generation goal directly.

## 1. Add test support

Generated tests use classes from `quietly-test-support`. Add it with test scope:

```xml
<dependency>
    <groupId>io.github.quietly-official</groupId>
    <artifactId>quietly-test-support</artifactId>
    <version>0.2.0</version>
    <scope>test</scope>
</dependency>
```

Your project must also provide the normal Quarkus test dependencies used by the generated tests, including
`quarkus-junit5`. The [quietly-demo](https://github.com/quietly-official/quietly-demo) is the reference consumer.

## 2. Configure the plugin

The example below uses the real demo package layout:

```text
com.acme.quietlydemo.model.Customer
com.acme.quietlydemo.services.rs.CustomerServiceRs
```

Add the plugin to the module that owns those compiled classes and the Quarkus test runtime:

```xml
<build>
    <plugins>
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
                <fieldResolutionMode>STRICT</fieldResolutionMode>
            </configuration>
        </plugin>
    </plugins>
</build>
```

See [Configuration](configuration.md) before changing failure behavior or enabling fuzzy field matching.

## 3. Provide the test prerequisites

For the generated setup method, the current filter-test template expects the entity to expose a public `TABLE_NAME`
constant and a matching SQL fixture:

```text
src/test/resources/sql/<TABLE_NAME>.sql
```

For the demo entity, `TABLE_NAME` is `customer`, so the fixture is:

```text
src/test/resources/sql/customer.sql
```

The REST service must match the configured package/name convention and accept the filter name used by the generated
test. Quietly diagnoses these conventions; it does not invent missing services, fixtures, endpoints, or business data.

## 4. Scan the compiled project

```bash
mvn compile quietly:scan
```

Inspect:

```text
target/quietly/filters-report.md
target/quietly/filters-report.json
```

`scan` inventories discovered filters. It does not decide whether tests are generatable.

## 5. Run diagnostics

```bash
mvn compile quietly:doctor
```

Review service and field resolution, SQL fixture diagnostics, existing generated methods, and stale generated methods.
Resolve blocking service/field issues before generation. See [`doctor`](doctor.md) for status details.

## 6. Generate and run tests

The lifecycle binding above runs `filter-tests` during `generate-test-sources`. Use one Maven invocation:

```bash
mvn test
```

Quietly writes Java sources under:

```text
target/generated-test-sources/quietly
```

The Mojo registers that directory with the current `MavenProject`, after which Maven compiles and executes the tests.
Surefire—not Quietly—is the source of truth for runtime pass/fail.

Do not rely on two separate commands such as `mvn compile quietly:filter-tests` followed by `mvn test`. The second Maven
process does not retain the generated test source root registered in the first process. Lifecycle binding avoids that
problem.

## 7. Re-run safely

Generation is incremental. Quietly adds missing methods, recognizes matching methods, and reports stale generated
methods without deleting them. Review the generation report after each structural change:

```text
target/quietly/filters-report.md
target/quietly/filters-report.json
```

Next: [Concepts](concepts.md), [filter-tests](filter-tests.md), and [Troubleshooting](troubleshooting.md).
