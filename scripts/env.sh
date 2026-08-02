#!/usr/bin/env bash
# Shared environment for every script here. Source it, don't execute it.
#
# Why this exists: Homebrew's Maven bundles its own JDK (23 on this machine) and uses it
# regardless of what `java` resolves to on PATH. Without pinning JAVA_HOME the reactor
# fails with "release version 25 not supported" even though `java -version` says 25.

set -euo pipefail

# Pick the newest installed JDK that is at least 21, preferring 25.
if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME:-}/bin/javac" ]]; then
  for v in 25 24 23 22 21; do
    if candidate=$(/usr/libexec/java_home -v "$v" 2>/dev/null); then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "error: no JDK 21+ found. Install one, or set JAVA_HOME by hand." >&2
  exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

# Signing key for the shared HS256 tokens. Must be >= 32 bytes. Overridden in any
# real deployment; hard-coded here so the demo starts with no setup.
export RTO_JWT_SECRET="${RTO_JWT_SECRET:-rto-local-development-secret-key-do-not-use-in-production}"

export RTO_KAFKA_BOOTSTRAP="${RTO_KAFKA_BOOTSTRAP:-localhost:9094}"
export RTO_POSTGRES_URL="${RTO_POSTGRES_URL:-jdbc:postgresql://localhost:5433/rto}"
export RTO_REDIS_HOST="${RTO_REDIS_HOST:-localhost}"
export RTO_REDIS_PORT="${RTO_REDIS_PORT:-6380}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export REPO_ROOT
