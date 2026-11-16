#!/usr/bin/env bash
# =============================================================================
# Build every service JAR and copy it to deploy/artifacts/<service>/app.jar
# =============================================================================
# Usage:
#   ./deploy/scripts/build-jars.sh
#   ./deploy/scripts/build-jars.sh gateway-service auth-service
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ARTIFACTS_ROOT="$REPO_ROOT/deploy/artifacts"

if [ "$#" -gt 0 ]; then
    SERVICES=("$@")
else
    SERVICES=(
        "gateway-service"
        "auth-service"
        "arena-service"
        "assessment-service"
        "analytics-service"
        "code-sandbox"
    )
fi

for service in "${SERVICES[@]}"; do
    service_dir="$REPO_ROOT/$service"

    if [ ! -d "$service_dir" ]; then
        echo "Unknown service directory: $service_dir" >&2
        exit 1
    fi

    echo ""
    echo "=== Building $service ==="

    pushd "$service_dir" >/dev/null
    sh ./mvnw -q -DskipTests clean package
    popd >/dev/null

    jar="$(find "$service_dir/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n1)"

    if [ -z "$jar" ]; then
        echo "No JAR produced for $service" >&2
        exit 1
    fi

    mkdir -p "$ARTIFACTS_ROOT/$service"
    cp "$jar" "$ARTIFACTS_ROOT/$service/app.jar"

    echo "  -> deploy/artifacts/$service/app.jar"
done

echo ""
echo "All requested services built."
echo "Next: upload each app.jar to its Botkeep Java workload."
