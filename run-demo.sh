#!/usr/bin/env sh
# Builds the frontend, packages the backend, and runs the application.
#
# Defaults reproduce the demo exactly, so `./run-demo.sh` with no arguments is
# still the one command a reader needs. The options exist because the previous
# version could only ever do that one thing: every run reinstalled locked
# frontend dependencies and did a clean package, a two-minute wait to restart a
# server whose Java had not changed.
#
# It runs from the repository root because data/maps, data/scenarios and
# data/demand are runtime inputs resolved relative to the working directory.

set -eu

usage() {
    cat <<'USAGE'
Usage: ./run-demo.sh [options]

  --port <n>          HTTP port (default 7070)
  --scenario <path>   Startup scenario JSON
                      (default data/scenarios/district1_typical_day.json)
  --map <path>        Topology-only startup map. The backend ignores it unless
                      no scenario is set, and so does this script.
  --skip-frontend     Do not reinstall or rebuild the frontend. The JAR keeps
                      whatever UI it was last packaged with.
  --skip-build        Do not run Maven either. Needs an existing JAR; use it to
                      relaunch with a different scenario or port.
  -h, --help          Show this message

Examples:
  ./run-demo.sh
  ./run-demo.sh --port 8080 --scenario data/scenarios/test/u-turn.json
  ./run-demo.sh --skip-build --scenario data/scenarios/test/moto-filtering.json
USAGE
}

PORT="${PORT:-7070}"
SCENARIO="${SCENARIO_FILE:-}"
MAP="${MAP_FILE:-}"
SKIP_FRONTEND=0
SKIP_BUILD=0

while [ $# -gt 0 ]; do
    case "$1" in
        --port) [ $# -ge 2 ] || { echo "--port needs a value" >&2; exit 2; }; PORT="$2"; shift 2 ;;
        --scenario) [ $# -ge 2 ] || { echo "--scenario needs a value" >&2; exit 2; }; SCENARIO="$2"; shift 2 ;;
        --map) [ $# -ge 2 ] || { echo "--map needs a value" >&2; exit 2; }; MAP="$2"; shift 2 ;;
        --skip-frontend) SKIP_FRONTEND=1; shift ;;
        # Skipping the backend build implies skipping the frontend one, because
        # the frontend is served from inside the JAR that is not being rebuilt.
        --skip-build) SKIP_BUILD=1; SKIP_FRONTEND=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

cd "$(dirname "$0")"

require() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "Required command not found: $1 -- $2" >&2
        exit 1
    }
}
require java "install a JDK 17 or newer"
[ "$SKIP_BUILD" -eq 1 ] || require mvn "install Apache Maven 3.9 or newer"
[ "$SKIP_FRONTEND" -eq 1 ] || require npm "install Node.js and npm"

# Checked here rather than left to the backend: a typo in a path should fail
# before a two-minute build, not after it.
if [ -n "$SCENARIO" ] && [ ! -f "$SCENARIO" ]; then
    echo "Scenario file not found: $SCENARIO" >&2
    exit 1
fi
if [ -n "$MAP" ] && [ ! -f "$MAP" ]; then
    echo "Map file not found: $MAP" >&2
    exit 1
fi

if [ "$SKIP_FRONTEND" -eq 0 ]; then
    echo "==> Frontend dependencies"
    npm --prefix frontend ci
    echo "==> Frontend build"
    npm --prefix frontend run build
fi

if [ "$SKIP_BUILD" -eq 0 ]; then
    echo "==> Backend package"
    mvn -q -DskipTests clean package
fi

# Located rather than hardcoded: the file name carries the project version, so
# pinning it means a version bump silently breaks the launch script.
#
# Shortest name wins. Maven names the artifact to launch with no classifier, and
# every other jar in target/ -- shaded, sources, javadoc, original -- adds one,
# so it is always the shortest. Listing classifiers to exclude instead would
# need editing whenever the build grows another one.
JAR=$(ls target/traffic-event-system-*.jar 2>/dev/null | awk '{ print length, $0 }' | sort -n | head -n 1 | cut -d" " -f2- || true)
if [ -z "$JAR" ]; then
    echo "No application JAR in target/. Run without --skip-build first." >&2
    exit 1
fi

echo "==> http://localhost:$PORT  ($JAR)"
if [ -n "$SCENARIO" ]; then
    echo "    scenario: $SCENARIO"
elif [ -n "$MAP" ]; then
    echo "    map:      $MAP"
fi

export PORT
[ -n "$SCENARIO" ] && { SCENARIO_FILE="$SCENARIO"; export SCENARIO_FILE; }
[ -n "$MAP" ] && { MAP_FILE="$MAP"; export MAP_FILE; }

exec java -jar "$JAR"
