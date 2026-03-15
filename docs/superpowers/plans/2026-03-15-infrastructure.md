# Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provision all AWS infrastructure for the Stanford Course Finder using Terraform with S3 native state locking.

**Architecture:** Terraform modules for each AWS service (Cognito, DynamoDB, ECR, AppConfig, Lambda, API Gateway, EventBridge, Amplify, Observability). Bootstrap creates the S3 state bucket first using a local backend, then all other resources are managed via remote S3 state with native locking (`use_lockfile = true`, Terraform ~> 1.14).

**Tech Stack:** Terraform ~> 1.14, AWS provider ~> 6.36, AWS CLI v2, `us-east-1` region.

**Spec:** `docs/superpowers/specs/2026-03-15-stanford-course-finder-design.md`

---

## Prerequisites

- Terraform ~> 1.14 installed (`terraform version`)
- AWS provider ~> 6.36 (managed via Terraform required_providers)
- AWS CLI v2 configured with credentials for a **dev** account (`aws sts get-caller-identity`)
- An AWS account with permissions to create: S3, DynamoDB, Cognito, Lambda, ECR, API Gateway, AppConfig, EventBridge, Amplify, IAM roles
- A GitHub repo for the project (needed for Amplify connection)

---

## File Structure

```text
infra/
├── bootstrap/
│   ├── main.tf          # S3 state bucket (local backend, run once)
│   ├── variables.tf
│   └── outputs.tf
├── main.tf              # root module: backend + provider + module calls
├── variables.tf         # project_name, environment, aws_region, github_*
├── outputs.tf           # API URL, Cognito IDs, ECR URIs
└── modules/
    ├── cognito/
    │   ├── main.tf      # UserPool, UserPoolClient, PostConfirmation trigger
    │   ├── variables.tf
    │   └── outputs.tf
    ├── dynamodb/
    │   ├── main.tf      # courses, applications (+ GSI), users tables
    │   ├── variables.tf
    │   └── outputs.tf
    ├── ecr/
    │   ├── main.tf      # api, ingestion, post-confirmation repositories
    │   ├── variables.tf
    │   └── outputs.tf
    ├── appconfig/
    │   ├── main.tf      # application, environment, profile, initial config
    │   ├── variables.tf
    │   └── outputs.tf
    ├── lambda/
    │   ├── main.tf      # api, ingestion, post-confirmation, admin-authorizer functions
    │   ├── iam.tf       # roles + policies for each function
    │   ├── variables.tf
    │   └── outputs.tf
    ├── api-gateway/
    │   ├── main.tf      # HTTP API, routes, Cognito authorizer, admin authorizer
    │   ├── variables.tf
    │   └── outputs.tf
    ├── eventbridge/
    │   ├── main.tf      # scheduler for weekly ingestion
    │   ├── variables.tf
    │   └── outputs.tf
    ├── amplify/
    │   ├── main.tf      # Amplify app + main branch + env vars
    │   ├── variables.tf
    │   └── outputs.tf
    └── observability/
        ├── main.tf      # X-Ray, CloudWatch log groups, alarms, dashboard, SNS
        ├── variables.tf
        └── outputs.tf
```

---

## Chunk 1: Bootstrap + Root Skeleton

### Task 1: Bootstrap — S3 State Bucket

**Files:**

- Create: `infra/bootstrap/main.tf`
- Create: `infra/bootstrap/variables.tf`
- Create: `infra/bootstrap/outputs.tf`

- [ ] **Step 1: Create `infra/bootstrap/variables.tf`**

```hcl
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "state_bucket_name" {
  description = "Name for the Terraform state S3 bucket (must be globally unique)"
  type        = string
}
```

- [ ] **Step 2: Create `infra/bootstrap/main.tf`**

```hcl
terraform {
  required_version = "~> 1.14"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.36"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

resource "aws_s3_bucket" "tfstate" {
  bucket = var.state_bucket_name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket                  = aws_s3_bucket.tfstate.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
```

- [ ] **Step 3: Create `infra/bootstrap/outputs.tf`**

```hcl
output "state_bucket_name" {
  value = aws_s3_bucket.tfstate.bucket
}
```

- [ ] **Step 4: Initialize and validate**

```bash
cd infra/bootstrap
terraform init
terraform validate
```

Expected: `Success! The configuration is valid.`

- [ ] **Step 5: Plan**

```bash
terraform plan -var="state_bucket_name=stanford-courses-tfstate-<your-account-id>"
```

Expected: `Plan: 4 to add, 0 to change, 0 to destroy.`
(bucket, versioning, encryption, public access block)

- [ ] **Step 6: Apply**

```bash
terraform apply -var="state_bucket_name=stanford-courses-tfstate-<your-account-id>"
```

Type `yes` when prompted. Expected: `Apply complete! Resources: 4 added.`

- [ ] **Step 7: Verify bucket exists**

```bash
aws s3api head-bucket --bucket stanford-courses-tfstate-<your-account-id>
aws s3api get-bucket-versioning --bucket stanford-courses-tfstate-<your-account-id>
```

Expected: second command returns `"Status": "Enabled"`

- [ ] **Step 8: Commit**

```bash
git add infra/bootstrap/
git commit -m "feat(infra): add Terraform bootstrap for S3 state bucket"
```

---

### Task 2: Root Module Skeleton

**Files:**

- Create: `infra/main.tf`
- Create: `infra/variables.tf`
- Create: `infra/outputs.tf`

- [ ] **Step 1: Create `infra/variables.tf`**

```hcl
variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (prod, dev)"
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Project name prefix for all resources"
  type        = string
  default     = "stanford-courses"
}

variable "github_owner" {
  description = "GitHub repository owner (username or org)"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name"
  type        = string
}

variable "github_branch" {
  description = "GitHub branch to deploy from"
  type        = string
  default     = "main"
}

variable "github_access_token" {
  description = "GitHub personal access token for Amplify"
  type        = string
  sensitive   = true
}
```

- [ ] **Step 2: Create `infra/main.tf`** (skeleton — module calls added in later tasks)

```hcl
terraform {
  required_version = "~> 1.14"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.36"
    }
  }

  backend "s3" {
    bucket       = "stanford-courses-tfstate-<your-account-id>"
    key          = "prod/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
```

- [ ] **Step 3: Create `infra/outputs.tf`** (empty for now, populated in later tasks)

```hcl
# Outputs added as modules are defined
```

- [ ] **Step 4: Initialize remote backend**

```bash
cd infra
terraform init
```

Expected output includes:

```text
Successfully configured the backend "s3"!
Terraform has been successfully initialized!
```

- [ ] **Step 5: Validate**

```bash
terraform validate
```

Expected: `Success! The configuration is valid.`

- [ ] **Step 6: Commit**

```bash
git add infra/main.tf infra/variables.tf infra/outputs.tf
git commit -m "feat(infra): add root Terraform module with S3 remote backend"
```

---

## Chunk 2: Cognito + DynamoDB Modules

### Task 3: Cognito Module

**Files:**

- Create: `infra/modules/cognito/main.tf`
- Create: `infra/modules/cognito/variables.tf`
- Create: `infra/modules/cognito/outputs.tf`

- [ ] **Step 1: Create `infra/modules/cognito/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "post_confirmation_lambda_arn" {
  description = "ARN of the Post-Confirmation Lambda (added after lambda module)"
  type        = string
  default     = ""
}
```

- [ ] **Step 2: Create `infra/modules/cognito/main.tf`**

