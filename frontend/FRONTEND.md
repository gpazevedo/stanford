# Frontend

Next.js 16 (SSR), TypeScript 5, Tailwind CSS 4. Deployed on AWS Amplify — auto-deploys on every push to `main`.

## Key Dependencies

| Library | Version |
| --- | --- |
| Next.js | 16.1.6 |
| React | 19.2.3 |
| AWS Amplify JS | 6.16.3 |
| Tailwind CSS | 4 |
| Vitest | 4.1.0 |
| React Testing Library | 16.3.2 |

## Structure

```text
src/
├── app/
│   ├── layout.tsx                 Root layout — server component, renders ClientProviders
│   ├── ClientProviders.tsx        Amplify Authenticator gate + nav bar
│   ├── page.tsx                   Search page (/)
│   ├── courses/[courseId]/        Course detail page
│   ├── applications/              My Applications page
│   └── profile/                  Completed courses management
├── components/
│   ├── SearchBar.tsx
│   ├── CourseCard.tsx
│   ├── EligibilityBadge.tsx
│   ├── PrereqList.tsx
│   └── WithdrawButton.tsx
├── lib/
│   ├── api.ts                     Typed API client (all fetch calls)
│   ├── auth.ts                    getCurrentJwt() via Amplify fetchAuthSession
│   └── amplifyConfig.ts           Side-effect import: configures Amplify once
└── types/
    └── api.ts                     Shared TypeScript interfaces + ApiError class
```

## Pages

### `/` — Search

Search bar + results list. On submit calls `GET /courses/search?q=`. Each result shows title, units, eligibility badge, and an Apply button (disabled if prerequisites not met or already applied). Applying optimistically updates the card to show `Applied`.

### `/courses/[courseId]` — Course Detail

Fetches `GET /courses/{courseId}`. Shows full description, instructors, quarter, prerequisite checklist (green ✓ / red ✗ per prereq), and an Apply button. The Apply button is hidden if prerequisites are not met.

### `/applications` — My Applications

Fetches `GET /applications`. Lists applied courses as cards with a Withdraw button. `WithdrawButton` is a two-step confirm component — first click shows Confirm/Cancel, second click calls `DELETE /applications/{courseId}`. Load errors and withdraw errors both surface inline.

### `/profile` — Completed Courses

Fetches and updates `GET|PUT /profile/completed-courses`. Multi-select list of all courses; saves with `PUT`. Used to mark prerequisites as complete, which affects eligibility shown elsewhere.

## Auth

`ClientProviders.tsx` wraps the entire app in `<Authenticator>` from `@aws-amplify/ui-react`. Unauthenticated users see the Amplify hosted sign-in/sign-up UI. Once authenticated, `auth.ts` provides `getCurrentJwt()` which returns the Cognito ID token for use as an API `Bearer` token.

Amplify is configured once via `src/lib/amplifyConfig.ts` (side-effect import in `ClientProviders`). Environment variables required:

| Variable | Value |
| --- | --- |
| `NEXT_PUBLIC_API_URL` | API Gateway endpoint |
| `NEXT_PUBLIC_COGNITO_USER_POOL` | Cognito user pool ID |
| `NEXT_PUBLIC_COGNITO_CLIENT_ID` | Cognito app client ID |
| `NEXT_PUBLIC_AWS_REGION` | `us-east-1` |

These are injected automatically by Amplify at build time (set in the Terraform `amplify` module).

## API Client

`src/lib/api.ts` exposes typed functions over a shared `apiFetch` helper. The helper:

- Attaches the Cognito JWT as `Authorization: Bearer`
- Only sends `Content-Type: application/json` when a body is present
- Throws `ApiError` (with `.status` and `.message`) on non-2xx responses
- Returns `undefined` for empty responses (e.g. `204 No Content`)

## Local Development

```bash
npm install
npm run dev       # http://localhost:3000
```

Set environment variables in `.env.local`:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_COGNITO_USER_POOL=us-east-1_xxxxx
NEXT_PUBLIC_COGNITO_CLIENT_ID=xxxxxxxxxx
NEXT_PUBLIC_AWS_REGION=us-east-1
```

## Testing

```bash
npm test          # run all tests (Vitest)
npm test -- --watch
```

Tests use Vitest + React Testing Library + jsdom. AWS SDK and `api.ts` calls are mocked via `vi.mock`. All pages and components have co-located test files (`*.test.tsx` / `*.test.ts`).

## Deployment

Amplify auto-deploys on every push to `main` that touches `frontend/`. No GitHub Actions workflow is needed — Amplify handles the build (`npm ci && npm run build`) and serves the Next.js SSR app.

The `amplify.yml` in the repo root configures the build to use `baseDirectory: .next` (SSR mode). Dynamic routes (e.g. `[courseId]`) require SSR — static export is not used.
