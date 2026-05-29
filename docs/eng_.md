<p align="center">
  <img src="img/quietly_hd_dark_theme.png" style="width: 500px; border: 1px solid #ccc;" />
</p>

##### [BACK](../README.md)

## What Quietly Is

Quietly is a Maven plugin for Quarkus/Hibernate projects. It generates JUnit/RestAssured tests for REST filters by reading Hibernate filter metadata from JPA entities.

It currently:

- scans `@Entity` classes
- reads `@Filter` and `@FilterDef`
- extracts filter name, field, parameter and type
- resolves the expected REST service
- creates or updates `*FiltersTest` classes
- adds only missing tests
- writes a Markdown generation report

Quietly is intentionally focused on Quarkus, Hibernate ORM/Panache, REST endpoints and integration tests.

## Local Install

From the Quietly repository:

```bash
mvn clean install
```

This installs:

```text
ua.quietly:quietly-core:1.0
ua.quietly:quietly-test-support:1.0
ua.quietly:quietly-maven-plugin:1.0
```

## Target Project Setup

Add the test runtime support:

```xml
<dependency>
    <groupId>ua.quietly</groupId>
    <artifactId>quietly-test-support</artifactId>
    <version>1.0</version>
    <scope>test</scope>
</dependency>
```

Add the Maven plugin:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>ua.quietly</groupId>
            <artifactId>quietly-maven-plugin</artifactId>
            <version>1.0</version>
            <configuration>
                <basePackage>com.acme</basePackage>
                <entityPackagePattern>${basePackage}.entities</entityPackagePattern>
                <servicePackagePattern>${basePackage}.services.rs</servicePackagePattern>
                <serviceNamePattern>${entitySimpleName}ServiceRs</serviceNamePattern>
                <testOutputDirectory>${project.build.directory}/generated-test-sources/quietly</testOutputDirectory>
                <reportFile>${project.build.directory}/quietly/filters-report.md</reportFile>
                <disabledByDefault>false</disabledByDefault>
                <failOnMissingService>false</failOnMissingService>
                <failOnUnresolvedField>false</failOnUnresolvedField>
                <fieldResolutionMode>FUZZY</fieldResolutionMode>
                <dryRun>false</dryRun>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Commands

Scan filters and write reports without generating tests:

```bash
mvn compile quietly:scan
```

Run project diagnostics:

```bash
mvn compile quietly:doctor
```

Generate or update tests:

```bash
mvn compile quietly:filter-tests
```

Run without writing test files:

```xml
<dryRun>true</dryRun>
```

then:

```bash
mvn compile quietly:filter-tests
```

With `dryRun=true`, Quietly scans the project and writes the report, but does not create or modify test files.

### Goal Differences

| Goal | Writes tests | Purpose |
| --- | --- | --- |
| `quietly:scan` | No | Filter inventory, Markdown/JSON report |
| `quietly:doctor` | No | Diagnostics for services, fields, SQL fixtures and existing generated tests |
| `quietly:filter-tests` | Yes, unless `dryRun=true` | Incremental test generation |

## Generated Output

For this entity:

```java
@Entity
@FilterDef(name = "obj.status", parameters = @ParamDef(name = "status", type = String.class))
@Filter(name = "obj.status", condition = "status = :status")
public class Customer { ... }
```

Quietly generates a class similar to:

```java
@QuarkusTest
@TestHTTPEndpoint(CustomerServiceRs.class)
public class CustomerFiltersTest extends FilterTestBase {

    @Inject
    private EntityManager em;

    @BeforeEach
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void beforeEach() throws IOException {
        Customer.deleteAll();
        Customer.getEntityManager()
                .createNativeQuery(SqlUtils.loadSqlFromClasspath("sql/" + Customer.TABLE_NAME + ".sql"))
                .executeUpdate();
    }

    /**
     * @quietly-generated filter="obj.status"
     */
    @Test
    @TestSecurity(user = "test")
    public void obj_status_filter_test() {
        String paramValue = test_entity(em, Customer.class, "status IS NOT NULL").status;
        Assertions.assertNotNull(paramValue);
        assert_filter_works("obj.status", paramValue, Customer.class);
    }
}
```

Generated methods include a `@quietly-generated` Javadoc marker. If a matching method already exists without the marker, Quietly adds it on the next run.

## Supported Filter Names

Quietly supports simple names:

```text
obj.status
like.name
from.createdAt
to.createdAt
nil.deletedAt
not_nil.customerId
```

and namespaced names:

```text
customer.obj.fornitore_uuid
```

For namespaced filters, the last segment is treated as the entity field:

```text
prefix = customer.obj
field  = fornitore_uuid
```

The generated method name is sanitized into a valid Java identifier:

```java
customer_obj_fornitore_uuid_filter_test()
```

## Configuration