```hcl
data "aws_region" "current" {}

resource "aws_cognito_user_pool" "main" {
  name = "${var.project_name}-${var.environment}"

  auto_verified_attributes = ["email"]

  username_attributes = ["email"]

  password_policy {
    minimum_length                   = 8
    require_uppercase                = true
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = false
    temporary_password_validity_days = 7
  }

  schema {
    name                = "email"
    attribute_data_type = "String"
    required            = true
    mutable             = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  dynamic "lambda_config" {
    for_each = var.post_confirmation_lambda_arn != "" ? [1] : []
    content {
      post_confirmation = var.post_confirmation_lambda_arn
    }
  }

  tags = {
    Name = "${var.project_name}-user-pool"
  }
}

resource "aws_cognito_user_pool_client" "frontend" {
  name         = "${var.project_name}-frontend"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["email", "openid", "profile"]

  callback_urls = ["http://localhost:3000/callback"]
  logout_urls   = ["http://localhost:3000"]

  supported_identity_providers = ["COGNITO"]

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]
}

resource "aws_cognito_user_group" "students" {
  name         = "students"
  user_pool_id = aws_cognito_user_pool.main.id
  description  = "Standard students group — assigned on self-registration"
}

resource "aws_cognito_user_group" "admins" {
  name         = "admins"
  user_pool_id = aws_cognito_user_pool.main.id
  description  = "Administrators — manually assigned"
}

resource "aws_cognito_user_pool_domain" "main" {
  domain       = "${var.project_name}-${var.environment}-auth"
  user_pool_id = aws_cognito_user_pool.main.id
}
```

- [ ] **Step 3: Create `infra/modules/cognito/outputs.tf`**

```hcl
output "user_pool_id" {
  value = aws_cognito_user_pool.main.id
}

output "user_pool_arn" {
  value = aws_cognito_user_pool.main.arn
}

output "user_pool_endpoint" {
  value = aws_cognito_user_pool.main.endpoint
}

output "client_id" {
  value = aws_cognito_user_pool_client.frontend.id
}

output "hosted_ui_domain" {
  value = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${data.aws_region.current.name}.amazoncognito.com"
}
```

- [ ] **Step 4: Add Cognito module call to `infra/main.tf`**

Append to `infra/main.tf`:

```hcl
module "cognito" {
  source       = "./modules/cognito"
  project_name = var.project_name
  environment  = var.environment
}
```

- [ ] **Step 5: Add outputs to `infra/outputs.tf`**

```hcl
output "cognito_user_pool_id" {
  value = module.cognito.user_pool_id
}

output "cognito_client_id" {
  value = module.cognito.client_id
}
```

- [ ] **Step 6: Validate and plan**

```bash
cd infra
terraform validate
terraform plan
```

Expected: `Plan: 5 to add` (user pool, client, 2 groups, user pool domain)

- [ ] **Step 7: Apply**

```bash
terraform apply
```

Type `yes`. Expected: `Apply complete! Resources: 5 added.`

- [ ] **Step 8: Verify**

```bash
terraform output cognito_user_pool_id
aws cognito-idp describe-user-pool --user-pool-id $(terraform output -raw cognito_user_pool_id)
```

Expected: User pool details including `"Status": "Active"`

- [ ] **Step 9: Commit**

```bash
git add infra/modules/cognito/ infra/main.tf infra/outputs.tf
git commit -m "feat(infra): add Cognito user pool, client, and groups"
```

---

### Task 4: DynamoDB Module

**Files:**

- Create: `infra/modules/dynamodb/main.tf`
- Create: `infra/modules/dynamodb/variables.tf`
- Create: `infra/modules/dynamodb/outputs.tf`

- [ ] **Step 1: Create `infra/modules/dynamodb/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}
```

- [ ] **Step 2: Create `infra/modules/dynamodb/main.tf`**

```hcl
resource "aws_dynamodb_table" "courses" {
  name         = "${var.project_name}-${var.environment}-courses"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "courseId"

  attribute {
    name = "courseId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "courses"
  }
}

resource "aws_dynamodb_table" "applications" {
  name         = "${var.project_name}-${var.environment}-applications"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"
  range_key    = "courseId"

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "courseId"
    type = "S"
  }

  global_secondary_index {
    name            = "courseId-index"
    hash_key        = "courseId"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "applications"
  }
}

resource "aws_dynamodb_table" "users" {
  name         = "${var.project_name}-${var.environment}-users"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"

  attribute {
    name = "userId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "users"
  }
}
```

- [ ] **Step 3: Create `infra/modules/dynamodb/outputs.tf`**

```hcl
output "courses_table_name" {
  value = aws_dynamodb_table.courses.name
}

output "courses_table_arn" {
  value = aws_dynamodb_table.courses.arn
}

output "applications_table_name" {
  value = aws_dynamodb_table.applications.name
}

output "applications_table_arn" {
  value = aws_dynamodb_table.applications.arn
}

output "users_table_name" {
  value = aws_dynamodb_table.users.name
}

output "users_table_arn" {
  value = aws_dynamodb_table.users.arn
}
```

- [ ] **Step 4: Add DynamoDB module call to `infra/main.tf`**

```hcl
module "dynamodb" {
  source       = "./modules/dynamodb"
  project_name = var.project_name
  environment  = var.environment
}
```

- [ ] **Step 5: Validate, plan, apply**

```bash
cd infra
terraform validate
terraform plan
terraform apply
```

Expected: `Plan: 3 to add` (3 DynamoDB tables).

- [ ] **Step 6: Verify**

```bash
aws dynamodb list-tables --region us-east-1 | grep stanford
```

Expected: 3 table names listed.

- [ ] **Step 7: Verify GSI on applications table**

```bash
aws dynamodb describe-table \
  --table-name stanford-courses-prod-applications \
  --query "Table.GlobalSecondaryIndexes[0].IndexName"
```

Expected: `"courseId-index"`

- [ ] **Step 8: Commit**

```bash
git add infra/modules/dynamodb/ infra/main.tf
git commit -m "feat(infra): add DynamoDB tables for courses, applications, users"
```

---

## Chunk 3: ECR + AppConfig + S3 Vectors Modules

### Task 5: ECR Module

**Files:**

- Create: `infra/modules/ecr/main.tf`
- Create: `infra/modules/ecr/variables.tf`
- Create: `infra/modules/ecr/outputs.tf`

- [ ] **Step 1: Create `infra/modules/ecr/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}
```

- [ ] **Step 2: Create `infra/modules/ecr/main.tf`**

```hcl
locals {
  repos = ["api", "ingestion", "post-confirmation"]
}

resource "aws_ecr_repository" "repos" {
  for_each = toset(local.repos)

  name                 = "${var.project_name}-${var.environment}-${each.key}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "keep_last_10" {
  for_each   = aws_ecr_repository.repos
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
```

- [ ] **Step 3: Create `infra/modules/ecr/outputs.tf`**

```hcl
output "repo_urls" {
  value = { for k, v in aws_ecr_repository.repos : k => v.repository_url }
}

output "api_repo_url" {
  value = aws_ecr_repository.repos["api"].repository_url
}

output "ingestion_repo_url" {
  value = aws_ecr_repository.repos["ingestion"].repository_url
}

output "post_confirmation_repo_url" {
  value = aws_ecr_repository.repos["post-confirmation"].repository_url
}
```

- [ ] **Step 4: Add ECR module to `infra/main.tf` and outputs**

In `infra/main.tf`:

```hcl
module "ecr" {
  source       = "./modules/ecr"
  project_name = var.project_name
  environment  = var.environment
}
```

In `infra/outputs.tf`:

```hcl
output "ecr_api_repo_url" {
  value = module.ecr.api_repo_url
}

output "ecr_ingestion_repo_url" {
  value = module.ecr.ingestion_repo_url
}
```

- [ ] **Step 5: Validate, plan, apply**

```bash
cd infra && terraform validate && terraform plan && terraform apply
```

Expected: `Plan: 6 to add` (3 repos + 3 lifecycle policies).

