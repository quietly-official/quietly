# Project

Quietly is an open-source Maven plugin for Quarkus and Hibernate projects. It discovers compiled JPA entities and
Hibernate filters, diagnoses filter-test readiness, and generates incremental JUnit/RestAssured integration-test source.

The repository is a Java 17, Maven 3.9.6 multi-module reactor. The build uses Quarkus platform 3.11.0 as its verified
baseline. Do not broaden compatibility claims without evidence from builds/tests.

# Repository structure

- `quietly-core`: entity scanning and Hibernate `@Filter`/`@FilterDef` metadata extraction. Put low-level discovery of
  compiled entity/filter facts here.
- `quietly-test-support`: runtime base classes and helpers imported by generated consumer tests. Keep Maven-plugin
  implementation concerns out of this artifact.
- `quietly-maven-plugin`: Mojo entry points, module context, discovery orchestration, service/fixture/field resolvers,
  planning, AST rendering, incremental file updates, and Markdown/JSON reporting.
- `quietly-parent` (`pom.xml`): reactor, dependency management, shared build metadata, and release profiles.
- `quietly-demo`: external repository at `quietly-official/quietly-demo`. It is not a reactor module. Main CI checks it
  out separately to verify lifecycle-bound generation, compilation, and execution in a real Quarkus consumer.
- `docs/index.md`: canonical documentation entry point.
- `RELEASE.md`: release process source of truth.

# Architecture

The filter pipeline is:

```text
MavenProject
  → ModuleContext
  → ProjectDiscovery
  → DiscoveredProject
  → ServiceResolver / FixtureResolver / FieldResolver
  → DoctorPlanner
  → GenerationPlan
  → doctor / filter-tests
  → QuietlyReport / generated tests
```

Responsibility boundaries:

- `ProjectDiscovery` coordinates discovery; `quietly-core` scans compiled entity/filter metadata.
- Resolvers return explicit service, field, and fixture outcomes.
- `DoctorPlanner` decides filter readiness and creates `GenerationPlan` entries.
- `GenerationPlan` is the shared source of truth for doctor and filter generation service/field decisions.
- `FilterTestAstBuilder` renders already-resolved decisions. Do not move discovery or readiness decisions into AST code.
- `FilterTestsCodeGenerator` consumes the plan and owns incremental filter-test file updates/report events.
- `QuietlyReport` renders outcomes; reporting must not create a second readiness model.
- CRUD generation currently has a separate, experimental convention-based generator. Do not imply that its fixed
  endpoint assumptions are inferred through the filter `GenerationPlan`.

# Development rules

- Keep discovery independent from rendering.
- Do not duplicate service/field readiness decisions outside `DoctorPlanner`/`GenerationPlan` for filter tests.
- When doctor and filter generation apply to the same filter, they must agree on service and field readiness.
- Preserve deterministic generation. `STRICT` field resolution is the default; fuzzy matching must remain explicit and
  must refuse ambiguity.
- Preserve idempotency: repeated generation must not duplicate recognized methods.
- Preserve custom methods and report stale generated methods rather than deleting them implicitly.
- Do not invent SQL fixtures, payloads, endpoint behavior, business data, database setup, or security configuration.
- Preserve current-module behavior. Quietly operates on the current `MavenProject` and must not become a reactor
  aggregator as an incidental change.
- Entity discovery currently indexes the executing module's compiled output. Do not claim or implement dependency-JAR
  traversal as a side effect of unrelated work.
- Keep public report/status/configuration behavior backward-compatible unless the change is explicitly approved and
  documented.
- Keep CRUD explicitly experimental until its capability model and compatibility are intentionally expanded.
- Make the smallest scoped change; avoid unrelated refactors across module boundaries.

# Build

Full repository build:

```bash
./mvnw -B -ntp clean verify
```

Useful targeted builds:

```bash
./mvnw -B -ntp -pl quietly-core test
./mvnw -B -ntp -pl quietly-test-support -am test
./mvnw -B -ntp -pl quietly-maven-plugin -am test
```

Build release-shaped artifacts without publishing:

```bash
./mvnw -B -ntp -Prelease clean verify
```

Do not invoke deployment/release profiles merely to validate ordinary changes.

# Tests

