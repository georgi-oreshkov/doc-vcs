#!/usr/bin/env bash
# One-time MinIO setup: create bucket + bind webhook event filter.
# Run after docker-compose up (wait for MinIO to be healthy first).
# Requires: mc CLI installed, or run via: docker run --rm -it --network host minio/mc ...

set -euo pipefail

MC="${MC:-mc}"
ALIAS="${MINIO_ALIAS:-local}"
ENDPOINT="${MINIO_ENDPOINT:-http://localhost:19000}"
ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
BUCKET="${S3_BUCKET:-vcs-documents}"
ARN="arn:minio:sqs::stagingdiff:webhook"

echo "→ Configuring mc alias..."
"$MC" alias set "$ALIAS" "$ENDPOINT" "$ACCESS_KEY" "$SECRET_KEY" --api s3v4

echo "→ Creating bucket (idempotent)..."
"$MC" mb --ignore-existing "$ALIAS/$BUCKET"

echo "→ Removing existing webhook event filter (if any)..."
"$MC" event remove "$ALIAS/$BUCKET" "$ARN" \
  --event put \
  --prefix "tmp/" \
  --suffix ".diff" 2>/dev/null || true

"$MC" event remove "$ALIAS/$BUCKET" "$ARN" \
  --event put \
  --prefix "documents/" 2>/dev/null || true

echo "→ Binding webhook event filter (PUT on tmp/*.diff — staging diffs)..."
"$MC" event add "$ALIAS/$BUCKET" "$ARN" \
  --event put \
  --prefix "tmp/" \
  --suffix ".diff"

echo "→ Binding webhook event filter (PUT on documents/ — snapshot uploads)..."
"$MC" event add "$ALIAS/$BUCKET" "$ARN" \
  --event put \
  --prefix "documents/"

echo "✓ MinIO setup complete."
