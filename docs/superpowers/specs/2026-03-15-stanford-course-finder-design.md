# Stanford Course Finder — Design Spec

**Date:** 2026-03-15
**Status:** Approved
**Scope:** CS Professional Master's Program (CS-PMN) — Stanford University

---

## 1. Overview

A production-grade web application that allows Stanford CS-PMN students to discover courses using natural language search, apply for them, and track their applications. Built on a fully serverless AWS architecture using Spring Boot 4, Spring AI 2, and Java.

---

## 2. Architecture

### Pattern

Fully serverless — two AWS Lambda functions backed by API Gateway, with a React/Next.js frontend on AWS Amplify. All resources deployed to **`us-east-1`** (required for S3 Vectors, Bedrock Titan Embed v2, and Claude Sonnet availability).

### Components

| Component | Technology |
| --- | --- |
| API Lambda | Spring Boot 4 + Spring AI 2 (Java 25) |
| Ingestion Lambda | Java 25, EventBridge weekly trigger |
| Post-Confirmation Lambda | Java 25, Cognito trigger — assigns `students` group |
| Frontend | React / Next.js on AWS Amplify |
| Auth | AWS Cognito (self-registration, email verification) |
| Vector Store | AWS S3 Vectors |
| Embeddings + LLM | Amazon Bedrock — model IDs configurable via AppConfig |
| Database | Amazon DynamoDB (on-demand billing) |
| Configuration | AWS AppConfig (feature flags + model selection) |
| Observability | AWS X-Ray, CloudWatch Logs/Alarms/Dashboard, SNS |
| Container Registry | Amazon ECR (images tagged by git SHA + semver) |
| IaC | Terraform (S3 backend, versioned, native S3 locking) |

### System Diagram

```text
Student Browser
  └── React/Next.js SPA (AWS Amplify)
        └── HTTPS + Bearer JWT
              └── API Gateway (HTTP API)
                    ├── Cognito JWT Authorizer (all routes)
                    ├── /admin/* routes: Lambda authorizer (admins group)
                    └── prod alias → Lambda version
                          └── API Lambda (Spring Boot 4 + Spring AI 2)
                                ├── CourseSearchService → Bedrock embed → S3 Vectors KNN
                                ├── ApplicationService  → DynamoDB
                                └── UserService         → DynamoDB

EventBridge Scheduler (weekly)
  └── Ingestion Lambda
        ├── Scrape bulletin.stanford.edu/programs/CS-PMN
        ├── Diff current vs stored courseIds (delete stale courses + vectors)
        ├── Withdraw APPLIED applications for deleted courses
        ├── Embed course descriptions → Bedrock (model from AppConfig)
        ├── Upsert vectors → S3 Vectors
        └── Upsert metadata → DynamoDB (courses table)

Cognito Post-Confirmation Trigger
  └── Post-Confirmation Lambda
        └── Adds new user to "students" Cognito group
```

---

## 3. Project Structure

```text
stanford-courses/
├── backend/
│   ├── settings.gradle.kts           # includes :api, :ingestion, :post-confirmation
│   ├── build.gradle.kts              # shared plugins + version catalog
│   ├── gradle/
│   │   └── libs.versions.toml
│   ├── api/                          # Spring Boot 4 + Spring AI 2 Lambda
│   │   ├── build.gradle.kts
│   │   └── src/
│   ├── ingestion/                    # Java ingestion Lambda
│   │   ├── build.gradle.kts
│   │   └── src/
│   └── post-confirmation/            # Cognito trigger Lambda
│       ├── build.gradle.kts
│       └── src/
├── frontend/                         # React / Next.js
│   ├── package.json
│   └── src/
└── infra/                            # Terraform
    ├── bootstrap/                    # one-time: versioned S3 state bucket
    ├── main.tf
    ├── variables.tf                  # includes var.aws_region (default: us-east-1)
    ├── outputs.tf
    └── modules/
        ├── cognito/                  # user pool, app client, post-confirmation trigger
        ├── dynamodb/
        ├── lambda/
        ├── api-gateway/
        └── amplify/
```

---

## 4. Data Model

### DynamoDB: `courses`

