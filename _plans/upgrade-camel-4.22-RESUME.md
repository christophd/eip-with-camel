# Resume point — Camel 4.22 upgrade

**Date:** 2026-09-14
**Branch:** `iteration/camel-4.22-upgrade`. Working tree clean, CI green.
**Not merged to main.** That is the only remaining step and needs the user's go-ahead.

Full record is in `upgrade-camel-4.22-PLAN.md`.

---

## All three previously-open items are closed

### 1. SDKMAN — done

Verified on a clean `fedora:44` container. SDKMAN installs; `sdk install java
25.0.2-tem`, `sdk install maven` and `sdk install jbang` all succeed and report
OpenJDK 25.0.2, Maven 3.9.16, JBang 0.141.0 — matching what chapter 00 claims.

Found and fixed: **SDKMAN requires `zip`/`unzip`** and stops with
`Looking for unzip... Not found.` without them. Chapter 00 never said so; it
now does, with the install command for dnf and apt. Its JBang version sample
was also two releases stale.

### 2. Testcontainers on Podman — done, and my earlier claim was wrong

I had reported the Podman socket did not exist. **It does** — `setup-stack.sh`
creates it, and my check ran before that. The tests used Docker only because
`DOCKER_HOST` was unset and Docker's socket was found first.

Confirmed by running 04-channel-types against the Podman socket: **8 of 8
pass**. `scripts/build-all-examples.sh` now exports `DOCKER_HOST` and
`TESTCONTAINERS_RYUK_DISABLED` when the socket exists and the caller has not
chosen an engine, and prints which engine it picked. Appendix 41 is rewritten;
its previous text described a failure mode that does not occur.

### 3. Appendix 42 tool calling — done

Two causes, both fixed:

- **`llama3.2` does not call tools.** One round trip, empty `toolExecutions`,
  and the assistant invents an answer. Examples now default to **`qwen2.5:3b`**
  (1.9 GB), which calls tools reliably.
- **On Quarkus that was not sufficient.** Injecting the `ChatModel` that
  quarkus-langchain4j produces drives the classifier fine, but the agent never
  offered the registered tools through it. Building the model directly in the
  CDI producer — as the Spring Boot variant already did — fixes it. The Ollama
  client timeout also had to rise from its 10s default, which expired once a
  second round trip was involved.

Verified on both runtimes: asking for ORD-002 logs
`Tool call — looking up order: ORD-002` and the assistant answers from the
tool's data, zero route errors.

Ollama is at `~/.local/ollama/bin/ollama` (0.34.0), not on PATH by default.
Models present: `qwen2.5:3b`, `llama3.2`.

---

## State

| Check | Result |
|---|---|
| `build-all-examples.sh --with-tests` | 56/56 (last full run; a Podman-engine re-run was in flight) |
| `verify-all-runtime.sh` | 54/54 artifacts boot |
| `compile-chapter-snippets.sh` | 116/116 snippets compile |
| `validate-content.py --links` | clean |
| Chapter footers | 43/43 verified |

---

## Two traps that cost time — do not repeat

- **Never `pkill -f <pattern>`** where the pattern appears in the command you
  are typing; it matches your own shell and kills the session. Kill by PID.
- **Rootless podman containers are host java processes.** A blanket `kill` over
  java PIDs takes down Kafka, Pulsar and Apicurio.
- The Citrus tests and the dev stack cannot both be up; both bind
  9092/6379/5432/6650.

---

## Deliberately left

- **431 chapter/example identifier divergences.** Chapters show variants the
  examples implement once. Only true 1:1 renames were fixed (37 of them).
