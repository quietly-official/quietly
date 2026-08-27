# Architecture

Quietly separates project discovery, analysis, planning, and rendering so diagnostics and filter generation use the
same project facts and decisions.

```text
MavenProject
    ↓
ModuleContext
    ↓
ProjectDiscovery
    ↓
DiscoveredProject
    ↓
ServiceResolver / FixtureResolver / FieldResolver
    ↓
DoctorPlanner
    ↓
GenerationPlan
    ↓
doctor / filter-tests
    ↓
QuietlyReport / generated tests
```

## Module context

`ModuleContext` captures the current Maven artifact, packaging, base directory, build/output directories, generated-test
directory, and reactor module count. It makes reports explicit about where Quietly ran. It does not turn Quietly into an
aggregator: every operation still belongs to one `MavenProject`.

## Discovery

`ProjectDiscovery` asks `quietly-core` to scan compiled application output using the current module's compile classpath.
The result is a `DiscoveredProject`: immutable module context plus discovered entity/filter metadata.

Filter goals discover entities that carry Hibernate filters. Experimental CRUD generation uses the all-entity discovery
mode.

Discovery answers **what exists**. It does not decide whether source should be generated.

## Resolution and analysis

Resolvers turn conventions into explicit results:

- `ServiceResolver` computes and loads the expected REST service class.
- `FieldResolver` performs strict or opt-in fuzzy entity-field matching.
- `FixtureResolver` checks public `TABLE_NAME` and the module-local SQL fixture path.

Resolution objects retain success/failure details rather than forcing the renderer to rediscover project state.

## Planning

`DoctorPlanner` combines the discovered project with resolver results and existing generated source. It produces a
`GenerationPlan` containing `READY`, `BLOCKED`, `EXISTING`, and `STALE` entries for the `FILTER_TEST` capability, plus
diagnostic entries for services, fixtures, and invalid existing files.

Both `doctor` and `filter-tests` consume this filter plan. This is the key consistency boundary: the same service and
field decision that appears in diagnostics controls filter generation.

Fixture diagnostics are represented in the plan but are currently separate from filter readiness; the generator can
write a test whose consumer fixture/runtime prerequisites still need repair.

## Rendering

`FilterTestAstBuilder` renders methods from already-resolved field information. `FilterTestsCodeGenerator` owns
incremental file updates, markers, stale reporting, and writes. Rendering should not decide which field or service is
correct.

CRUD uses `CrudTestsCodeGenerator` and `CrudTestAstBuilder` with a smaller, convention-based path. CRUD does not yet use
the filter `GenerationPlan` to infer endpoint capabilities; it emits the two fixed smoke-test operations documented in
[`crud-tests`](crud-tests.md).

## Reporting

`QuietlyReport` renders goal-specific Markdown and JSON from logical report entries. It keeps discovery/readiness,
generation events, module context, and runtime execution separate. Runtime execution is always `NOT_MEASURED`; Maven and
Surefire remain authoritative.

## Module responsibilities

| Artifact | Responsibility |
| --- | --- |
| `quietly-parent` | Reactor and shared dependency/build metadata. |
| `quietly-core` | Entity scanning and Hibernate filter metadata extraction. |
| `quietly-maven-plugin` | Mojos, module context, discovery orchestration, resolution, planning, AST rendering, reports. |
| `quietly-test-support` | Runtime base classes and helpers used by generated tests. |

This boundary keeps Maven/project inspection out of runtime test support and keeps generated-test runtime dependencies
out of the plugin realm.