| Attribute | Type | Description |
| --- | --- | --- |
| `courseId` (PK) | String | e.g. `CS229` |
| `title` | String | Course title |
| `description` | String | Full course description |
| `units` | String | e.g. `3-4` |
| `instructors` | List\<String\> | Instructor names |
| `quarter` | String | e.g. `Autumn 2024` |
| `url` | String | Bulletin page URL |
| `prerequisites` | List\<String\> | Required courseIds (structured — used for enforcement) |
| `prereqNote` | String | Raw bulletin text (e.g. "or equivalent") — **display only, not enforced** |
| `ingestedAt` | String (ISO) | Last scrape timestamp |

### DynamoDB: `applications`

| Attribute | Type | Description |
| --- | --- | --- |
| `userId` (PK) | String | Cognito sub |
| `courseId` (SK) | String | e.g. `CS229` |
| `status` | String | `APPLIED` \| `WITHDRAWN` (soft delete — record is updated, not removed) |
| `appliedAt` | String (ISO) | |
| `updatedAt` | String (ISO) | |

**GSI:** `courseId-index` (PK=`courseId`) — used by admin endpoints to list applicants per course.

### DynamoDB: `users`

| Attribute | Type | Description |
| --- | --- | --- |
| `userId` (PK) | String | Cognito sub |
| `email` | String | From Cognito |
| `name` | String | Display name |
| `completedCourseIds` | List\<String\> | Self-reported completed courses (validated against `courses` table) |
| `updatedAt` | String (ISO) | |

Profile is auto-created on first authenticated request with `completedCourseIds: []`.

### S3 Vectors: `course-embeddings`

| Field | Type | Description |
| --- | --- | --- |
| `vectorKey` | String | `courseId` (matches DynamoDB PK) |
| `vector` | float[1024] | Embedding vector (dimension matches configured embedding model) |
| `metadata` | Map | `{title, units, quarter}` |

---

## 5. API Design

All endpoints require `Authorization: Bearer {Cognito JWT}`. The `userId` is always extracted from the JWT `sub` claim — never from the request body.

### Student Endpoints

#### `GET /courses/search?q={query}&limit={n}`

Embeds the query via Bedrock (using the embedding model from AppConfig), performs KNN search on S3 Vectors, returns top-N courses annotated with prerequisite status for the authenticated student.

- `limit` default: `10`, maximum: `20`. Requests above 20 are clamped to 20.

```json
[{
  "courseId": "CS229",
  "title": "Machine Learning",
  "units": "3-4",
  "canApply": true,
  "missingPrereqs": [],
  "applied": false
}]
```

#### `GET /courses/{courseId}`

Full course detail. Each prerequisite is annotated with `met: true/false` based on the student's `completedCourseIds`. Includes top-level `canApply` flag.

`prereqNote` is returned for display only and does not affect `canApply`.

```json
{
  "courseId": "CS231N",
  "title": "Deep Learning for Vision",
  "description": "...",
  "units": "3",
  "quarter": "Spring 2024",
  "instructors": ["Fei-Fei Li"],
  "prereqNote": "CS229 and CS109",
  "prerequisites": [
    { "courseId": "CS229", "title": "Machine Learning", "met": false },
    { "courseId": "CS109", "title": "Probability", "met": false }
  ],
  "canApply": false,
  "applied": false
}
```

#### `GET /applications`

List all courses the student has applied to (status `APPLIED`), with course details joined.

#### `POST /applications/{courseId}`

Apply to a course.

- `400` with list of missing prerequisite courseIds if prerequisites are not met
- `409` if student already has status `APPLIED` for this course
- `404` if `courseId` does not exist

#### `DELETE /applications/{courseId}`

Withdraw from a course — sets `status` to `WITHDRAWN` (soft delete).

- `204` on success
- `404` if no `APPLIED` application exists for this student + course
- Re-withdrawal of an already-`WITHDRAWN` application returns `404`

#### `GET /profile/completed-courses`

Returns the student's completed course list.

#### `PUT /profile/completed-courses`

```json
{ "courseIds": ["CS106B", "CS109"] }
```

Replaces the student's completed courses list. Each submitted `courseId` is validated against the `courses` DynamoDB table. Any `courseId` not found is rejected with a `400` listing the invalid IDs.

### Admin Endpoints

Admin endpoints require:

1. A valid Cognito JWT (enforced at API Gateway)
2. The JWT `cognito:groups` claim to include `admins` — enforced at the **API Gateway route level** via a dedicated Lambda authorizer. Spring Security provides a secondary defense-in-depth check. `403` is returned if the claim is absent.

#### `GET /admin/courses`

