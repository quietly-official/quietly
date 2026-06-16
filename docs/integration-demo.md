# Consumer Lifecycle Integration

Quietly verifies lifecycle-bound test generation against the external
[`quietly-official/quietly-demo`](https://github.com/quietly-official/quietly-demo) consumer project.

The public demo gives Quietly a real consumer build:

- `quietly` checks out `quietly-demo`, installs the Quietly change under test, and runs the demo lifecycle.
- `quietly-demo` can also be run directly against the latest published version from Maven Central.

The integration intentionally exercises the normal Maven lifecycle instead of invoking a generated class directly. This
protects the documented `generate-test-sources` setup: generated test sources must be created, registered, compiled and
executed by a plain Maven test run.

For a local smoke test after a release:

```bash
git clone https://github.com/quietly-official/quietly-demo.git
cd quietly-demo
mvn -U -Dmaven.repo.local=/tmp/quietly-central-check clean test
```

When validating an unpublished Quietly change, install the local reactor first and override the demo version:

```bash
cd quietly
./mvnw -B -ntp install -DskipTests

cd ../quietly-demo
mvn -B -ntp clean test -Dquietly.version=<local-version>
```
