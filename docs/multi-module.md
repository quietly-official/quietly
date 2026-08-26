# Multi-module Maven projects

Quietly is module-aware, but it is not a reactor aggregator plugin.

Every goal operates on the current `MavenProject`: its base directory, compiled output, compile classpath, generated-test
source directory, and report paths. Running a Quietly goal on the reactor parent does not make it visit every child.

## Supported layout

```text
root/
├── pom.xml
├── service/
│   └── pom.xml
└── app/
    ├── pom.xml    # configure Quietly here
    └── src/main/java/.../model/Customer.java
```

Configure Quietly in `app` when `app`:

- owns the Quarkus test runtime;
- runs the generated integration tests;
- contains the entity classes in its compiled output;
- can load matching REST services from its compile/runtime classpath, including dependency artifacts.

The generated sources and reports then belong to that module:

```text
app/target/generated-test-sources/quietly
app/target/quietly/filters-report.md
app/target/quietly/filters-report.json
```

The current scanner indexes the executing module's compiled output directory. A dependency on a separate `model` JAR is
not enough to make entities in that JAR discoverable, even though the classloader can load dependency classes during
resolution. Keep scanned entities in the executing module output or treat cross-artifact entity discovery as unsupported.

## Unsupported aggregator configuration

```text
root/
├── pom.xml        # Quietly configured only here; packaging=pom
├── model/
├── service/
└── app/
```

Quietly does not:

- iterate the parent's collected projects;
- merge child output directories;
- infer which child owns the Quarkus runtime;
- generate source into another module.

Running `scan` or `doctor` on a `packaging=pom` project writes `WARNING_AGGREGATOR_MODULE` and explains that the plugin
must move to a concrete module. The warning is diagnostic, not evidence that child modules were scanned. Generation
goals are also non-aggregating and must not be bound at the parent with the expectation of cross-module generation.

## Recommended configuration

In `app/pom.xml`:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>service</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>io.github.quietly-official</groupId>
    <artifactId>quietly-test-support</artifactId>
    <version>0.2.0</version>
    <scope>test</scope>
</dependency>
```

Then configure `quietly-maven-plugin` in the same `app/pom.xml` and bind `filter-tests` to
`generate-test-sources`. Entity package patterns apply to the executing module's compiled output; service patterns can
resolve classes available from its project classloader. Verify the actual result with:

```bash
mvn -pl app -am compile quietly:scan
mvn -pl app -am compile quietly:doctor
mvn -pl app -am test
```

The exact reactor selectors depend on the project. The invariant is that the Quietly goal executes against `app`, not
the parent.

## Checklist

1. Identify the module that runs `@QuarkusTest` tests.
2. Configure the plugin and `quietly-test-support` there.
3. Confirm entity classes are in that module's compiled output and service classes are loadable from its classpath.
4. Confirm the module's main classes are compiled before direct Quietly goals.
5. Inspect the report's **Module Context** section for the expected artifactId, packaging, base directory, and output.