- [ ] **Step 6: Verify**

```bash
aws ecr describe-repositories --query "repositories[?contains(repositoryName,'stanford')].repositoryName"
```

Expected: 3 repository names listed.

- [ ] **Step 7: Commit**

```bash
git add infra/modules/ecr/ infra/main.tf infra/outputs.tf
git commit -m "feat(infra): add ECR repositories for Lambda container images"
```

---

### Task 6: AppConfig Module

**Files:**

- Create: `infra/modules/appconfig/main.tf`
- Create: `infra/modules/appconfig/variables.tf`
- Create: `infra/modules/appconfig/outputs.tf`

- [ ] **Step 1: Create `infra/modules/appconfig/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "embedding_model_id" {
  description = "Bedrock model ID for embeddings"
  type        = string
  default     = "amazon.titan-embed-text-v2:0"
}

variable "generative_model_id" {
  description = "Bedrock model ID for generative tasks"
  type        = string
  default     = "anthropic.claude-sonnet-4-5"
}
```

- [ ] **Step 2: Create `infra/modules/appconfig/main.tf`**

```hcl
resource "aws_appconfig_application" "main" {
  name        = "${var.project_name}-${var.environment}"
  description = "Stanford Course Finder configuration"
}

resource "aws_appconfig_environment" "main" {
  name           = var.environment
  application_id = aws_appconfig_application.main.id
}

resource "aws_appconfig_configuration_profile" "main" {
  application_id = aws_appconfig_application.main.id
  name           = "app-config"
  location_uri   = "hosted"
}

resource "aws_appconfig_hosted_configuration_version" "initial" {
  application_id           = aws_appconfig_application.main.id
  configuration_profile_id = aws_appconfig_configuration_profile.main.configuration_profile_id
  content_type             = "application/json"

  content = jsonencode({
    embeddingModelId        = var.embedding_model_id
    generativeModelId       = var.generative_model_id
    maxSearchResults        = 10
    enableSemanticReranking = false
    newPrereqEnforcement    = true
  })
}
```

- [ ] **Step 3: Create `infra/modules/appconfig/outputs.tf`**

```hcl
output "application_id" {
  value = aws_appconfig_application.main.id
}

output "environment_id" {
  value = aws_appconfig_environment.main.environment_id
}

output "configuration_profile_id" {
  value = aws_appconfig_configuration_profile.main.configuration_profile_id
}

output "initial_version_number" {
  value = tostring(aws_appconfig_hosted_configuration_version.initial.version_number)
}
```

- [ ] **Step 4: Add AppConfig module to `infra/main.tf`**

```hcl
module "appconfig" {
  source       = "./modules/appconfig"
  project_name = var.project_name
  environment  = var.environment
}
```

- [ ] **Step 5: Validate, plan, apply**

```bash
cd infra && terraform validate && terraform plan && terraform apply
```

Expected: `Plan: 4 to add` (application, environment, profile, hosted config version).

- [ ] **Step 6: Verify configuration is deployed**

```bash
aws appconfig list-applications --query "Items[?Name=='stanford-courses-prod'].Id" --output text
```

- [ ] **Step 7: Commit**

```bash
git add infra/modules/appconfig/ infra/main.tf
git commit -m "feat(infra): add AppConfig with initial model and feature flag config"
```

---

### Task 7: S3 Vectors Module

**Files:**

- Create: `infra/modules/s3vectors/main.tf`
- Create: `infra/modules/s3vectors/variables.tf`
- Create: `infra/modules/s3vectors/outputs.tf`

> **Note:** S3 Vectors support in the Terraform AWS provider requires version ~> 6.0+. If `aws_s3vectors_vector_bucket` is not available in the installed provider version, create the vector bucket and index using the AWS CLI (see fallback steps below).

- [ ] **Step 1: Create `infra/modules/s3vectors/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "vector_dimension" {
  description = "Embedding vector dimension (1024 for Titan Embed v2)"
  type        = number
  default     = 1024
}
```

- [ ] **Step 2: Create `infra/modules/s3vectors/main.tf`**

```hcl
resource "aws_s3vectors_vector_bucket" "main" {
  vector_bucket_name = "${var.project_name}-${var.environment}-vectors"
}

resource "aws_s3vectors_index" "course_embeddings" {
  vector_bucket_name = aws_s3vectors_vector_bucket.main.vector_bucket_name
  index_name         = "course-embeddings"
  data_type          = "float32"
  dimension          = var.vector_dimension
  distance_metric    = "cosine"
}
```

- [ ] **Step 3: Create `infra/modules/s3vectors/outputs.tf`**

```hcl
output "vector_bucket_name" {
  value = aws_s3vectors_vector_bucket.main.vector_bucket_name
}

output "index_name" {
  value = aws_s3vectors_index.course_embeddings.index_name
}
```

- [ ] **Step 4: Add S3 Vectors module to `infra/main.tf`**

```hcl
module "s3vectors" {
  source       = "./modules/s3vectors"
  project_name = var.project_name
  environment  = var.environment
}
```

- [ ] **Step 5: Validate and plan**

```bash
cd infra && terraform validate && terraform plan
```

If `Error: Invalid resource type "aws_s3vectors_vector_bucket"` → use the AWS CLI fallback:

```bash
# Fallback: create via AWS CLI
aws s3vectors create-vector-bucket \
  --vector-bucket-name stanford-courses-prod-vectors \
  --region us-east-1

aws s3vectors create-index \
  --vector-bucket-name stanford-courses-prod-vectors \
  --index-name course-embeddings \
  --data-type float32 \
  --dimension 1024 \
  --distance-metric cosine \
  --region us-east-1
```

If using the fallback, replace the module with a `data` source or `null_resource` that documents the manually created resources, and store the names in `outputs.tf` as local values.

- [ ] **Step 6: Apply (if Terraform resources available)**

```bash
terraform apply
```

- [ ] **Step 7: Verify**

```bash
aws s3vectors list-vector-buckets --region us-east-1
aws s3vectors list-indexes \
  --vector-bucket-name stanford-courses-prod-vectors \
  --region us-east-1
```

- [ ] **Step 8: Verify S3 Vectors IAM ARN format**

