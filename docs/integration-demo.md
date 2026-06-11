# Cross-Repository Lifecycle Integration

Quietly verifies lifecycle-bound test generation against the external
[`quietly-official/quietly-demo`](https://github.com/quietly-official/quietly-demo) consumer project.

The integration works in both directions:

- `quietly` checks out `quietly-demo`, installs the Quietly change under test, and runs the demo lifecycle;
- `quietly-demo` checks out `quietly`, installs it locally, and verifies generated-test execution.

Both repositories must remain accessible to their corresponding workflow token.

## Secret Required In `quietly`

Configure this GitHub Actions secret in `quietly-official/quietly`:

| Setting | Value |
| --- | --- |
| Secret name | `QUIETLY_INTEGRATION_DEMO_TOKEN` |
| Secret value | Fine-grained GitHub personal access token |
| Repository access | `quietly-official/quietly-demo` |
| Minimum permission | Contents: Read-only |

## Secret Required In `quietly-demo`

Configure this GitHub Actions secret in `quietly-official/quietly-demo`:

| Setting | Value |
| --- | --- |
| Secret name | `QUIETLY_REPOSITORY_TOKEN` |
| Secret value | Fine-grained GitHub personal access token |
| Repository access | `quietly-official/quietly` |
| Minimum permission | Contents: Read-only |

## Token Requirements

- Select `quietly-official` as the resource owner when creating each fine-grained PAT.
- Organization approval may be required before a token can access the selected private repository.
- Keep both tokens read-only. The workflows only need to clone source code.
- Store token values only as GitHub Actions secrets; never commit them to either repository.
- If either repository becomes inaccessible to its token, the cross-repository lifecycle integration fails before
  Maven can generate or run tests.

Repository-level secrets are configured under **Settings → Secrets and variables → Actions**. Organization secrets
must explicitly grant access to the repository that consumes them.
