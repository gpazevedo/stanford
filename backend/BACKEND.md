# Backend

Gradle 9 multi-module project, Java 25, Spring Boot 4.0.3. Three Lambda functions deployed as Docker container images.

## Modules

| Module | Trigger | Purpose |
 | --- | --- | --- |
| `api` | API Gateway HTTP request | Course search, applications, user profile, admin |
| `ingestion` | EventBridge (nightly) | Scrape Stanford bulletin, embed courses, sync DynamoDB + S3 Vectors |
| `post-confirmation` | Cognito post-confirmation | Add new user to `students` Cognito group |

## Key Dependencies

| Library | Version |
| --- | --- |
| Spring Boot | 4.0.3 |
| Spring AI | 2.0.0-M2 |
| AWS SDK v2 | 2.32.0 |
| jsoup | 1.18.1 |
| JUnit 5 | 5.11.0 |
| Mockito | 5.12.0 |

## API Module

### Package Structure

```text
api/
└── src/main/java/edu/stanford/courses/api/
    ├── ApiApplication.java
    ├── courses/
    │   ├── rest/controllers/CourseController.java
    │   └── domain/CourseSearchService.java, CourseRepository.java
    ├── applications/
    │   ├── rest/controllers/ApplicationController.java
    │   └── domain/ApplicationService.java, ApplicationRepository.java
    ├── users/
    │   ├── rest/controllers/ProfileController.java
    │   └── domain/UserRepository.java
    ├── admin/
    │   ├── rest/controllers/AdminController.java
    │   └── domain/AdminService.java
    └── config/
        ├── SecurityConfig.java   Cognito JWT + admin authorizer
        ├── AppConfigService.java Dynamic settings from AppConfig
        └── AwsConfig.java        AWS SDK beans
```

### REST API

All endpoints require a Cognito Bearer JWT (`Authorization: Bearer <token>`).

**Courses**

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/courses/search?q=&limit=10` | KNN semantic search (max 20 results) |
| `GET` | `/courses/{courseId}` | Course detail with prerequisite status |

**Applications**

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/applications` | List authenticated user's applications |
| `POST` | `/applications/{courseId}` | Apply to a course (`201 Created`) |
| `DELETE` | `/applications/{courseId}` | Withdraw application (`204 No Content`) |

**Profile**

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/profile/completed-courses` | Get completed course IDs |
| `PUT` | `/profile/completed-courses` | Update completed courses (validates IDs exist) |

**Admin** — requires `admins` Cognito group (enforced by both API Gateway authorizer and Spring Security)

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/admin/courses` | List all courses with application counts |
| `GET` | `/admin/courses/{courseId}/applicants` | List applicants for a course |

### Search Flow

1. Embed query text via Bedrock (model ID from AppConfig)
2. KNN query against S3 Vectors index
3. Fetch matching courses from DynamoDB by ID
4. Annotate each result with prerequisite status (using user's completed courses)
5. Mark which courses the user has already applied to

### Auth

`SecurityConfig` configures Spring Security as a stateless JWT resource server. The `cognito:groups` claim is mapped to Spring authorities — `admins` group grants `ROLE_admins`, enabling `/admin/**` access.

### AppConfig

`AppConfigService` polls AppConfig at startup and caches settings. Current settings:

```json
{
  "embeddingModelId": "...",
  "generativeModelId": "...",
  "maxSearchResults": 10,
  "enableSemanticReranking": false,
  "newPrereqEnforcement": true
}
```

Both `AppSettings` records use `@JsonIgnoreProperties(ignoreUnknown = true)` so adding fields to AppConfig never breaks running Lambdas.

---

## Ingestion Module

Runs nightly via EventBridge. No Spring — plain Java Lambda.

### Flow

```text
BulletinScraper → scrape bulletin.stanford.edu/programs/CS-PMN
        │
        ▼
IngestionService.ingest(scraped)
        │
        ├── diff scraped IDs vs DynamoDB
        │       └── stale courses: delete from DynamoDB + S3 Vectors
        │                         + withdraw APPLIED applications
        │
        └── for each scraped course:
                ├── embed (title + description) → Bedrock
                ├── upsert vector → S3 Vectors
                └── upsert metadata → DynamoDB
```

`BulletinScraper` uses jsoup. All HTML element selections are null-guarded (`?.text()`) to handle missing fields gracefully.

---

## Post-Confirmation Module

Single-class Lambda triggered by Cognito after email verification. Calls `CognitoIdentityProviderClient.adminAddUserToGroup()` to add the new user to the `students` group.

---

## Building

```bash
cd backend
./gradlew build          # compile + test all modules
./gradlew test           # tests only
./gradlew :api:shadowJar           # fat JAR for api
./gradlew :ingestion:shadowJar     # fat JAR for ingestion
./gradlew :post-confirmation:shadowJar
```

Fat JARs are built by the Shadow plugin and used as the Docker image entrypoint. The Lambda Web Adapter 0.9.1 is included in the Docker images to bridge HTTP ↔ Lambda invocation for the API module.

## Testing

Tests use JUnit 5 + Mockito. All AWS SDK clients are mocked — no real AWS calls in tests.

```bash
./gradlew test                         # all modules
./gradlew :api:test --tests "*.CourseSearchServiceTest"  # single class
```