After deploying the ingestion Lambda (Plan 2), run a test invocation and check CloudWatch Logs for `AccessDeniedException`. If seen on S3 Vectors actions, the ARN format `arn:aws:s3vectors:${region}:${account_id}:vectorbucket/*` may need adjustment. Consult the current [AWS S3 Vectors IAM documentation](https://docs.aws.amazon.com/AmazonS3/latest/userguide/s3-vectors.html) for the authoritative ARN format before production deployment.

- [ ] **Step 9: Commit**

```bash
git add infra/modules/s3vectors/ infra/main.tf
git commit -m "feat(infra): add S3 Vectors bucket and course-embeddings index"
```

---

## Chunk 4: Lambda + IAM Module

### Task 8: Lambda IAM Roles

**Files:**

- Create: `infra/modules/lambda/iam.tf`
- Create: `infra/modules/lambda/variables.tf`

- [ ] **Step 1: Create `infra/modules/lambda/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  type = string
}

variable "courses_table_arn" {
  type = string
}

variable "applications_table_arn" {
  type = string
}

variable "users_table_arn" {
  type = string
}

variable "user_pool_arn" {
  type = string
}

variable "user_pool_id" {
  type = string
}

variable "appconfig_application_id" {
  type = string
}

variable "appconfig_environment_id" {
  type = string
}

variable "appconfig_profile_id" {
  type = string
}

variable "api_image_uri" {
  description = "ECR image URI for the API Lambda (set after first image push)"
  type        = string
  default     = ""
}

variable "ingestion_image_uri" {
  description = "ECR image URI for the Ingestion Lambda"
  type        = string
  default     = ""
}

variable "post_confirmation_image_uri" {
  description = "ECR image URI for the Post-Confirmation Lambda"
  type        = string
  default     = ""
}
```

- [ ] **Step 2: Create `infra/modules/lambda/iam.tf`**

```hcl
data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# ── API Lambda ────────────────────────────────────────────────────────────────

resource "aws_iam_role" "api" {
  name               = "${var.project_name}-${var.environment}-api-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "api_basic" {
  role       = aws_iam_role.api.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "api_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:Query",
        "dynamodb:BatchGetItem",
        "dynamodb:Scan"
      ]
      Resource = [
        var.courses_table_arn,
        var.applications_table_arn,
        "${var.applications_table_arn}/index/*",
        var.users_table_arn
      ]
    }]
  })
}

resource "aws_iam_role_policy" "api_bedrock" {
  name = "bedrock"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["bedrock:InvokeModel"]
      Resource = "arn:aws:bedrock:${var.aws_region}::foundation-model/*"
    }]
  })
}

resource "aws_iam_role_policy" "api_s3vectors" {
  name = "s3vectors"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3vectors:QueryVectors", "s3vectors:GetVectors"]
      Resource = "arn:aws:s3vectors:${var.aws_region}:${var.aws_account_id}:vectorbucket/*"
    }]
  })
}

resource "aws_iam_role_policy" "api_appconfig" {
  name = "appconfig"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "appconfig:GetLatestConfiguration",
        "appconfig:StartConfigurationSession"
      ]
      Resource = "arn:aws:appconfig:${var.aws_region}:${var.aws_account_id}:application/${var.appconfig_application_id}/environment/${var.appconfig_environment_id}/configuration/${var.appconfig_profile_id}"
    }]
  })
}

# ── Ingestion Lambda ──────────────────────────────────────────────────────────

resource "aws_iam_role" "ingestion" {
  name               = "${var.project_name}-${var.environment}-ingestion-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ingestion_basic" {
  role       = aws_iam_role.ingestion.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "ingestion_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["dynamodb:Scan", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem"]
        Resource = var.courses_table_arn
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:Query", "dynamodb:UpdateItem"]
        Resource = [var.applications_table_arn, "${var.applications_table_arn}/index/*"]
      }
    ]
  })
}

resource "aws_iam_role_policy" "ingestion_bedrock" {
  name = "bedrock"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["bedrock:InvokeModel"]
      Resource = "arn:aws:bedrock:${var.aws_region}::foundation-model/*"
    }]
  })
}

resource "aws_iam_role_policy" "ingestion_s3vectors" {
  name = "s3vectors"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3vectors:PutVectors", "s3vectors:DeleteVectors"]
      Resource = "arn:aws:s3vectors:${var.aws_region}:${var.aws_account_id}:vectorbucket/*"
    }]
  })
}

resource "aws_iam_role_policy" "ingestion_appconfig" {
  name = "appconfig"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "appconfig:GetLatestConfiguration",
        "appconfig:StartConfigurationSession"
      ]
      Resource = "arn:aws:appconfig:${var.aws_region}:${var.aws_account_id}:application/${var.appconfig_application_id}/environment/${var.appconfig_environment_id}/configuration/${var.appconfig_profile_id}"
    }]
  })
}

# ── Post-Confirmation Lambda ──────────────────────────────────────────────────

resource "aws_iam_role" "post_confirmation" {
  name               = "${var.project_name}-${var.environment}-post-confirmation-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "post_confirmation_basic" {
  role       = aws_iam_role.post_confirmation.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "post_confirmation_cognito" {
  name = "cognito"
  role = aws_iam_role.post_confirmation.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cognito-idp:AdminAddUserToGroup"]
      Resource = var.user_pool_arn
    }]
  })
}

# ── Admin Authorizer Lambda ───────────────────────────────────────────────────

resource "aws_iam_role" "admin_authorizer" {
  name               = "${var.project_name}-${var.environment}-admin-authorizer-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "admin_authorizer_basic" {
  role       = aws_iam_role.admin_authorizer.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}
```

- [ ] **Step 3: Validate IAM config**

```bash
cd infra && terraform validate
```

(No apply yet — Lambda functions defined next)

- [ ] **Step 4: Commit**

```bash
git add infra/modules/lambda/iam.tf infra/modules/lambda/variables.tf
git commit -m "feat(infra): add IAM roles and policies for all Lambda functions"
```

---

### Task 9: Lambda Functions

**Files:**

- Create: `infra/modules/lambda/main.tf`
- Create: `infra/modules/lambda/outputs.tf`

- [ ] **Step 1: Create `infra/modules/lambda/main.tf`**

> **Note:** Lambda functions using container images require at least one image to exist in ECR before the first `terraform apply`. A placeholder image must be pushed first. See Step 4.

```hcl
data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
}

# Note: SnapStart is not supported for container image Lambdas (package_type = "Image")
# API Lambda
resource "aws_lambda_function" "api" {
  count = var.api_image_uri != "" ? 1 : 0

  function_name = "${var.project_name}-${var.environment}-api"
  role          = aws_iam_role.api.arn
  package_type  = "Image"
  image_uri     = var.api_image_uri
  timeout       = 30
  memory_size   = 1024
  publish       = true

  environment {
    variables = {
      ENVIRONMENT              = var.environment
      APPCONFIG_APPLICATION_ID = var.appconfig_application_id
      APPCONFIG_ENVIRONMENT_ID = var.appconfig_environment_id
      APPCONFIG_PROFILE_ID     = var.appconfig_profile_id
    }
  }
}

resource "aws_lambda_alias" "api_prod" {
  count            = var.api_image_uri != "" ? 1 : 0
  name             = "prod"
  function_name    = aws_lambda_function.api[0].function_name
  function_version = aws_lambda_function.api[0].version
}

# Ingestion Lambda
resource "aws_lambda_function" "ingestion" {
  count = var.ingestion_image_uri != "" ? 1 : 0

  function_name = "${var.project_name}-${var.environment}-ingestion"
  role          = aws_iam_role.ingestion.arn
  package_type  = "Image"
  image_uri     = var.ingestion_image_uri
  timeout       = 300
  memory_size   = 512

  environment {
    variables = {
      ENVIRONMENT              = var.environment
      APPCONFIG_APPLICATION_ID = var.appconfig_application_id
      APPCONFIG_ENVIRONMENT_ID = var.appconfig_environment_id
      APPCONFIG_PROFILE_ID     = var.appconfig_profile_id
    }
  }
}

# Post-Confirmation Lambda
resource "aws_lambda_function" "post_confirmation" {
  count = var.post_confirmation_image_uri != "" ? 1 : 0

  function_name = "${var.project_name}-${var.environment}-post-confirmation"
  role          = aws_iam_role.post_confirmation.arn
  package_type  = "Image"
  image_uri     = var.post_confirmation_image_uri
  timeout       = 10
  memory_size   = 256

  environment {
    variables = {
      USER_POOL_ID = var.user_pool_id
    }
  }
}

# Cognito permission to invoke post-confirmation Lambda
resource "aws_lambda_permission" "cognito_post_confirmation" {
  count         = var.post_confirmation_image_uri != "" ? 1 : 0
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.post_confirmation[0].function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = var.user_pool_arn
}

# Admin authorizer Lambda (inline zip — no container needed, tiny function)
data "archive_file" "admin_authorizer" {
  type        = "zip"
  output_path = "${path.module}/admin_authorizer.zip"
  source {
    content  = <<-EOF
      import { createPublicKey } from "node:crypto";
      import { createVerify } from "node:crypto";

      const REGION = process.env.AWS_REGION;
      const USER_POOL_ID = process.env.USER_POOL_ID;
      const JWKS_URI = `https://cognito-idp.${REGION}.amazonaws.com/${USER_POOL_ID}/.well-known/jwks.json`;

      let cachedKeys = null;

      async function getPublicKeys() {
        if (cachedKeys) return cachedKeys;
        const res = await fetch(JWKS_URI);
        const { keys } = await res.json();
        cachedKeys = keys;
        return keys;
      }

      export const handler = async (event) => {
        try {
          const authHeader = event.headers?.authorization || event.headers?.Authorization || "";
          const token = authHeader.replace(/^Bearer\s+/i, "");
          if (!token) return { isAuthorized: false };

          const [headerB64] = token.split(".");
          const header = JSON.parse(Buffer.from(headerB64, "base64url").toString());

          const keys = await getPublicKeys();
          const key = keys.find(k => k.kid === header.kid);
          if (!key) return { isAuthorized: false };

          // Verify signature using the public key
          const [h, p, sig] = token.split(".");
          const pubKey = createPublicKey({ key, format: "jwk" });
          const verify = createVerify("RSA-SHA256");
          verify.update(`${h}.${p}`);
          const valid = verify.verify(pubKey, sig, "base64url");
          if (!valid) return { isAuthorized: false };

          const payload = JSON.parse(Buffer.from(p, "base64url").toString());

          // Check expiry
          if (payload.exp < Math.floor(Date.now() / 1000)) return { isAuthorized: false };

          // Check admins group
          const groups = payload["cognito:groups"] || [];
          return { isAuthorized: Array.isArray(groups) ? groups.includes("admins") : groups === "admins" };
        } catch {
          return { isAuthorized: false };
        }
      };
    EOF
    filename = "index.mjs"
  }
}

