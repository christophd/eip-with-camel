# Resume point — Camel 4.22 upgrade

**Date:** 2026-09-14
**Branch:** `iteration/camel-4.22-upgrade`, 103 commits ahead of `main`, working tree clean, CI green.
**Not merged to main.** That is the last step and needs the user's go-ahead.

Read `upgrade-camel-4.22-PLAN.md` first — it has the full record. This file is
only the short list of what is still open.

---

## Done and proven

| Check | Result |
|---|---|
| `./scripts/build-all-examples.sh --with-tests` | **56/56 pass** (final run 2026-09-14) |
| `./scripts/verify-all-runtime.sh` | **54/54 artifacts boot** |
| `./scripts/compile-chapter-snippets.sh` | **116/116 snippets compile** |
| `./scripts/validate-content.py --links` | clean |
| Chapter footers | 43/43 verified, each stating what was exercised |

Stack: Camel 4.22.0 on all runtimes, Quarkus 3.39.3, Spring Boot 4.1.1
(Spring 7.0.9), Citrus 5.0.1, JDK 25. Issues #12–#15 closed. All 21 Citrus PRs
merged.

---

## Open items, in the order they were being worked

### 1. SDKMAN instructions — IN PROGRESS, nearly done

Chapter 00's install steps were being verified in a clean `fedora:44` container.

**Already established:**
- `curl -s "https://get.sdkman.io" | bash` → works
- `sdk install java 25.0.2-tem` → works; `java -version` prints exactly the
  `openjdk version "25.0.2" 2026-01-20 LTS` the chapter claims
- **SDKMAN requires `unzip`, which chapter 00 does not mention.** On a bare
  Fedora the installer stops with "Looking for unzip... Not found." This needs
  adding to the chapter as a prerequisite.

**Still to confirm:** `sdk install maven` and `sdk install jbang` in the same
clean container. A background run was doing this; re-run it with:

```bash
podman run --rm docker.io/library/fedora:44 bash -lc '
  dnf install -y -q zip unzip >/dev/null 2>&1
  curl -s "https://get.sdkman.io" | bash >/dev/null 2>&1
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk install java 25.0.2-tem </dev/null >/dev/null 2>&1 && echo "java OK"
  sdk install maven </dev/null >/dev/null 2>&1 && echo "maven OK"
  sdk install jbang </dev/null >/dev/null 2>&1 && echo "jbang OK"
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  java -version 2>&1 | head -1; mvn -version 2>&1 | head -1; jbang --version 2>&1 | head -1'
```

Then: add the `unzip` prerequisite to `_docs/00-prerequisites.md`, and update
that chapter's footer, which currently says the SDKMAN commands were **not**
verified.

### 2. Testcontainers on Podman — my earlier claim was wrong, fix it

I reported that the podman socket "does not exist". **It does.**
`/run/user/25963/podman/podman.sock` is present and answers:

```bash
curl --unix-socket /run/user/$(id -u)/podman/podman.sock http://d/v1.41/version
```

It was created by `setup-stack.sh` at 06:16, after the check that said it was
missing. So the whole test suite ran on Docker only because `DOCKER_HOST` was
unset and Docker's socket was found first — not because Podman was unavailable.

**To do:** run the Citrus suite with Testcontainers pointed at Podman and see
whether it passes:

```bash
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
podman-compose -p eip -f examples/_infra/compose.yaml down   # ports must be free
./scripts/build-all-examples.sh --with-tests 04-channel-types
```

The open question is whether Testcontainers' `ComposeContainer`, which shells
out to a compose binary, works against the podman socket. If it does, the
project is finally consistent with its own documentation and appendix 41 should
say so. If it does not, appendix 41's Podman section should say plainly that
Docker is required for the Citrus tests.

Either way the appendix 41 text added in this branch needs revising — it
currently implies the socket may not exist, which is not the failure mode.

### 3. Appendix 42 tool invocation — model is now available

`llama3.2` does not reliably call tools, so `toolExecutions` came back empty and
the chapter says so. **`qwen2.5:3b` has since been pulled** and does support
tool calling.

**To do:** point the example at it and see whether the `ai-tool` route actually
fires.

```bash
export PATH="$HOME/.local/ollama/bin:$PATH"
ollama serve &          # if not running
# quarkus: quarkus.langchain4j.ollama.chat-model.model-id=qwen2.5:3b
# spring:  ollama.model-name=qwen2.5:3b
java -jar examples/42-ai-mcp/quarkus/target/quarkus-app/quarkus-run.jar
curl -X POST http://localhost:8088/api/assistant/chat \
  -H 'Content-Type: text/plain' -d 'What is the status of order ORD-001?'
```

Look for `Tool call — looking up order: ORD-001` in the log and a non-empty
`toolExecutions`. If it fires, switch the example's default model to
`qwen2.5:3b`, update the chapter's `ollama pull` line and the note about
llama3.2, and re-verify the footer claim.

Ollama is installed at `~/.local/ollama/bin/ollama` (0.34.0), not on PATH by
default. Models present: `llama3.2`, `qwen2.5:3b`.

---

## Two traps that cost time — do not repeat

- **Never `pkill -f <pattern>`** where the pattern appears in the command line
  you are typing. It matches your own shell and kills the session. Kill by PID.
- **Rootless podman containers are host java processes.** A blanket
  `kill` over java PIDs takes down Kafka, Pulsar and Apicurio. That is what
  caused the "Kafka unreachable" detour.
- The Citrus tests and the dev stack cannot both be up; both bind
  9092/6379/5432/6650. Take the stack down before running tests.

---

## Deliberately left

- **431 chapter/example identifier divergences.** Triaged: chapters show
  variants the examples implement once. The user chose to fix only true 1:1
  renames, and 37 of those were fixed.
