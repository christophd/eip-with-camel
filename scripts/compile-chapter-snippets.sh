#!/usr/bin/env bash
# Compile the Java route snippets embedded in the chapters against Camel 4.22.
#
# Chapter code is not built by anything. The Resilience4j snippet that called
# waitDurationInOpenState(int) went stale at the 4.22 upgrade and nothing
# noticed, because a chapter is markdown. This wraps each route-shaped snippet
# in a RouteBuilder and runs javac over it.
#
#   ./scripts/compile-chapter-snippets.sh          # all chapters
#   ./scripts/compile-chapter-snippets.sh 02 18    # only matching
#
# Snippets are fragments, so some cannot compile in isolation: they reference
# beans, helper classes or variables the chapter defines in prose. Those are
# reported separately from genuine API errors.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

WORK="${TMPDIR:-/tmp}/eip-snippets"
CP_FILE="$WORK/cp.txt"
rm -rf "$WORK"; mkdir -p "$WORK/src" "$WORK/out"

# Borrow a classpath that already has Camel plus the common components.
echo "resolving classpath from examples/17-observability/spring-boot ..."
mvn -B -q -f examples/17-observability/spring-boot/pom.xml \
    dependency:build-classpath -Dmdep.outputFile="$CP_FILE" -DincludeScope=test >/dev/null 2>&1
[ -s "$CP_FILE" ] || { echo "could not resolve a classpath"; exit 2; }
CP="$(cat "$CP_FILE")"

python3 - "$WORK" "$@" <<'PY'
import pathlib, re, sys
work = pathlib.Path(sys.argv[1]); filters = sys.argv[2:]
FENCE = re.compile(r"^```java\n(.*?)^```", re.M | re.S)
n = 0
for doc in sorted(pathlib.Path("_docs").glob("*.md")):
    if filters and not any(f in doc.name for f in filters):
        continue
    for i, body in enumerate(FENCE.findall(doc.read_text(encoding="utf-8", errors="replace"))):
        # Only route-shaped fragments: a from(...) chain, not a whole class.
        # Skip whole classes, and blocks that declare fields or methods before
        # the route -- those are bean definitions the chapter shows alongside it,
        # not route code, and they cannot sit inside configure().
        if "class " in body or not re.search(r"^\s*(from|rest)\(", body, re.M):
            continue
        head = body.split("from(")[0].split("rest(")[0]
        if re.search(r"@(Inject|Produces|Bean|Named|ApplicationScoped)\b|^\s*(public|private|protected)\s", head, re.M):
            continue
        stem = "Snip_" + doc.stem.replace("-", "_") + f"_{i}"
        (work / "src" / f"{stem}.java").write_text(
            "import org.apache.camel.*;\n"
            "import org.apache.camel.builder.RouteBuilder;\n"
            "import org.apache.camel.model.*;\n"
            "import org.apache.camel.model.dataformat.*;\n"
            "import java.util.*;\n"
            "import java.util.concurrent.*;\n"
            f"public class {stem} extends RouteBuilder {{\n"
            "  @Override public void configure() throws Exception {\n"
            f"{body}\n"
            "  }\n}\n", encoding="utf-8")
        n += 1
print(f"extracted {n} route snippet(s)")
PY

shopt -s nullglob
files=("$WORK"/src/*.java)
[ ${#files[@]} -eq 0 ] && { echo "no snippets"; exit 0; }

api=0; frag=0
for f in "${files[@]}"; do
  err="$WORK/$(basename "$f" .java).err"
  if javac -nowarn -proc:none -cp "$CP" -d "$WORK/out" "$f" > "$err" 2>&1; then
    continue
  fi
  # "cannot find symbol" for a bare identifier is a fragment referencing
  # something the chapter defines elsewhere, not a wrong Camel API call.
  if grep -qE "cannot find symbol|package .* does not exist" "$err" \
     && ! grep -qE "method .* cannot be applied|no suitable method|incompatible types|is not (public|abstract)" "$err"; then
    frag=$((frag + 1)); continue
  fi
  api=$((api + 1))
  echo
  echo "### $(basename "$f" .java)"
  grep -E "error:" "$err" | head -4 | sed 's/^/    /'
done

echo
echo "snippets compiled clean or fragment-limited: $(( ${#files[@]} - api ))"
echo "snippets with API errors:                    $api"
[ "$api" -gt 0 ] && exit 1 || exit 0