resource "aws_lambda_function" "admin_authorizer" {
  function_name    = "${var.project_name}-${var.environment}-admin-authorizer"
  role             = aws_iam_role.admin_authorizer.arn
  runtime          = "nodejs22.x"
  handler          = "index.handler"
  filename         = data.archive_file.admin_authorizer.output_path
  source_code_hash = data.archive_file.admin_authorizer.output_base64sha256
  timeout          = 5
  memory_size      = 128

  environment {
    variables = {
      USER_POOL_ID = var.user_pool_id
    }
  }
}
```

- [ ] **Step 2: Create `infra/modules/lambda/outputs.tf`**

```hcl
output "api_function_name" {
  value = length(aws_lambda_function.api) > 0 ? aws_lambda_function.api[0].function_name : ""
}

output "api_function_arn" {
  value = length(aws_lambda_function.api) > 0 ? aws_lambda_function.api[0].arn : ""
}

output "api_alias_arn" {
  value = length(aws_lambda_alias.api_prod) > 0 ? aws_lambda_alias.api_prod[0].arn : ""
}

output "ingestion_function_name" {
  value = length(aws_lambda_function.ingestion) > 0 ? aws_lambda_function.ingestion[0].function_name : ""
}

output "ingestion_function_arn" {
  value = length(aws_lambda_function.ingestion) > 0 ? aws_lambda_function.ingestion[0].arn : ""
}

output "post_confirmation_function_arn" {
  value = length(aws_lambda_function.post_confirmation) > 0 ? aws_lambda_function.post_confirmation[0].arn : ""
}

output "admin_authorizer_arn" {
  value = aws_lambda_function.admin_authorizer.arn
}

output "api_role_arn" {
  value = aws_iam_role.api.arn
}

output "ingestion_role_arn" {
  value = aws_iam_role.ingestion.arn
}
```

- [ ] **Step 3: Push placeholder images to ECR** (required before Lambda can be created)

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Push a placeholder image for each repo
for repo in api ingestion post-confirmation; do
  docker pull public.ecr.aws/lambda/java:25
  docker tag public.ecr.aws/lambda/java:25 \
    <account-id>.dkr.ecr.us-east-1.amazonaws.com/stanford-courses-prod-${repo}:placeholder
  docker push \
    <account-id>.dkr.ecr.us-east-1.amazonaws.com/stanford-courses-prod-${repo}:placeholder
done
```

- [ ] **Step 4: Add Lambda module to `infra/main.tf`**

```hcl
data "aws_caller_identity" "current" {}

module "lambda" {
  source       = "./modules/lambda"
  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id

  courses_table_arn      = module.dynamodb.courses_table_arn
  applications_table_arn = module.dynamodb.applications_table_arn
  users_table_arn        = module.dynamodb.users_table_arn
  user_pool_arn          = module.cognito.user_pool_arn
  user_pool_id           = module.cognito.user_pool_id

  appconfig_application_id = module.appconfig.application_id
  appconfig_environment_id = module.appconfig.environment_id
  appconfig_profile_id     = module.appconfig.configuration_profile_id

  api_image_uri               = "${module.ecr.api_repo_url}:placeholder"
  ingestion_image_uri         = "${module.ecr.ingestion_repo_url}:placeholder"
  post_confirmation_image_uri = "${module.ecr.post_confirmation_repo_url}:placeholder"
}
```

- [ ] **Step 5: Validate, plan, apply**

```bash
cd infra && terraform validate && terraform plan && terraform apply
```

Expected: Lambda functions, alias, permissions, admin authorizer created.

- [ ] **Step 6: Verify**

```bash
aws lambda list-functions --query "Functions[?contains(FunctionName,'stanford')].FunctionName"
```

Expected: 4 functions listed (api, ingestion, post-confirmation, admin-authorizer).

- [ ] **Step 7: Commit**

```bash
git add infra/modules/lambda/ infra/main.tf
git commit -m "feat(infra): add Lambda functions with IAM roles and prod alias"
```

---

## Chunk 5: API Gateway Module

### Task 10: API Gateway HTTP API

**Files:**

- Create: `infra/modules/api-gateway/main.tf`
- Create: `infra/modules/api-gateway/variables.tf`
- Create: `infra/modules/api-gateway/outputs.tf`

- [ ] **Step 1: Create `infra/modules/api-gateway/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "cognito_user_pool_endpoint" {
  type = string
}

variable "cognito_client_id" {
  type = string
}

variable "api_lambda_alias_arn" {
  type = string
}

variable "admin_authorizer_lambda_arn" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  type = string
}
```

- [ ] **Step 2: Create `infra/modules/api-gateway/main.tf`**

```hcl
resource "aws_apigatewayv2_api" "main" {
  name          = "${var.project_name}-${var.environment}"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers = ["Content-Type", "Authorization"]
    max_age       = 300
  }
}

# Cognito JWT Authorizer (all routes)
resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.main.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito-jwt"

  jwt_configuration {
    audience = [var.cognito_client_id]
    issuer   = "https://${var.cognito_user_pool_endpoint}"
  }
}

# Admin Lambda Authorizer
resource "aws_apigatewayv2_authorizer" "admin" {
  api_id                            = aws_apigatewayv2_api.main.id
  authorizer_type                   = "REQUEST"
  authorizer_uri                    = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.admin_authorizer_lambda_arn}/invocations"
  identity_sources                  = ["$request.header.Authorization"]
  name                              = "admin-group-authorizer"
  authorizer_payload_format_version = "2.0"
  enable_simple_responses           = true
}

# Permission for API Gateway to invoke the admin authorizer Lambda
resource "aws_lambda_permission" "api_gateway_admin_authorizer" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = var.admin_authorizer_lambda_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*"
}

# Integration — API Lambda alias
resource "aws_apigatewayv2_integration" "api_lambda" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = var.api_lambda_alias_arn
  payload_format_version = "2.0"
}

# Permission for API Gateway to invoke API Lambda alias
resource "aws_lambda_permission" "api_gateway_api_lambda" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = var.api_lambda_alias_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*"
}

# Routes — student endpoints (Cognito JWT authorizer)
locals {
  student_routes = [
    "GET /courses/search",
    "GET /courses/{courseId}",
    "GET /applications",
    "POST /applications/{courseId}",
    "DELETE /applications/{courseId}",
    "GET /profile/completed-courses",
    "PUT /profile/completed-courses",
  ]
}

resource "aws_apigatewayv2_route" "student" {
  for_each = toset(local.student_routes)

  api_id             = aws_apigatewayv2_api.main.id
  route_key          = each.key
  target             = "integrations/${aws_apigatewayv2_integration.api_lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

# Routes — admin endpoints (admin Lambda authorizer)
locals {
  admin_routes = [
    "GET /admin/courses",
    "GET /admin/courses/{courseId}/applicants",
  ]
}

resource "aws_apigatewayv2_route" "admin" {
  for_each = toset(local.admin_routes)

  api_id             = aws_apigatewayv2_api.main.id
  route_key          = each.key
  target             = "integrations/${aws_apigatewayv2_integration.api_lambda.id}"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.admin.id
}

# Stage
resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 100
    throttling_rate_limit  = 50
  }
}
```

