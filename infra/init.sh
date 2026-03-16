#!/usr/bin/env bash
# Run instead of bare `terraform init`.
# Auto-detects the AWS account ID, creates the state bucket if absent,
# pushes placeholder images to ECR if absent, then inits.
set -euo pipefail

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET="stanford-courses-tfstate-${ACCOUNT_ID}"
REGION="us-east-1"
ECR_BASE="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
PROJECT="stanford-courses-prod"

# ── State bucket ─────────────────────────────────────────────────────────────
if ! aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "Creating state bucket $BUCKET..."
  aws s3 mb "s3://$BUCKET" --region "$REGION"
  aws s3api put-bucket-versioning \
    --bucket "$BUCKET" \
    --versioning-configuration Status=Enabled
fi

# ── Placeholder images ───────────────────────────────────────────────────────
# Lambda requires a valid image URI at creation time.
# Push a minimal placeholder to each ECR repo if no image exists yet.
# CI/CD will replace these with real images on the first backend deploy.
needs_placeholder() {
  local repo="$1"
  # repo must exist first (created by terraform); skip silently if not yet
  aws ecr describe-images \
    --repository-name "${PROJECT}-${repo}" \
    --region "$REGION" \
    --query 'imageDetails[0]' \
    --output text 2>/dev/null | grep -q "None" || \
  ! aws ecr describe-images \
    --repository-name "${PROJECT}-${repo}" \
    --region "$REGION" 2>/dev/null | grep -q "imageTag"
}

push_placeholder() {
  local repo="$1"
  local image="${ECR_BASE}/${PROJECT}-${repo}:placeholder"
  echo "Pushing placeholder image to ${repo}..."
  aws ecr get-login-password --region "$REGION" | \
    docker login --username AWS --password-stdin "$ECR_BASE"
  docker pull --quiet public.ecr.aws/lambda/provided:al2
  docker tag public.ecr.aws/lambda/provided:al2 "$image"
  docker push "$image"
}

for repo in api ingestion post-confirmation; do
  if aws ecr describe-repositories \
      --repository-names "${PROJECT}-${repo}" \
      --region "$REGION" &>/dev/null; then
    image_count=$(aws ecr describe-images \
      --repository-name "${PROJECT}-${repo}" \
      --region "$REGION" \
      --query 'length(imageDetails)' \
      --output text 2>/dev/null || echo "0")
    if [ "$image_count" = "0" ]; then
      push_placeholder "$repo"
    fi
  fi
done

# ── Terraform init ────────────────────────────────────────────────────────────
terraform init -backend-config="bucket=${BUCKET}" "$@"
