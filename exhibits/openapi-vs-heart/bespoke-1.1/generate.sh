#!/usr/bin/env bash
# Generates the garden client from the OpenAPI description this dashboard was built against,
# with the pinned OpenAPI Generator image. The generated code is committed, as teams do.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
spec=${1:-garden-1.1.0.json}
rm -rf "$here/src/generated"
docker run --rm -u "$(id -u):$(id -g)" -v "$here/..:/exhibit" openapitools/openapi-generator-cli:v7.8.0 generate \
  -i "/exhibit/openapi/$spec" -g typescript-fetch -o "/exhibit/$(basename "$here")/src/generated" \
  --additional-properties=supportsES6=true,typescriptThreePlus=true > /dev/null
rm -rf "$here/src/generated/.openapi-generator" "$here/src/generated/.openapi-generator-ignore"