- `quietly-core` tests scanning, package selection, filter metadata, and normalization.
- `quietly-test-support` tests runtime helpers, SQL loading, and response parsing.
- `quietly-maven-plugin` tests module context/configuration, discovery/resolvers, planning, field resolution, report
  semantics, AST builders, idempotent generators, dry-run, stale markers, and source-root registration.
- Main CI runs `./mvnw -B -ntp clean verify` and then checks out the external `quietly-demo` to prove that a normal Maven
  lifecycle generates, compiles, and executes `CustomerFiltersTest`.

For local external-consumer validation, install the reactor change without publishing, then run the separate demo:

```bash
./mvnw -B -ntp install -DskipTests
```

From a sibling `quietly-demo` checkout:

```bash
mvn -B -ntp clean test -Dquietly.version=0.2.0
```

When changing public behavior, add focused unit coverage in the owning module and run the full reactor. Changes to
lifecycle registration or generated imports/source should also be validated against the demo flow.

# Before submitting changes

Always run:

```bash
./mvnw -B -ntp clean verify
git diff --check
```

Also verify, as applicable:

- generated source remains syntactically valid and idempotent;
- doctor and filter generation produce consistent plan decisions;
- report Markdown/JSON statuses and logical counts remain compatible;
- no secret, credential, local absolute path, build output, or temporary fixture was added;
- the external demo flow still passes when lifecycle or generated-test behavior changed;
- the plugin descriptor matches intended parameters after Mojo changes:

```bash
./mvnw -B -ntp help:describe \
  -Dplugin=io.github.quietly-official:quietly-maven-plugin:0.2.0 \
  -Ddetail
```

# Documentation

Use `docs/index.md` as the canonical user-documentation entry point.

When public behavior changes, update the relevant goal/concept/troubleshooting page. When a Mojo parameter, default, or
goal scope changes, update `docs/configuration.md` and `llms-full.txt`, then compare both with `help:describe -Ddetail`.
Keep `llms.txt` compact; it is a map, not a duplicate manual.

The `https://quietly.cloud/docs/...` links in `llms.txt` are target canonical routes and are not yet asserted to be
deployed. Do not state that those pages are live until verified.

# Release

Releases are maintainer-controlled, manually initiated, signed, uploaded to Maven Central for manual publication, and
verified with an isolated consumer build. `autoPublish` must remain false. Follow `RELEASE.md` and the release workflow;
do not reproduce or improvise the full release sequence in unrelated changes.

Never start a release, upload artifacts, publish a Central deployment, create/move a tag, or push release changes without
explicit user/maintainer authorization.

# Compatibility

Verified repository baselines:

- Java 17 (`maven.compiler.release`);
- Maven 3.9.6 (plugin prerequisite/build baseline);
- Quarkus platform 3.11.0 in dependency management and the external demo;
- current public coordinates/version: `io.github.quietly-official`, `0.2.0`.

Quietly targets Quarkus, Hibernate ORM/Panache, REST endpoints, JUnit 5, and RestAssured. Spring support is not present.
Runtime success still depends on the consumer's database, schema, fixture data, endpoint behavior, and security setup.

# Generated code

Default generated source root:

```text
target/generated-test-sources/quietly
```

Filter methods use:

```text
@quietly-generated filter="<filter-name>"
```

CRUD methods use:

```text
@quietly-generated crud="list"
@quietly-generated crud="get-missing"
```

Generation adds missing methods/markers and reports stale generated methods. It must not silently delete stale or custom
consumer code. Existing invalid/unparseable generated targets should be reported and left for explicit repair. Generated
files are consumer build output by default; consumers own their fixture data and runtime environment.

# Do not

Unless explicitly requested and authorized, do not:

- release, deploy, publish, push, or create/move tags;
- change Maven coordinates, public versions, SCM tags, or artifact names;
- modify release credentials or commit secrets;
- turn Quietly into a reactor aggregator or scan unrelated dependency JARs;
- invent fixtures, payloads, endpoints, business data, or runtime configuration;
- delete stale/custom generated methods or overwrite invalid existing files destructively;
- change public status/report semantics or defaults as part of an unrelated refactor;
- present experimental CRUD generation as production-complete;
- perform broad unrelated refactors across modules.
