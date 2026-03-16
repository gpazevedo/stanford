# Infrastructure

Terraform 1.14, AWS provider 6.36. All resources in `us-east-1`.

## Architecture

```text
GitHub Actions (OIDC)
        │
        ▼
  ECR (3 repos)
        │
        ▼
  Lambda (api / ingestion / post-confirmation)
        │                    │
   API Gateway          EventBridge (nightly schedule)
        │
   Cognito JWT
        │
   DynamoDB (courses / applications / users)
        │
   S3 Vectors (course embeddings)
        │
   AppConfig (model IDs, feature flags)
        │
   Amplify (frontend, auto-deploy from GitHub)

CloudWatch Alarms → SNS → email
```

## Modules

| Module | Resources |
| --- | --- |
| `cognito` | User pool + app client, email verification, post-confirmation Lambda trigger |
| `dynamodb` | `courses`, `applications`, `users` tables (PAY_PER_REQUEST, PITR enabled) |
| `ecr` | Three image repos: `api`, `ingestion`, `post-confirmation` (keep last 10 images) |
| `appconfig` | Application, environment, hosted config profile with model IDs and feature flags |
| `s3vectors` | Vector bucket + `course-embeddings` index (cosine, float32) |
| `lambda` | Three Lambda functions (container image), IAM roles, `prod` aliases |
| `api-gateway` | HTTP API, Cognito JWT authorizer, admin Lambda authorizer |
| `eventbridge` | Scheduler rule triggering ingestion Lambda nightly |
| `amplify` | Amplify app connected to GitHub, deploys `frontend/` on push to `main` |
| `observability` | CloudWatch log groups (30-day retention), error-rate and throttle alarms, SNS email |
| `github-oidc` | IAM OIDC provider + `stanford-courses-ci` role for GitHub Actions |

## State

Remote state in S3 with native file locking (`use_lockfile = true`). The bucket name is auto-derived from the AWS account ID by `init.sh` — no manual configuration needed.

## First-time Setup

### Prerequisites

- AWS CLI configured with admin credentials (`aws sts get-caller-identity` should succeed)
- Terraform 1.14+
- GitHub fine-grained PAT (see [GitHub token](#github-token))

### Variables

Create `infra/terraform.tfvars` (excluded from git):

```hcl
github_owner        = "your-github-username"
github_org          = "your-github-username"   # same value, used for OIDC trust
github_repo         = "stanford"
github_access_token = "github_pat_xxxx..."
alarm_email         = "you@example.com"
```

Optional overrides (defaults shown):

```hcl
aws_region           = "us-east-1"
environment          = "prod"
github_branch        = "main"
create_oidc_provider = true   # set false if OIDC provider already exists in the account
```

### Apply

```bash
cd infra
bash init.sh          # creates S3 state bucket + placeholder ECR images if absent, runs terraform init
terraform apply
```

`init.sh` does three things automatically:
1. Creates the S3 state bucket `stanford-courses-tfstate-<account-id>` with versioning if it doesn't exist
2. Pushes a minimal placeholder image (`public.ecr.aws/lambda/provided:al2`) to each ECR repo if empty — Lambda requires a valid image URI at creation time; CI/CD replaces these on the first backend deploy
3. Runs `terraform init -backend-config="bucket=..."`

> **OIDC provider already exists?** If your AWS account already has a GitHub OIDC provider, add `create_oidc_provider = false` to `terraform.tfvars` before applying.

### After Apply

Retrieve the outputs and configure GitHub Actions:

```bash
# Add the CI role ARN as a GitHub secret
gh secret set AWS_OIDC_ROLE_ARN \
  --repo your-github-username/stanford \
  --body "$(terraform output -raw ci_role_arn)"
```

The Amplify app is connected to GitHub automatically. The first deploy triggers on the next push to `main` in `frontend/`.

## GitHub Token

The Amplify module needs a fine-grained PAT to pull the repo and register a webhook.

1. GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens
2. Scope to the `stanford` repository only
3. Permissions: **Contents: Read**, **Webhooks: Read & Write**

## CI/CD

After setup, the following workflows run automatically:

| Event | Workflow | What happens |
| --- | --- | --- |
| PR touches `backend/**` | `backend.yml` | Build + test (required check) |
| Push to `main` touches `backend/**` | `backend.yml` | Build Docker images → push to ECR → update Lambda `prod` aliases |
| PR touches `infra/**` | `infra.yml` | `terraform validate` + `terraform plan` posted as PR comment |
| Push to `main` touches `infra/**` | `infra.yml` | `terraform apply -auto-approve` |
| Push to `main` touches `frontend/**` | Amplify | Auto-deployed by Amplify (no workflow needed) |

## Updating AppConfig

Feature flags and model IDs live in AppConfig. Edit the initial configuration version in `modules/appconfig/main.tf` and run `terraform apply` — no code deploy needed.

Current defaults:

```json
{
  "embeddingModelId": "...",
  "generativeModelId": "...",
  "maxSearchResults": 10,
  "enableSemanticReranking": false,
  "newPrereqEnforcement": true
}
```
