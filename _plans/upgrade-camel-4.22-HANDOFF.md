# Handoff — Camel 4.22 / Quarkus 3.39.1 / Spring Boot 4.1.1 upgrade

**Status:** Phase 1 (planning) NOT yet complete. Recon done, decisions locked, no code written.
**Date:** 2026-09-11
**Repo state at handoff:** branch `main`, HEAD `2f6c91e`, working tree clean (no repo files modified).

---

## Why this session ended

Background subagents deadlocked twice (see memory `project_gitkraken-hook-deadlock.md`).
The GitKraken plugin registered a `PermissionRequest` hook with a 24-hour blocking
timeout; subagents cannot surface permission prompts, so they hung forever.

**Already fixed:** `"gitkraken-hooks@gitkraken": false` set under `enabledPlugins`
in `~/.claude/settings.json`. 46 leaked hook processes (~1.5 GB) and 3 stale MCP
servers killed. **The fix requires the session restart that is happening now.**

After restart, verify before fanning out subagents:

```bash
ps -eo pid,etime,args | grep '[g]k ai hook'   # expect: no output
```

---

## The task (5 workstreams, user's original ask)

1. Migrate site, docs/READMEs, and code to Camel 4.22 LTS / Quarkus / Spring Boot
2. Update tooling: container images, Kafka/Pulsar/Redis/PostgreSQL/LGTM, JDK to latest LTS
3. Full local retest of everything
4. Address the open repository issues
5. Evaluate and address the open pull requests

Method: `lgtm-relay` skill — Opus plans, Sonnet executes, Opus validates, with a
user gate on the plan before execution.

---

## DECISIONS LOCKED (user answered — do not re-open)

### 1. Version anchor: Camel 4.22 across all three runtimes

The original ask named **Quarkus 3.33.3.2**, which is impossible alongside Camel 4.22:
the Quarkus platform BOM pins the Camel version.

- Quarkus 3.33.x LTS → Camel Quarkus 3.33 → **Camel 4.18**
- Camel 4.22 → Camel Quarkus 3.39.0 → Quarkus platform **3.39.x**

User chose Camel 4.22 everywhere, accepting that Quarkus 3.39.x is not an LTS
stream, in exchange for one consistent Camel version in every codetab.
**"Quarkus 3.33.3.2" is dropped.**

### 2. PR sequencing: merge the 21 Citrus PRs FIRST, then upgrade

Rationale: land them on the stack they were authored against, then carry them
through the upgrade as the regression net.

**Unresolved tension the plan must address:** CI has *never run* on any of these
PRs, so "known-good" is an assumption. They need local verification before landing,
and fixing the already-red CI may be the true step 0.

---

## Target versions

| Component | Current | Target |
|---|---|---|
| Apache Camel | 4.20.0 | **4.22.0 LTS** (rel. 2026-08-11) |
| Quarkus platform | 3.37.0 | **3.39.1** (ships Camel 4.22; per Camel K 2.11.0) |
| Spring Boot | 4.0.7 | **4.1.1** (rel. 2026-08-20; 4.0 EOL 2026-12-31) |
| Java | 25 | 25 — already current LTS, no pom change |
| CI JDK | **21** | 25 — mismatch, must fix |
| Citrus | docs say 4.4.0 / PRs use 5.0.0 | reconcile — **mismatch found** |

---

## VERIFIED REPO STATE (gathered — do not re-derive)

### Poms
- **57** `pom.xml`. **No parent aggregator** — every example standalone.
  Only "root-ish": `examples/domain-model/pom.xml`, `examples/25-quarkus-flow/pom.xml` (multi-module).
- 27 `quarkus/` dirs, 27 `spring-boot/` dirs, 10 `yaml-dsl/` dirs.
- `quarkus.platform.version` = `3.37.0` (28x, **a property**)
- `camel.version` = `4.20.0` (27x, **a property**; drives `camel-spring-boot-bom` + `camel-test-spring-junit5`)
- `spring-boot-starter-parent` = `4.0.7` — **HARDCODED literal in `<parent>`, NOT a property.**
  A naive property bump will silently miss all 27.
- `maven.compiler.source`/`target` = 25 (29x); `java.version` = 25 (27x)
- Quarkus poms import `io.quarkus.platform:quarkus-bom` + `quarkus-camel-bom`, plus `quarkus-maven-plugin`, all at `${quarkus.platform.version}`.

### CI — both facts matter
- **`Build Examples` is ALREADY RED on `main`** — failing every push (20s, 38s).
  `Build and deploy site` is green. Cause almost certainly JDK 21 vs `release=25`.
- **No PR has ever run CI.** Every run on every PR branch is `action_required`, 0s —
  the GitHub fork-PR approval gate. **Zero CI signal on 21 PRs.**
- `.github/workflows/examples.yml`: matrix of 17 examples, JDK 21, `mvn -B package -DskipTests`.
  Omits 20, 21, 22, 24, 25, 27, 32, 33, 37, 38, 39, 40, 41, 42 (14 examples untested).

### Infra images — all `:latest`, a reproducibility problem
In `examples/_infra/compose.yaml` + `compose.lgtm.yaml`:
`apache/kafka:latest`, `apachepulsar/pulsar:latest`, `apicurio/apicurio-registry:latest`,
`grafana/grafana:latest`, `grafana/loki:latest`, `grafana/mimir:latest`, `grafana/tempo:latest`,
`postgres:16-alpine`, `redis:7-alpine`, `otel/opentelemetry-collector-contrib:latest`,
`provectuslabs/kafka-ui:latest`