| Parameter | Default | Meaning |
| --- | --- | --- |
| `basePackage` | derived from the entity package | Base package used by patterns |
| `entityPackagePattern` | legacy `.model` convention | Entity package pattern |
| `servicePackagePattern` | `.services.rs` | REST service package pattern |
| `serviceNamePattern` | `${entitySimpleName}ServiceRs` | REST service name pattern |
| `testOutputDirectory` | `src/test/java` | Test generation directory |
| `reportFile` | `target/quietly/filters-report.md` | Markdown report path |
| `disabledByDefault` | `false` | Adds `@Disabled` to generated tests when `true` |
| `failOnMissingService` | `true` | Fails when a REST service is missing |
| `failOnUnresolvedField` | `true` | Fails when a filter field cannot be resolved |
| `fieldResolutionMode` | `STRICT` | `STRICT` or `FUZZY` |
| `dryRun` | `false` | Does not write test files when `true` |

Supported placeholders:

```text
${basePackage}
${entitySimpleName}
```

## STRICT And FUZZY

`STRICT` is the default. It only accepts exact, deterministic field matches.

`FUZZY` tries to find the closest field, but it refuses ambiguous matches. If a field cannot be resolved:

- `failOnUnresolvedField=true` fails the build
- `failOnUnresolvedField=false` skips that filter and records it in the report

## Report

Quietly always writes a Markdown report:

```text
target/quietly/filters-report.md
```

Example:

```markdown
| Entity | Filter | Status | Details |
| --- | --- | --- | --- |
| Customer | obj.status | GENERATED | Generated test method. |
| Worker | obj.data_riferimento | SKIPPED_UNRESOLVED_FIELD | ... |
| UtenteFattura | * | SKIPPED_MISSING_SERVICE | ... |
```

Main statuses:

| Status | Meaning |
| --- | --- |
| `GENERATED` | Test method generated |
| `DISCOVERED` | Filter discovered by `quietly:scan` |
| `OK` | Positive diagnostic from `quietly:doctor` |
| `EXISTING` | Method already exists |
| `UPDATED_MARKER` | Existing method updated with the Quietly marker |
| `STALE_GENERATED_TEST` | Generated method references a filter that no longer exists |
| `SKIPPED_MISSING_SERVICE` | REST service not found |
| `SKIPPED_UNRESOLVED_FIELD` | Filter field not resolved |
| `MISSING_SQL_FIXTURE` | Expected SQL fixture not found |
| `MISSING_TABLE_NAME` | Entity does not expose public `TABLE_NAME` |
| `SKIPPED_INVALID_EXISTING_FILE` | Existing file does not contain the expected test class |

Quietly also writes a JSON report next to the Markdown report:

```text
target/quietly/filters-report.json
```

The JSON report includes a CI/script-friendly summary:

```json
{
  "summary": {
    "totalFilters": 10,
    "coveredFilters": 8,
    "coveragePercent": 80.0
  }
}
```

## Target Project Requirements

Quietly currently expects:

- Quarkus test runtime
- Hibernate ORM / Panache
- entities annotated with `@Entity`
- Hibernate filters with `@Filter` and `@FilterDef`
- REST service compatible with `@TestHTTPEndpoint`
- entities exposing `deleteAll()` and `getEntityManager()`
- a `TABLE_NAME` constant
- SQL fixtures under `src/test/resources/sql/<TABLE_NAME>.sql`
- REST endpoint query parameters matching filter names

## Idempotency

Quietly is designed to be run repeatedly:

- it does not duplicate existing methods
- it does not delete custom code
- it adds missing methods
- it adds `beforeEach()` when missing
- it adds the `@quietly-generated` marker to recognized existing methods
- it reports generated methods for removed filters as `STALE_GENERATED_TEST`

## Troubleshooting

### Nothing was generated

Check:

```text
target/quietly/filters-report.md
```

The most common causes are `SKIPPED_MISSING_SERVICE` and `SKIPPED_UNRESOLVED_FIELD`.

### REST service not found

Typical message:

```text
Entity Customer has Hibernate filters but no matching REST service was found.
Expected com.acme.services.rs.CustomerServiceRs.
```

Fix `servicePackagePattern` / `serviceNamePattern`, or use:

```xml
<failOnMissingService>false</failOnMissingService>
```

### Filter field not resolved

With `STRICT`, the field must exist exactly on the entity.

For onboarding legacy projects, use:

```xml
<fieldResolutionMode>FUZZY</fieldResolutionMode>
<failOnUnresolvedField>false</failOnUnresolvedField>
```

### I only want to see what would happen

Use:

```xml
<dryRun>true</dryRun>
```

## Architecture

| Module | Responsibility |
| --- | --- |
| `quietly-core` | Entity scanning and Hibernate metadata extraction |
| `quietly-maven-plugin` | Maven Mojo, configuration, AST generation, report |
| `quietly-test-support` | Runtime base class and helpers for generated tests |

## Current Scope

Quietly is focused on Quarkus/Hibernate/Panache. It does not currently include Spring support, HTML reports, or separate goals such as `doctor`.