Lists **all** courses with their applicant count (including courses with zero applicants). Sorted by applicant count descending.

Data access pattern:

1. Scan `courses` table — retrieve all courseIds and titles
2. Scan `courseId-index` GSI on `applications` table — filter to `status = APPLIED`, aggregate count per `courseId`
3. Join: for each course from step 1, look up count from step 2 (default 0 if absent)

```json
[{ "courseId": "CS229", "title": "Machine Learning", "applicantCount": 42 }]
```

#### `GET /admin/courses/{courseId}/applicants`

Lists all students with status `APPLIED` for a course.

Data access pattern:

1. Query `courseId-index` GSI on `applications` table — filter to `status = APPLIED`
2. Batch fetch user details from `users` table

```json
[{ "userId": "...", "name": "Jane Doe", "email": "jane@stanford.edu", "appliedAt": "...", "status": "APPLIED" }]
```

---

## 6. Prerequisite Enforcement

At `POST /applications/{courseId}`, the API:

1. Fetches `course.prerequisites` from DynamoDB (`courses` table)
2. Fetches `user.completedCourseIds` from DynamoDB (`users` table)
3. Computes `missing = course.prerequisites − user.completedCourseIds`
4. If `missing` is non-empty → returns `400` with the missing courseId list
5. Otherwise → writes the application to DynamoDB

`prereqNote` (e.g. "or equivalent") is informational only and does not affect this check.

---

## 7. Ingestion Pipeline

Weekly Lambda triggered by EventBridge Scheduler. Uses the embedding model ID from AppConfig at runtime.

1. Fetch current AppConfig configuration (embedding model ID)
2. HTTP GET `https://bulletin.stanford.edu/programs/CS-PMN`
3. Parse HTML — extract courses: `courseId`, `title`, `description`, `units`, `instructors`, `quarter`, `prerequisites`, `prereqNote`
4. **Stale course cleanup:**
   - Scan existing `courseId`s from the `courses` DynamoDB table
   - Compute `stale = existing − scraped`
   - For each stale courseId: delete from DynamoDB `courses` and delete vector from S3 Vectors
5. **Orphaned application handling:** for each stale courseId, update all `APPLIED` applications to status `WITHDRAWN` (query `courseId-index` GSI, then `UpdateItem` each record)
6. For each current course:
   - Embed `title + description` via Bedrock using the configured embedding model
   - Upsert vector in S3 Vectors (`vectorKey = courseId`)
   - Upsert course metadata in DynamoDB `courses` table
7. Log ingestion summary (added, updated, deleted counts, orphaned applications withdrawn, errors)

---

## 8. Authentication

- **Cognito User Pool** with self-service sign-up enabled and email verification required
- **App Client** configured with PKCE (no client secret) for the React SPA
- **Cognito Groups:** `students` (auto-assigned on registration via Post-Confirmation Lambda trigger), `admins` (manually assigned)
- **Post-Confirmation Lambda:** triggered by Cognito after email verification; calls `adminAddUserToGroup` to add the new user to the `students` group
- **Frontend:** Amplify Auth (Cognito Hosted UI or Amplify JS SDK)
- **API:** API Gateway HTTP API with Cognito JWT Authorizer for all routes; `/admin/*` routes additionally enforce `cognito:groups` contains `admins` via a dedicated Lambda authorizer. Spring Security provides defense-in-depth group enforcement inside the Lambda.

---

## 9. Deployment Versioning, Rollback & Configuration

### Container Images (ECR)

Each Lambda is packaged as a Docker container image and pushed to ECR tagged with git SHA and semver (e.g. `v1.2.3-abc1234`). Lambda functions reference images by tag.

### Lambda Versions & Aliases

Each deploy publishes a new immutable Lambda version. The `prod` alias points to the live version. API Gateway invokes the Lambda via the `prod` alias.

- **Rollback:** update `prod` alias to point to a previous version number
- **A/B testing:** alias weighted routing (e.g. 90% v1, 10% v2) via `routing_config.additional_version_weights` in Terraform

The CI/CD pipeline (Section 11) explicitly updates the `prod` alias to the newly published version after each deploy.

### Runtime Configuration (AWS AppConfig)

All runtime-tunable settings live in AppConfig — no redeployment needed to change them.

**Configuration schema:**

```json
{
  "embeddingModelId": "amazon.titan-embed-text-v2:0",
  "generativeModelId": "anthropic.claude-sonnet-4-5",
  "maxSearchResults": 10,
  "enableSemanticReranking": false,
  "newPrereqEnforcement": true
}
```