- [ ] **Step 3: Create `infra/modules/api-gateway/outputs.tf`**

```hcl
output "api_endpoint" {
  value = aws_apigatewayv2_stage.default.invoke_url
}

output "api_id" {
  value = aws_apigatewayv2_api.main.id
}
```

- [ ] **Step 4: Add API Gateway module to `infra/main.tf`**

```hcl
module "api_gateway" {
  source       = "./modules/api-gateway"
  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id

  cognito_user_pool_endpoint = module.cognito.user_pool_endpoint
  cognito_client_id          = module.cognito.client_id
  api_lambda_alias_arn       = module.lambda.api_alias_arn
  admin_authorizer_lambda_arn = module.lambda.admin_authorizer_arn
}
```

In `infra/outputs.tf`:

```hcl
output "api_endpoint" {
  value = module.api_gateway.api_endpoint
}
```

- [ ] **Step 5: Validate, plan, apply**

```bash
cd infra && terraform validate && terraform plan && terraform apply
```

Expected: API Gateway, authorizers, routes, stage, permissions created.

- [ ] **Step 6: Verify — test a route returns 401 (not 404)**

```bash
API_URL=$(terraform output -raw api_endpoint)
curl -s -o /dev/null -w "%{http_code}" "${API_URL}/courses/search?q=machine+learning"
```

Expected: `401` (Unauthorized — JWT required, not 404 which would mean route not found)

- [ ] **Step 7: Commit**

```bash
git add infra/modules/api-gateway/ infra/main.tf infra/outputs.tf
git commit -m "feat(infra): add API Gateway HTTP API with Cognito and admin authorizers"
```

---

## Chunk 6: EventBridge + Amplify + Final Wiring

### Task 11: EventBridge Module

**Files:**

- Create: `infra/modules/eventbridge/main.tf`
- Create: `infra/modules/eventbridge/variables.tf`
- Create: `infra/modules/eventbridge/outputs.tf`

- [ ] **Step 1: Create `infra/modules/eventbridge/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "ingestion_lambda_arn" {
  type = string
}

variable "schedule_expression" {
  description = "EventBridge schedule expression for ingestion (cron or rate)"
  type        = string
  default     = "cron(0 2 ? * SUN *)"  # Every Sunday at 2AM UTC
}
```

- [ ] **Step 2: Create `infra/modules/eventbridge/main.tf`**

```hcl
# EventBridge Scheduler requires a dedicated IAM role to invoke the target Lambda

resource "aws_iam_role" "scheduler" {
  name = "${var.project_name}-${var.environment}-eventbridge-scheduler"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "scheduler.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "scheduler_invoke" {
  name = "invoke-ingestion"
  role = aws_iam_role.scheduler.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = var.ingestion_lambda_arn
    }]
  })
}

resource "aws_scheduler_schedule" "ingestion" {
  name       = "${var.project_name}-${var.environment}-ingestion"
  group_name = "default"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.schedule_expression
  schedule_expression_timezone = "UTC"

  target {
    arn      = var.ingestion_lambda_arn
    role_arn = aws_iam_role.scheduler.arn
  }
}
```

- [ ] **Step 3: Create `infra/modules/eventbridge/outputs.tf`**

```hcl
output "schedule_arn" {
  value = aws_scheduler_schedule.ingestion.arn
}
```

- [ ] **Step 4: Add EventBridge module to `infra/main.tf`**

```hcl
module "eventbridge" {
  source       = "./modules/eventbridge"
  project_name = var.project_name
  environment  = var.environment

  ingestion_lambda_arn = module.lambda.ingestion_function_arn
}
```

- [ ] **Step 5: Apply and verify**

```bash
cd infra && terraform apply
aws scheduler list-schedules --group-name default --query "Schedules[?Name=='stanford-courses-prod-ingestion']"
```

Expected: schedule listed with `"State": "ENABLED"`

- [ ] **Step 6: Commit**

```bash
git add infra/modules/eventbridge/ infra/main.tf
git commit -m "feat(infra): add EventBridge weekly schedule for ingestion Lambda"
```

---

### Task 12: Amplify Module

**Files:**

- Create: `infra/modules/amplify/main.tf`
- Create: `infra/modules/amplify/variables.tf`
- Create: `infra/modules/amplify/outputs.tf`

- [ ] **Step 1: Create `infra/modules/amplify/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "github_owner" {
  type = string
}

variable "github_repo" {
  type = string
}

variable "github_branch" {
  type = string
}

variable "github_access_token" {
  type      = string
  sensitive = true
}

variable "api_endpoint" {
  type = string
}

variable "cognito_user_pool_id" {
  type = string
}

variable "cognito_client_id" {
  type = string
}
```

- [ ] **Step 2: Create `infra/modules/amplify/main.tf`**

```hcl
resource "aws_amplify_app" "frontend" {
  name         = "${var.project_name}-${var.environment}"
  repository   = "https://github.com/${var.github_owner}/${var.github_repo}"
  access_token = var.github_access_token

  build_spec = <<-EOT
    version: 1
    frontend:
      phases:
        preBuild:
          commands:
            - cd frontend
            - npm ci
        build:
          commands:
            - npm run build
      artifacts:
        baseDirectory: frontend/.next
        files:
          - '**/*'
      cache:
        paths:
          - frontend/node_modules/**/*
  EOT

  environment_variables = {
    NEXT_PUBLIC_API_URL            = var.api_endpoint
    NEXT_PUBLIC_COGNITO_USER_POOL  = var.cognito_user_pool_id
    NEXT_PUBLIC_COGNITO_CLIENT_ID  = var.cognito_client_id
    NEXT_PUBLIC_AWS_REGION         = "us-east-1"
  }
}

resource "aws_amplify_branch" "main" {
  app_id      = aws_amplify_app.frontend.id
  branch_name = var.github_branch

  framework = "Next.js - SSR"
  stage     = "PRODUCTION"

  enable_auto_build = true
}
```

- [ ] **Step 3: Create `infra/modules/amplify/outputs.tf`**

```hcl
output "app_id" {
  value = aws_amplify_app.frontend.id
}

output "default_domain" {
  value = aws_amplify_app.frontend.default_domain
}

output "frontend_url" {
  value = "https://${var.github_branch}.${aws_amplify_app.frontend.default_domain}"
}
```

- [ ] **Step 4: Add Amplify module to `infra/main.tf`**

