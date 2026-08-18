# Release Checklist

This document describes the public, reusable release process for Quietly maintainers.

## Release Preparation

Before starting a release:

- Choose the release `<version>`.
- Ensure every reactor module uses the same release version.
- Ensure the SCM tag in the POM is `v<version>`.
- Update README, documentation, and CHANGELOG for the release.
- Confirm the working tree is clean.
- Confirm the release commit is present on `main`.

## Published Coordinates

Quietly publishes these Maven coordinates:

```text
io.github.quietly-official:quietly-parent:<version>
io.github.quietly-official:quietly-core:<version>
io.github.quietly-official:quietly-test-support:<version>
io.github.quietly-official:quietly-maven-plugin:<version>
```

## Prerequisites

- Maven Central namespace ownership must be verified for `io.github.quietly-official`.
- GitHub Actions release secrets must be configured for Central Portal and GPG signing.
- The GPG public key used for signing must be published to a public keyserver.
- The release must be started manually from the GitHub Actions release workflow.

Secret names are documented intentionally, but secret values must never be committed:

- `CENTRAL_USERNAME`
- `CENTRAL_PASSWORD`
- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`

## Build Verification

Before publishing, verify the normal build, release artifacts, and plugin descriptor:

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Prelease clean verify
./mvnw -B -ntp package source:jar-no-fork javadoc:jar
./mvnw -B -ntp help:describe \
  -Dplugin=io.github.quietly-official:quietly-maven-plugin:<version> \
  -Ddetail
```

The parent publishes its POM. Each JAR module must produce:

- main JAR;
- sources JAR;
- Javadoc JAR;
- POM.

## Signing Rehearsal

Central releases must be signed. The `central-release` profile signs artifacts through Maven using the configured GPG
key and passphrase supplied by the maintainer environment or GitHub Actions secrets.

A signing rehearsal may be run without deployment:

```bash
./mvnw -B -ntp -Prelease,central-release -DskipTests verify
```

This command must not deploy or upload anything. It must generate `.asc` files beside each publishable artifact.

List generated signatures:

```bash
find . -path "*/target/*" -name "*.asc" | sort
```

Verify at least one signature per module with `gpg --verify` before uploading a release.

## Tag

Create and push the release tag only after build verification and signing rehearsal pass:

```bash
git switch main
git pull --ff-only origin main
git tag -a v<version> -m "Release <version>"
git push origin v<version>
```

Do not move a tag for a release that has already been published.

## GitHub Actions Release Workflow

Publishing is manual. The release workflow must not publish on every push.

Use `workflow_dispatch` on tag `v<version>`:

1. Run the workflow with Central upload disabled.
2. Run the workflow again with Central upload enabled.

The workflow must keep `autoPublish=false`. Upload and publish must remain separate maintainer actions.

## Central Portal

The `central-release` profile uses the Central Publishing Maven Plugin with:

```text
publishingServerId=central
autoPublish=false
waitUntil=VALIDATED
```

After upload:

1. Wait for `VALIDATED`.
2. Verify coordinates and artifacts.
3. Verify metadata, sources, Javadocs, and signatures.
4. Publish manually from Central Portal.
5. Wait for `PUBLISHED`.

## Post-release Verification

After Central Portal publication, validate a consumer build using the released version from Maven Central.

Use an isolated Maven local repository so artifacts cannot be resolved from the maintainer cache:

```bash
rm -rf /tmp/quietly-central-check
cd ../quietly-demo
mvn -U -B -ntp \
  -Dquietly.version=<version> \
  -Dmaven.repo.local=/tmp/quietly-central-check \
  clean test
```

Success requires:

- artifacts resolved from Maven Central;
- generated tests written under `target/generated-test-sources/quietly`;
- `CustomerFiltersTest` compiled;
- generated tests executed;
- green build.

## Release Complete

A release is complete when:

- the release commit is on `main`;
- tag `v<version>` exists;
- CI is green;
- Central deployment is `PUBLISHED`;
- isolated consumer verification is green.
