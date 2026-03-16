#!/usr/bin/env bash
# Run instead of bare `terraform init`.
# Auto-detects the AWS account ID, creates the state bucket if absent, then inits.
set -euo pipefail

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET="stanford-courses-tfstate-${ACCOUNT_ID}"
REGION="us-east-1"

# Create the bucket if it doesn't exist
if ! aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "Creating state bucket $BUCKET..."
  aws s3 mb "s3://$BUCKET" --region "$REGION"
  aws s3api put-bucket-versioning \
    --bucket "$BUCKET" \
    --versioning-configuration Status=Enabled
fi

terraform init -backend-config="bucket=${BUCKET}" "$@"