```hcl
module "amplify" {
  source       = "./modules/amplify"
  project_name = var.project_name
  environment  = var.environment

  github_owner        = var.github_owner
  github_repo         = var.github_repo
  github_branch       = var.github_branch
  github_access_token = var.github_access_token

  api_endpoint         = module.api_gateway.api_endpoint
  cognito_user_pool_id = module.cognito.user_pool_id
  cognito_client_id    = module.cognito.client_id
}
```

In `infra/outputs.tf`:

```hcl
output "frontend_url" {
  value = module.amplify.frontend_url
}
```

- [ ] **Step 5: Apply and verify**

```bash
cd infra && terraform apply
terraform output frontend_url
```

Expected: Amplify app URL printed.

- [ ] **Step 6: Commit**

```bash
git add infra/modules/amplify/ infra/main.tf infra/outputs.tf
git commit -m "feat(infra): add Amplify app for React frontend with Cognito env vars"
```

---

### Task 13: Wire Cognito Post-Confirmation Lambda

> **Note:** This creates a dependency between the Cognito and Lambda modules (Cognito needs the Lambda ARN, Lambda needed the Cognito User Pool ARN). Terraform handles this via the two-phase apply already described. After this step, do not attempt `terraform destroy` without first nullifying the `post_confirmation_lambda_arn` in the Cognito module call (set to `""`), applying, then destroying — otherwise Terraform will error on cycle detection during destroy ordering.

After the `post-confirmation` Lambda is deployed (Plan 2), update Cognito to use it.

- [ ] **Step 1: Update `infra/main.tf` Cognito module call**

Add the `post_confirmation_lambda_arn` argument:

```hcl
module "cognito" {
  source       = "./modules/cognito"
  project_name = var.project_name
  environment  = var.environment

  post_confirmation_lambda_arn = module.lambda.post_confirmation_function_arn
}
```

- [ ] **Step 2: Apply**

```bash
cd infra && terraform apply
```

Expected: Cognito User Pool updated with Lambda trigger.

- [ ] **Step 3: Verify trigger is set**

```bash
aws cognito-idp describe-user-pool \
  --user-pool-id $(terraform output -raw cognito_user_pool_id) \
  --query "UserPool.LambdaConfig"
```

Expected: `"PostConfirmation"` key with the Lambda ARN.

- [ ] **Step 4: Commit**

```bash
git add infra/main.tf
git commit -m "feat(infra): wire post-confirmation Lambda trigger to Cognito"
```

---

## Chunk 7: Observability Module

### Task 14: Observability — X-Ray, Alarms, Dashboard

**Files:**

- Create: `infra/modules/observability/variables.tf`
- Create: `infra/modules/observability/main.tf`
- Create: `infra/modules/observability/outputs.tf`

- [ ] **Step 1: Create `infra/modules/observability/variables.tf`**

```hcl
variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "alarm_email" {
  description = "Email address for CloudWatch alarm notifications"
  type        = string
}

variable "api_lambda_name" {
  type = string
}

variable "ingestion_lambda_name" {
  type = string
}

variable "api_gateway_id" {
  type = string
}

variable "appconfig_application_id" {
  type = string
}

variable "appconfig_environment_id" {
  type = string
}

variable "appconfig_profile_id" {
  type = string
}

variable "appconfig_version_number" {
  description = "AppConfig hosted configuration version number to deploy"
  type        = string
}

variable "courses_table_name" {
  type = string
}

variable "applications_table_name" {
  type = string
}

variable "users_table_name" {
  type = string
}
```

- [ ] **Step 2: Create `infra/modules/observability/main.tf`**

```hcl
# ── SNS Topic for alarm notifications ────────────────────────────────────────

resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-${var.environment}-alarms"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# ── CloudWatch Log Groups (explicit, 30-day retention) ───────────────────────

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/lambda/${var.api_lambda_name}"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "ingestion" {
  name              = "/aws/lambda/${var.ingestion_lambda_name}"
  retention_in_days = 30
}

# ── CloudWatch Alarms ─────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "api_error_rate" {
  alarm_name          = "${var.project_name}-${var.environment}-api-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  threshold           = 5
  alarm_description   = "API Lambda error rate > 5% over 5 minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "error_rate"
    expression  = "100 * errors / invocations"
    label       = "Error Rate (%)"
    return_data = true
  }

  metric_query {
    id = "errors"
    metric {
      namespace   = "AWS/Lambda"
      metric_name = "Errors"
      dimensions  = { FunctionName = var.api_lambda_name }
      period      = 300
      stat        = "Sum"
    }
  }

  metric_query {
    id = "invocations"
    metric {
      namespace   = "AWS/Lambda"
      metric_name = "Invocations"
      dimensions  = { FunctionName = var.api_lambda_name }
      period      = 300
      stat        = "Sum"
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "api_p99_duration" {
  alarm_name          = "${var.project_name}-${var.environment}-api-p99-duration"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  threshold           = 10000
  alarm_description   = "API Lambda P99 duration > 10s over 5 minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  treat_missing_data  = "notBreaching"

  namespace          = "AWS/Lambda"
  metric_name        = "Duration"
  dimensions         = { FunctionName = var.api_lambda_name }
  period             = 300
  extended_statistic = "p99"
}

resource "aws_cloudwatch_metric_alarm" "ingestion_errors" {
  alarm_name          = "${var.project_name}-${var.environment}-ingestion-errors"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  threshold           = 1
  alarm_description   = "Ingestion Lambda error detected"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  treat_missing_data  = "notBreaching"

  namespace   = "AWS/Lambda"
  metric_name = "Errors"
  dimensions  = { FunctionName = var.ingestion_lambda_name }
  period      = 300
  statistic   = "Sum"
}

resource "aws_cloudwatch_metric_alarm" "api_gateway_5xx" {
  alarm_name          = "${var.project_name}-${var.environment}-apigw-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  threshold           = 1
  alarm_description   = "API Gateway 5xx rate > 1% over 5 minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "error_rate"
    expression  = "100 * errors / requests"
    label       = "5xx Rate (%)"
    return_data = true
  }

  metric_query {
    id = "errors"
    metric {
      namespace   = "AWS/ApiGateway"
      metric_name = "5XXError"
      dimensions  = { ApiId = var.api_gateway_id }
      period      = 300
      stat        = "Sum"
    }
  }

  metric_query {
    id = "requests"
    metric {
      namespace   = "AWS/ApiGateway"
      metric_name = "Count"
      dimensions  = { ApiId = var.api_gateway_id }
      period      = 300
      stat        = "Sum"
    }
  }
}

# ── AppConfig alarm-based rollback ────────────────────────────────────────────

resource "aws_appconfig_deployment_strategy" "alarm_rollback" {
  name                           = "${var.project_name}-with-rollback"
  description                    = "5-minute bake with alarm-based rollback"
  deployment_duration_in_minutes = 5
  final_bake_time_in_minutes     = 5
  growth_factor                  = 100
  growth_type                    = "LINEAR"
  replicate_to                   = "NONE"
}

resource "aws_appconfig_deployment" "with_rollback" {
  application_id           = var.appconfig_application_id
  environment_id           = var.appconfig_environment_id
  configuration_profile_id = var.appconfig_profile_id
  configuration_version    = var.appconfig_version_number
  deployment_strategy_id   = aws_appconfig_deployment_strategy.alarm_rollback.id
  description              = "Production deployment with alarm-based rollback"

  monitor {
    alarm_arn      = aws_cloudwatch_metric_alarm.api_error_rate.arn
    alarm_role_arn = aws_iam_role.appconfig_monitor.arn
  }

  # Deployed once by Terraform on initial setup.
  # Subsequent config updates must be deployed manually (console/CLI) or via a
  # dedicated CI step — as described in spec Section 9 "How to change configuration".
  # Prevents re-triggering a deployment on every `terraform apply`.
  lifecycle {
    ignore_changes = all
  }
}

resource "aws_iam_role" "appconfig_monitor" {
  name = "${var.project_name}-${var.environment}-appconfig-monitor"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "appconfig.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "appconfig_monitor_cloudwatch" {
  name = "cloudwatch-read"
  role = aws_iam_role.appconfig_monitor.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cloudwatch:DescribeAlarms"]
      Resource = aws_cloudwatch_metric_alarm.api_error_rate.arn
    }]
  })
}

# ── CloudWatch Dashboard ──────────────────────────────────────────────────────

resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title  = "API Lambda — Invocations & Errors"
          period = 300
          stat   = "Sum"
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", var.api_lambda_name],
            ["AWS/Lambda", "Errors", "FunctionName", var.api_lambda_name]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title  = "API Lambda — P50/P99 Duration"
          period = 300
          metrics = [
            [{ expression = "SELECT PERCENTILE(Duration, 50) FROM SCHEMA(\"AWS/Lambda\", FunctionName) WHERE FunctionName = '${var.api_lambda_name}'", label = "P50", id = "p50" }],
            [{ expression = "SELECT PERCENTILE(Duration, 99) FROM SCHEMA(\"AWS/Lambda\", FunctionName) WHERE FunctionName = '${var.api_lambda_name}'", label = "P99", id = "p99" }]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title  = "API Gateway — Requests & 5xx"
          period = 300
          stat   = "Sum"
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiId", var.api_gateway_id],
            ["AWS/ApiGateway", "5XXError", "ApiId", var.api_gateway_id],
            ["AWS/ApiGateway", "4XXError", "ApiId", var.api_gateway_id]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title  = "Ingestion Lambda — Invocations & Errors"
          period = 300
          stat   = "Sum"
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", var.ingestion_lambda_name],
            ["AWS/Lambda", "Errors", "FunctionName", var.ingestion_lambda_name],
            ["AWS/Lambda", "Duration", "FunctionName", var.ingestion_lambda_name]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title  = "DynamoDB — Consumed Read Capacity Units"
          period = 300
          stat   = "Sum"
          metrics = [
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", var.courses_table_name],
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", var.applications_table_name],
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", var.users_table_name]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title  = "DynamoDB — Consumed Write Capacity Units"
          period = 300
          stat   = "Sum"
          metrics = [
            ["AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", var.courses_table_name],
            ["AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", var.applications_table_name],
            ["AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", var.users_table_name]
          ]
        }
      }
    ]
  })
}
```

