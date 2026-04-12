# CI/CD Master Frontend

React + TypeScript + Ant Design frontend for the CI/CD master service.

## Implemented

- Pipeline Builder:
  - create pipeline metadata
  - add/remove stages
  - add jobs from backend templates (`job_template`)
  - dynamic template parameter form from `params_template`
  - manual script mode for custom jobs
- Pipeline Monitor:
  - stage progress view
  - per-stage jobs and statuses
  - run/cancel pipeline controls
- Logs:
  - history via `/api/v1/job-history/by-job/{jobId}`
  - real-time SSE stream via `/api/v1/logs/stream?jobId=<uuid>`

## Structure

- `src/components/PipelineBuilder.tsx`
- `src/components/PipelineMonitor.tsx`
- `src/components/TemplateParamForm.tsx`
- `src/api/client.ts`
- `src/utils/template.ts`

## Run

```bash
cd frontend
npm install
npm run dev
```

Default URL: `http://localhost:5173`

## Environment

Optional override:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## Build

```bash
npm run build
npm run preview
```

Backend CORS note:
- If browser still shows CORS errors, set backend env CORS_ALLOWED_ORIGINS (comma-separated).