### Version-bearing docs to update
`_docs/00-prerequisites.md` (SDKMAN, JDK 25 `25.0.2-tem`, JBang, `jbang app install camel@apache/camel`),
`GETTING-STARTED.md`, `README.md`, `examples/_infra/README.md`,
`_docs/31-appendix-virtual-threads.md`, and chapters `39-camel-cli`, `40-camel-tui`,
`41-citrus-testing`, `42-ai-mcp`.

---

## OPEN ISSUES (4)

| # | Title | Note |
|---|---|---|
| 15 | docs: document the embedded Camel MCP Server in Appendix X | |
| 14 | Simplify package-photo example with langchain4j-agent multimodal | |
| 13 | Migrate Appendix X AI/MCP examples to Camel 4.22 `ai-tool` | **Unblocked by this upgrade — fold into workstream 1** |
| 12 | Migrate Spring Boot REST examples to Platform HTTP | Interacts with the Spring Boot 4.1.1 move |

---

## OPEN PRs (21) — all from `christophd`, all "test: add Citrus integration tests for chapter NN"

All **MERGEABLE**. Totals: **508 files, +21,698 lines.**

```
PR  files  +add  -del  branch
2   36  1765  132  tests/04-channel-types
3   17   577    2  tests/05-reliability
4   32  1331   52  tests/06-channel-infra
5   29   967   25  tests/07-message-types
6   26  1193   40  tests/08-message-metadata
7   38  1444   53  tests/09-routing-fundamentals
8   17   766    1  tests/10-composed-routing
9   32  1164   24  tests/11-advanced-routing
10  22   826    2  tests/12-transformation
11  31  1134    9  tests/13-aggregator
16  36  1741   45  tests/14-consumer-patterns
17  24  1322    3  tests/15-endpoints
18  26  1157   16  tests/16-endpoint-management
19  24  1092   37  tests/17-observability
20  23  1058    4  tests/18-testing-management
21  19   723    2  tests/20-kafka-deep-dive
22  19   678    0  tests/21-pulsar-deep-dive
23  17   868    2  tests/22-redis-integration
24  17   748    2  tests/24-drools-rules
25   8   351    0  tests/25-quarkus-flow
26  15   793    1  tests/27-observability-stack
```

Shape (verified from #2 and #26 diffs):
- Each scoped to ONE chapter dir, touching both `quarkus/` and `spring-boot/`.
  **Largely disjoint → batch merge is viable.**
- Only shared file: `.gitignore` (PR #2 adds `.camel-jbang/`, `.citrus-jbang/`) — expect conflict there only.
- Modify `pom.xml` per runtime: add `<citrus.version>5.0.0</citrus.version>`,
  `<surefire-plugin.version>3.5.6</surefire-plugin.version>`, and
  `citrus-camel`, `citrus-quarkus`, `citrus-junit-jupiter`, `citrus-kafka` test deps.
- **Modify `src/main/java` too**, not just tests (`DemoDataGenerator`, `PulsarDemoDataGenerator`,
  `RedisChannelRoute`, `RedisDemoDataGenerator`) — these change production example code.
- Add **~40 new `src/test/resources/_infra/compose.yaml`** files, **also using `:latest`**.
  → Image-pinning must run AFTER the merge, or be applied twice.
- Add `src/test/resources/{application.properties,citrus-application.properties,templates/order.json}`.

---

## NEXT STEP ON RESTART

Phase 1 planning was never completed. Resume with:

> Continue the Camel 4.22 upgrade relay. Read `_plans/upgrade-camel-4.22-HANDOFF.md`
> for full context, then produce the Phase 1 plan.

Plan inline (session model is `opus[1m]`, which already satisfies the relay's
Opus-planner requirement) or delegate to a `Plan` subagent now that the hook
deadlock is fixed. Gate the plan on user review before any execution.

### Research still outstanding for the plan
- Camel 4.20 → 4.22 breaking changes. **Priority: the dynamic URI allow-list for
  `toD`/`enrich` and the JEP-290 deserialization filter** — most likely to break
  working routes silently at runtime, not at compile time. Determine default
  posture (opt-in vs opt-out) and the config that restores prior behaviour.
- Quarkus 3.37 → 3.38 → 3.39 migration guides.
- Spring Boot 4.0 → 4.1 release notes affecting Camel Spring Boot.
- Exact patch versions + current stable image tags to pin.
- Grep `_docs/`, `README.md`, `GETTING-STARTED.md` for hardcoded `4.20`, `3.37`,
  `4.0.7`, `4.4.0`, Citrus versions.

### Plan must resolve
1. How the 21 untested PRs get verified before landing (fix red CI first as step 0?)
2. Where image-pinning sits relative to the PR merge (PRs add 40 more `:latest` files)
3. Aggregator pom vs scripted edits across 57 poms — noting an aggregator would change
   the documented per-example `cd examples/<name>/<runtime> && mvn ...` commands in CLAUDE.md
4. Citrus 4.4.0 (docs) vs 5.0.0 (PRs) mismatch
5. Mechanical vs semantic step split, and which steps are parallel-safe

### Relay hygiene reminders
- Work on an **iteration branch**, never checkpoint onto `main`.
- Commit at every phase boundary and after each executed step.
- No `Co-authored-by` trailers.
- **Restart-verify the gk hook fix before spawning any subagent.**