- [ ] **Step 3: Create `infra/modules/observability/outputs.tf`**

```hcl
output "alarm_topic_arn" {
  value = aws_sns_topic.alarms.arn
}

output "api_error_alarm_arn" {
  value = aws_cloudwatch_metric_alarm.api_error_rate.arn
}

output "dashboard_name" {
  value = aws_cloudwatch_dashboard.main.dashboard_name
}
```

- [ ] **Step 4: Update Lambda IAM to include X-Ray and CloudWatch permissions**

Add to `infra/modules/lambda/iam.tf` — append to the API Lambda role:

```hcl
resource "aws_iam_role_policy" "api_xray" {
  name = "xray"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["xray:PutTraceSegments", "xray:PutTelemetryRecords"]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role_policy" "ingestion_xray" {
  name = "xray"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["xray:PutTraceSegments", "xray:PutTelemetryRecords"]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role_policy" "ingestion_cloudwatch" {
  name = "cloudwatch"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cloudwatch:PutMetricData"]
      Resource = "*"
    }]
  })
}
```

- [ ] **Step 5: Enable X-Ray tracing on Lambda functions**

Add `tracing_config` to each Lambda function in `infra/modules/lambda/main.tf`:

For `aws_lambda_function.api`:

```hcl
  tracing_config {
    mode = "Active"
  }
```

For `aws_lambda_function.ingestion`:

```hcl
  tracing_config {
    mode = "Active"
  }
```

- [ ] **Step 6: Add observability module to `infra/main.tf`**

```hcl
module "observability" {
  source       = "./modules/observability"
  project_name = var.project_name
  environment  = var.environment

  alarm_email           = var.alarm_email
  api_lambda_name       = module.lambda.api_function_name
  ingestion_lambda_name = module.lambda.ingestion_function_name
  api_gateway_id        = module.api_gateway.api_id

  appconfig_application_id = module.appconfig.application_id
  appconfig_environment_id = module.appconfig.environment_id
  appconfig_profile_id     = module.appconfig.configuration_profile_id
  appconfig_version_number = module.appconfig.initial_version_number

  courses_table_name      = module.dynamodb.courses_table_name
  applications_table_name = module.dynamodb.applications_table_name
  users_table_name        = module.dynamodb.users_table_name
}
```

Also add to `infra/variables.tf`:

```hcl
variable "alarm_email" {
  description = "Email address for CloudWatch alarm notifications"
  type        = string
}
```

- [ ] **Step 7: Validate, plan, apply**

```bash
cd infra && terraform validate && terraform plan && terraform apply
```

Expected: SNS topic + subscription, 4 alarms, log groups, dashboard, AppConfig strategy, X-Ray + CloudWatch IAM policies, Lambda tracing enabled.

- [ ] **Step 8: Confirm SNS subscription**

Check your email inbox and click the confirmation link in the "AWS Notification - Subscription Confirmation" email.

- [ ] **Step 9: Verify dashboard exists**

```bash
aws cloudwatch list-dashboards --query "DashboardEntries[?DashboardName=='stanford-courses-prod'].DashboardName"
```

Expected: `"stanford-courses-prod"`

- [ ] **Step 10: Verify alarms**

```bash
aws cloudwatch describe-alarms \
  --alarm-name-prefix stanford-courses-prod \
  --query "MetricAlarms[].AlarmName"
```

Expected: 4 alarm names listed.

- [ ] **Step 11: Commit**

```bash
git add infra/modules/observability/ infra/modules/lambda/iam.tf \
        infra/modules/lambda/main.tf infra/main.tf infra/variables.tf
git commit -m "feat(infra): add observability — X-Ray, CloudWatch alarms, dashboard, SNS"
```

---

### Task 15: Final Verification

- [ ] **Step 1: Full plan shows no changes**

```bash
cd infra && terraform plan
```

Expected: `No changes. Your infrastructure matches the configuration.`

- [ ] **Step 2: Print all outputs**

```bash
terraform output
```

Expected: All outputs printed — API endpoint, Cognito IDs, ECR URLs, frontend URL.

- [ ] **Step 3: Save terraform output to a file for backend Plan 2 reference**

```bash
terraform output -json > infra-outputs.json
```

- [ ] **Step 4: Final commit**

```bash
git add infra-outputs.json
git commit -m "chore(infra): save terraform outputs for backend configuration reference"
```

---

## Summary

After completing all tasks:

| Resource | Name pattern |
| --- | --- |
| S3 state bucket | `stanford-courses-tfstate-<account-id>` |
| Cognito User Pool | `stanford-courses-prod` |
| DynamoDB tables | `stanford-courses-prod-{courses,applications,users}` |
| ECR repos | `stanford-courses-prod-{api,ingestion,post-confirmation}` |
| Lambda functions | `stanford-courses-prod-{api,ingestion,post-confirmation,admin-authorizer}` |
| API Gateway | HTTP API with JWT + admin authorizer, all 9 routes |
| EventBridge | Weekly Sunday 2AM UTC ingestion trigger |
| Amplify | Frontend app connected to GitHub |
| AppConfig | Initial config with model IDs + feature flags |
| Observability | X-Ray tracing, CloudWatch log groups/alarms/dashboard, SNS |

The infrastructure is ready for **Plan 2: Backend** which will build and push real Docker images to replace the placeholders.
