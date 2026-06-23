#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

build_and_hash() {
  local output="$1"

  cd "${root}"
  ./mvnw -B -ntp -nsu clean package \
    -DskipTests \
    -Ppublication,sbom,reproducible

  find . -type f \
    \( -path '*/target/*.jar' \
       -o -path '*/target/*.pom' \
       -o -path '*/target/*-cyclonedx.json' \
       -o -path '*/target/*.buildinfo' \) \
    ! -path './integration/target/*' \
    -print0 \
    | sort -z \
    | xargs -0 sha256sum \
    | sed "s#  \\./#  #" \
    > "${output}"
}

build_and_hash "${work}/first.sha256"
build_and_hash "${work}/second.sha256"

if ! diff -u "${work}/first.sha256" "${work}/second.sha256"; then
  echo "Reproducible-build verification failed." >&2
  exit 1
fi

echo "Reproducible-build verification passed for all published artifacts."
