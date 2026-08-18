# Release Checklist

This document describes the public release process for Quietly maintainers.

## Release Version

Current release: `0.2.0`

Expected tag: `v0.2.0`

Published coordinates:

```text
io.github.quietly-official:quietly-core:0.2.0
io.github.quietly-official:quietly-test-support:0.2.0
io.github.quietly-official:quietly-maven-plugin:0.2.0
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
./mvnw -B -ntp help:describe -Dplugin=io.github.quietly-official:quietly-maven-plugin:0.2.0 -Ddetail
```

The release build must produce the main JAR, sources JAR, Javadoc JAR, and POM for each published module.

## Signing Requirement

Central releases must be signed. The `central-release` profile signs artifacts through Maven using the configured GPG
key and passphrase supplied by the maintainer environment or GitHub Actions secrets.

A signing rehearsal may be run without deployment:

```bash
./mvnw -B -ntp -Prelease,central-release -DskipTests verify
```

Verify that `.asc` files are generated beside each publishable artifact before uploading a release.

## GitHub Actions Release Workflow

Publishing is manual. The release workflow must not publish on every push.

Use the workflow dispatch input to choose whether the run only validates release artifacts or uploads a signed bundle to
Central Portal.

The workflow must keep `autoPublish=false` so that Central publication remains a deliberate maintainer action.

## Central Portal

The `central-release` profile uses the Central Publishing Maven Plugin with:

```text
publishingServerId=central
autoPublish=false
waitUntil=VALIDATED
```

After upload, inspect the deployment in Central Portal. Publish only after validation succeeds and artifacts, metadata,
sources, Javadocs, and signatures look correct.

## Post-release Verification

After Central Portal publication, validate a consumer build using the released version from Maven Central.

The demo project should generate `target/generated-test-sources/quietly`, compile `CustomerFiltersTest`, and run the
generated tests with zero failures and zero errors.
