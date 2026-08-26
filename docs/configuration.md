# Configuration reference

This reference is based on the 0.2.0 Mojo annotations/Javadocs and was verified with:

```bash
./mvnw -B -ntp help:describe \
  -Dplugin=io.github.quietly-official:quietly-maven-plugin:0.2.0 \
  -Ddetail
```

All parameters below are optional user parameters. Maven injects an additional required, read-only `MavenProject`
internally; it is not user-configurable.

## Parameter matrix

| Parameter | Type | Effective default | Required | Goals | Meaning |
| --- | --- | --- | --- | --- | --- |
| `basePackage` | `String` | Derived per entity | No | all | Root used when resolving package/name patterns. |
| `entityPackagePattern` | `String` | No package restriction | No | all | Selects entity packages during discovery. |
| `servicePackagePattern` | `String` | Convention derived from entity package | No | all | Package containing the expected REST service. |
| `serviceNamePattern` | `String` | `${entitySimpleName}ServiceRs` | No | all | Expected REST service simple name. |
| `testOutputDirectory` | `File` | `target/generated-test-sources/quietly` | No | all | Generated-test directory; inspected by non-generation goals. |
| `reportFile` | `File` | Filter: `target/quietly/filters-report.md`; CRUD: `target/quietly/crud-report.md` | No | all | Markdown report path; JSON is written beside it. |
| `disabledByDefault` | `boolean` | `false` | No | `filter-tests`, `crud-tests` | Adds `@Disabled` to newly generated tests. |
| `failOnMissingService` | `boolean` | `true` | No | `filter-tests`, `crud-tests` | Fails generation when the service convention does not resolve. |
| `failOnUnresolvedField` | `boolean` | `true` | No | `filter-tests` | Fails generation when a filter field is unresolved. |
| `dryRun` | `boolean` | `false` | No | `filter-tests`, `crud-tests` | Writes reports/logs without writing or registering test sources. |
| `fieldResolutionMode` | `FieldResolutionMode` | `STRICT` | No | `scan`, `doctor`, `filter-tests` | Controls matching in doctor/filter generation; scan records the selected mode but does not resolve readiness. |
| `failOnProblems` | `boolean` | `false` | No | `doctor` | Fails Maven after doctor writes a report containing problems. |

## Package and naming parameters

### `basePackage`

Use it as the stable root for package patterns:

```xml
<basePackage>com.acme.quietlydemo</basePackage>
```

If omitted, Quietly derives a legacy root per entity: it removes a trailing `.model`, otherwise it uses the entity
package. `basePackage` does not itself restrict scanning.

### `entityPackagePattern`

Exact package:

```xml
<entityPackagePattern>${basePackage}.model</entityPackagePattern>
```

Package and subpackages:

```xml
<entityPackagePattern>${basePackage}.model.*</entityPackagePattern>
```

Without this parameter, all compiled application `@Entity` classes visible in the current module output are eligible.
`${basePackage}` requires `basePackage` to be configured; otherwise Quietly rejects the configuration. An incorrect
pattern commonly produces zero discovered entities.

### `servicePackagePattern`

```xml
<servicePackagePattern>${basePackage}.services.rs</servicePackagePattern>
```

Without it, an entity in a package ending in `.model` maps to the corresponding `.services.rs` package. Other entity
packages map to a nested `.services.rs` package. Configure this explicitly when the project does not follow that
convention.

### `serviceNamePattern`

```xml
<serviceNamePattern>${entitySimpleName}ServiceRs</serviceNamePattern>
```

`${entitySimpleName}` is replaced with the entity class name. The default uses the same pattern. Quietly loads the
resulting class; it does not search for arbitrary REST resources.

## Output parameters

### `testOutputDirectory`

```xml
<testOutputDirectory>${project.build.directory}/generated-test-sources/quietly</testOutputDirectory>
```

Relative paths resolve from the current module base directory. Generation goals register this directory only when
`dryRun=false`. Keep the lifecycle binding in the same Maven invocation that compiles tests.

### `reportFile`

```xml
<reportFile>${project.build.directory}/quietly/filters-report.md</reportFile>
```

Relative paths resolve from the current module. A `.json` sibling is always derived from the Markdown path. If the
configured name does not end in `.md`, Quietly appends `.json` to that name for the JSON report. CRUD uses a separate
default report but will use a configured `reportFile` verbatim.

## Generation controls

### `disabledByDefault`

```xml
<disabledByDefault>true</disabledByDefault>
```

Adds `@Disabled` to generated methods. This is useful for onboarding, but it also means Maven will not execute those
tests until they are enabled. The default is active tests (`false`).

### `failOnMissingService`

```xml
<failOnMissingService>false</failOnMissingService>
```

With the default `true`, generation fails when a matching REST service is absent. With `false`, the entity/filter is
skipped and reported. Relaxing this setting can make a build green while leaving generation incomplete.

### `failOnUnresolvedField`

```xml
<failOnUnresolvedField>false</failOnUnresolvedField>
```

Only `filter-tests` uses this parameter. With `false`, unresolved filters are skipped and reported instead of failing
the build. Prefer fixing metadata while keeping the default `true`.

### `dryRun`

```xml
<dryRun>true</dryRun>
```

Generation goals still discover and report but do not write test files or register the test source directory. A dry-run
therefore cannot prove compilation or runtime success. For filter generation, candidate methods use
`WOULD_GENERATE`; CRUD report event wording is currently less specific, so use the absence of written source plus the
logs/report details as the authority.

## Diagnostic controls

### `fieldResolutionMode`

```xml
<fieldResolutionMode>STRICT</fieldResolutionMode>
```

- `STRICT` requires one exact field name across the entity class hierarchy.
- `FUZZY` uses the closest name only when there is a unique best match and emits a warning.

Ambiguous exact or fuzzy matches remain unresolved. `FUZZY` is intended for diagnosing legacy naming, not as a default
for new projects.

### `failOnProblems`

```xml
<failOnProblems>true</failOnProblems>
```

Only `doctor` uses this parameter. Doctor writes its Markdown/JSON reports first, then fails Maven if the report has
problem statuses. Use it when diagnostic readiness should gate CI.

## Complete example

```xml
<configuration>
    <basePackage>com.acme.quietlydemo</basePackage>
    <entityPackagePattern>${basePackage}.model</entityPackagePattern>
    <servicePackagePattern>${basePackage}.services.rs</servicePackagePattern>
    <serviceNamePattern>${entitySimpleName}ServiceRs</serviceNamePattern>
    <testOutputDirectory>${project.build.directory}/generated-test-sources/quietly</testOutputDirectory>
    <reportFile>${project.build.directory}/quietly/filters-report.md</reportFile>
    <disabledByDefault>false</disabledByDefault>
    <failOnMissingService>true</failOnMissingService>
    <failOnUnresolvedField>true</failOnUnresolvedField>
    <fieldResolutionMode>STRICT</fieldResolutionMode>
    <dryRun>false</dryRun>
</configuration>
```

Do not copy every option without a reason. Start with explicit package conventions and keep safety defaults enabled.