Both `embeddingModelId` and `generativeModelId` are Bedrock model IDs. Changing them in AppConfig takes effect on the next Lambda invocation within the refresh TTL — no redeployment required. When changing the embedding model, a full re-ingestion must be triggered manually (the new model may produce different vector dimensions), and the S3 Vectors index must be recreated with the new dimensions before the API Lambda is switched over.

**Refresh strategy:** Each Lambda instance caches the AppConfig response with a TTL of 60 seconds. On each invocation, if the cache is expired, the instance re-fetches from AppConfig before handling the request. This bounds configuration staleness to 60 seconds without adding latency to every call.

**How to change configuration:**

1. Open the AWS AppConfig console (or use the AWS CLI)
2. Navigate to the application → environment → configuration profile
3. Create a new **hosted configuration version** with the updated JSON
4. Start a **deployment** of the new version to the `prod` environment (AppConfig requires an explicit deployment step — changes do not take effect until deployed)
5. Lambda instances pick up the change within 60 seconds (TTL)
6. Monitor CloudWatch metrics during the deployment; AppConfig supports automatic rollback via CloudWatch alarm integration

**Embedding model change procedure** (requires coordinated steps):

1. Update AppConfig: set `embeddingModelId` to the new model ID — deploy to a `staging` environment first
2. Re-run the ingestion Lambda against staging (new vectors generated with new model)
3. Recreate the S3 Vectors index for staging with the new vector dimension if it differs
4. Validate search quality in staging
5. Deploy AppConfig change to `prod` environment, then trigger re-ingestion in prod
6. The API Lambda picks up the new model ID within 60 seconds

---

## 10. Infrastructure (Terraform)

### Region

All resources deployed to `us-east-1` (configured via `var.aws_region`, default `us-east-1`). This region is required for S3 Vectors, Bedrock Titan Embed v2, and Claude Sonnet availability.

### Backend

Terraform state is stored in S3 with native S3 locking (Terraform ~> 1.14). No DynamoDB table is required — S3 conditional writes enforce atomic state updates.

```hcl
terraform {
  required_version = "~> 1.14"

  backend "s3" {
    bucket       = "stanford-courses-tfstate"
    key          = "prod/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
```

The S3 bucket has versioning enabled for full state history. The `use_lockfile = true` flag enables S3 native locking via a `.tflock` object in the same bucket — no DynamoDB dependency. Bootstrap config (`infra/bootstrap/`) creates the S3 bucket once using a local backend.

### Rate Limiting

API Gateway HTTP API default throttling: 100 req/sec burst, 50 req/sec steady-state — applied at the stage level (aggregate across all callers). This design uses Cognito JWT auth without API keys; per-user rate limiting is not enforced at the gateway layer. If per-user throttling is required in future, it can be added via AWS WAF with a rate-based rule keyed on the JWT `sub` claim.

### IAM (least privilege)

**API Lambda role:**

- `dynamodb:GetItem`, `PutItem`, `UpdateItem`, `Query`, `BatchGetItem` on all three tables
- `bedrock:InvokeModel` (any model — model ID comes from AppConfig)
- `s3vectors:QueryVectors`, `GetVectors`
- `appconfig:GetLatestConfiguration`, `appconfig:StartConfigurationSession`
- `xray:PutTraceSegments`, `xray:PutTelemetryRecords`

**Ingestion Lambda role:**

- `dynamodb:Scan`, `PutItem`, `UpdateItem`, `DeleteItem` on `courses` table
- `dynamodb:Query`, `UpdateItem` on `applications` table (to withdraw orphaned applications on stale course deletion)
- `bedrock:InvokeModel` (any model — model ID comes from AppConfig)
- `s3vectors:PutVectors`, `DeleteVectors`
- `appconfig:GetLatestConfiguration`, `appconfig:StartConfigurationSession`
- `xray:PutTraceSegments`, `xray:PutTelemetryRecords`
- `cloudwatch:PutMetricData` (custom ingestion metrics)

**Post-Confirmation Lambda role:**

- `cognito-idp:AdminAddUserToGroup` on the User Pool

---

## 11. Observability

All observability infrastructure is provisioned via Terraform in the `modules/observability` module.

### Structured Logging

