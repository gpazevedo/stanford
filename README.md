# Stanford Course Finder

A web application for Stanford CS-PMN students to discover courses with natural language search, apply, and track applications. Fully serverless on AWS.

## Stack

| Layer | Technology |
| --- | --- |
| Frontend | Next.js 16, TypeScript, Tailwind CSS 4, AWS Amplify |
| API | Spring Boot 4, Spring AI 2, Java 25 — AWS Lambda |
| Auth | AWS Cognito (email self-registration) |
| Search | Amazon Bedrock embeddings → AWS S3 Vectors KNN |
| Database | Amazon DynamoDB (on-demand) |
| Config | AWS AppConfig (model IDs, feature flags) |
| IaC | Terraform 1.14 |
| CI/CD | GitHub Actions (OIDC) |

## Project Structure

```text
backend/
├── api/               Spring Boot 4 API Lambda (courses, applications, search)
├── ingestion/         Scrapes Stanford bulletin, embeds + stores courses
└── post-confirmation/ Cognito trigger — adds new users to "students" group
frontend/              Next.js app (course search, apply, application tracking)
infra/                 Terraform — all AWS resources
.github/workflows/
├── backend.yml        PR: build+test  /  merge: build images → ECR → Lambda
└── infra.yml          PR: terraform plan comment  /  merge: terraform apply
```

## Local Development

### Backend

Requires Java 25.

```bash
cd backend
./gradlew build test          # build all modules + run tests
./gradlew :api:bootRun        # run API locally (port 8080)
```

### Frontend

Requires Node.js 20+.

```bash
cd frontend
npm install
npm run dev                   # http://localhost:3000
npm test                      # Vitest unit tests
```

## Infrastructure

See [`infra/infra.md`](infra/infra.md) for full setup guide.

**Quick start:**

```bash
cp infra/terraform.tfvars.example infra/terraform.tfvars
# fill in github_owner, github_repo, github_access_token, alarm_email

cd infra
bash init.sh        # creates S3 state bucket, runs terraform init
terraform apply
```

After apply, set the CI role secret:

```bash
gh secret set AWS_OIDC_ROLE_ARN \
  --repo <owner>/stanford \
  --body "$(terraform output -raw ci_role_arn)"
```

## CI/CD

Workflows trigger automatically on path-filtered push/PR events. Frontend is auto-deployed by Amplify on every push to `main` — no workflow needed.

The `AWS_OIDC_ROLE_ARN` GitHub secret must be set before workflows can authenticate to AWS (see infra setup above).
