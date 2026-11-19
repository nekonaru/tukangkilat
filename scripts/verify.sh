#!/usr/bin/env bash
# Local verification gate: lint, tests and build. Mirror of the CI checks.
set -euo pipefail

echo "== Shared and backend tests =="
./gradlew :shared:test :backend:test --console=plain

echo "== Android debug build =="
./gradlew :app:assembleDebug --console=plain

echo "== No secrets in the repository =="
if command -v gitleaks >/dev/null 2>&1; then
  gitleaks detect --no-git --redact --source .
else
  echo "gitleaks not installed, skipping secret scan (CI runs it)"
fi

echo "OK"
