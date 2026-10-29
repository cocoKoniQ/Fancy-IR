#!/usr/bin/env bash
# Regenerates the bundled Layer 2 catalog (core/data/src/main/assets/catalog/) from pinned
# upstream revisions. Re-run after bumping a pin; review the diff of catalog.properties.
#
#   POLICY=cc0-only (default)  Flipper-IRDB files added after its CC0 relicensing commit only.
#   POLICY=all                 every Flipper-IRDB file (see ARCHITECTURE.md §3.4 before shipping).
set -euo pipefail

IRDB_REPO=https://github.com/Lucaslhm/Flipper-IRDB
IRDB_REF=d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d
# "Commits prior to 2319685 are not covered" by the CC0 dedication (Flipper-IRDB README).
IRDB_CC0_CUTOFF=2319685f2cbf0cd3f809609622cade14d24fb819
SMARTIR_REPO=https://github.com/smartHomeHub/SmartIR
SMARTIR_REF=e4df2957ad915536f41ffb39daa96886d7cfe040

POLICY=${POLICY:-cc0-only}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
WORK=${WORK:-$(mktemp -d)}

checkout() { # repo ref dir
  if [ ! -d "$3/.git" ]; then git init -q "$3"; git -C "$3" remote add origin "$1"; fi
  git -C "$3" fetch -q --depth 1 origin "$2"
  git -C "$3" checkout -q FETCH_HEAD
}

checkout "$IRDB_REPO" "$IRDB_REF" "$WORK/irdb"
checkout "$SMARTIR_REPO" "$SMARTIR_REF" "$WORK/smartir"

EXCLUDE_ARGS=()
if [ "$POLICY" = "cc0-only" ]; then
  git -C "$WORK/irdb" fetch -q --depth 1 origin "$IRDB_CC0_CUTOFF"
  git -C "$WORK/irdb" ls-tree -r --name-only "$IRDB_CC0_CUTOFF" | grep -i '\.ir$' > "$WORK/irdb-pre-cc0.txt"
  EXCLUDE_ARGS=(--irdb-exclude "$WORK/irdb-pre-cc0.txt")
fi

"$ROOT/gradlew" -p "$ROOT" -q :tools:irdb-compiler:run --args="--irdb $WORK/irdb ${EXCLUDE_ARGS[*]} --smartir $WORK/smartir --out $ROOT/core/data/src/main/assets/catalog/fancyir-catalog.tsv.gzip"