All Lambda functions emit structured JSON logs to CloudWatch Logs. Log groups are created explicitly with a 30-day retention policy. Log fields include: `requestId`, `level`, `message`, `durationMs`, and domain-specific context (e.g. `courseId`, `userId`, `modelId`).

### Distributed Tracing (AWS X-Ray)

X-Ray active tracing is enabled on all Lambda functions and API Gateway. The Spring Boot API Lambda uses the AWS X-Ray SDK to create subsegments for: Bedrock calls, S3 Vectors queries, and DynamoDB operations. This provides end-to-end traces for every search and application request.

### Metrics & Alarms

CloudWatch Alarms notify an SNS topic (email subscription) when:

| Alarm | Threshold |
| --- | --- |
| API Lambda error rate | > 5% over 5 minutes |
| API Lambda P99 duration | > 10 seconds over 5 minutes |
| Ingestion Lambda errors | ≥ 1 error per invocation |
| API Gateway 5xx rate | > 1% over 5 minutes |

### AppConfig Automatic Rollback

The API Lambda error rate alarm is wired to the AppConfig deployment as a rollback trigger. If a new AppConfig deployment causes the error rate alarm to breach, AppConfig automatically rolls back to the previous configuration version.

### CloudWatch Dashboard

A single operational dashboard named `stanford-courses-prod` displays:

- API Lambda: invocations, error rate, P50/P99 duration
- Ingestion Lambda: last invocation status, duration, courses ingested (custom metric)
- DynamoDB: consumed read/write capacity units per table
- API Gateway: request count, 4xx/5xx rates

### IAM additions

**API Lambda role** additionally requires:
- `xray:PutTraceSegments`, `xray:PutTelemetryRecords`

**Ingestion Lambda role** additionally requires:
- `xray:PutTraceSegments`, `xray:PutTelemetryRecords`
- `cloudwatch:PutMetricData` (for custom ingestion metrics)

---

## 12. CI/CD (GitHub Actions)

### AWS Authentication

GitHub Actions authenticates to AWS via **OIDC federation** (no long-lived static credentials stored). An IAM role with a trust policy scoped to the specific GitHub repository and branch is assumed per workflow run using `aws-actions/configure-aws-credentials` with `role-to-assume`.

### On Pull Request

| Changed path | Action |
| --- | --- |
| `backend/` | Build + test (`./gradlew build test`) — required status check |
| `infra/` | `terraform validate` + `terraform plan` — plan posted as PR comment |
| `frontend/` | Amplify auto-generates PR preview URL |

### On Merge to Main

| Changed path | Action |
| --- | --- |
| `backend/` | Docker build → push to ECR (tagged with git SHA + semver) → `aws lambda update-function-code` → `aws lambda publish-version` → `aws lambda update-alias --name prod --function-version $NEW_VERSION` |
| `infra/` | `terraform apply -auto-approve` |
| `frontend/` | Amplify auto-deploys |

Pipelines use path filters — a frontend-only change does not trigger backend CI.

### GitHub Secrets

- `AWS_OIDC_ROLE_ARN` — IAM role ARN for OIDC federation (no access keys stored)

---

## 12. Frontend Screens

| Screen | Description |
| --- | --- |
| **Search** | Natural language query input; results show eligibility badge (`Eligible` / `Missing prereqs`) and inline Apply button |
| **Course Detail** | Full details; prerequisites listed with ✓/✗ per item; `canApply=false` shows blocked message with missing prereqs; `prereqNote` shown as informational text |
| **My Applications** | List of applied courses (status `APPLIED`) with Withdraw button |
| **Profile** | View/edit completed courses (add by courseId, remove) |

Authentication screens are handled by Cognito Hosted UI or Amplify Auth UI component.

---

## 13. Local Development

Local development targets a dedicated **dev AWS account** — never production. AWS credentials for the dev account are configured via environment variables or AWS CLI profiles. No production resources are accessible from local dev.

```bash
# Backend
cd backend
./gradlew :api:bootRun        # API on localhost:8080 (Spring profile: local)
./gradlew :ingestion:run      # run ingestion once against dev AWS

# Frontend
cd frontend
npm run dev                   # Next.js on localhost:3000

# Infrastructure
cd infra
terraform workspace select dev
terraform plan
terraform apply
```

The `local` Spring profile configures reduced timeouts and verbose logging. Developers must have AWS credentials for the dev account with access to dev-tier DynamoDB, S3 Vectors, Bedrock, and AppConfig.
