import { API_BASE_URL, request } from './http';
import type {
  JobHistoryResponse,
  JobResponse,
  JobTemplateResponse,
  JobUpsertRequest,
  PipelineCancelRequest,
  PipelineResponse,
  PipelineRunRequest,
  PipelineUpsertRequest,
  StageResponse,
  StageUpsertRequest,
} from '../types/api';

export const api = {
  fetchPipelines: () => request<PipelineResponse[]>('/api/v1/pipelines'),
  fetchPipelineById: (pipelineId: string) => request<PipelineResponse>(`/api/v1/pipelines/${pipelineId}`),
  createPipeline: (payload: PipelineUpsertRequest) =>
    request<PipelineResponse>('/api/v1/pipelines', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  runPipeline: (pipelineId: string, payload: PipelineRunRequest) =>
    request<void>(`/api/v1/pipelines/${pipelineId}/run`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  cancelPipeline: (pipelineId: string, payload?: PipelineCancelRequest) =>
    request<void>(`/api/v1/pipelines/${pipelineId}/cancel`, {
      method: 'POST',
      body: JSON.stringify(payload ?? {}),
    }),

  fetchStagesByPipeline: (pipelineId: string) => request<StageResponse[]>(`/api/v1/stages/by-pipeline/${pipelineId}`),
  createStage: (payload: StageUpsertRequest) =>
    request<StageResponse>('/api/v1/stages', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  fetchJobsByStage: (stageId: string) => request<JobResponse[]>(`/api/v1/jobs/by-stage/${stageId}`),
  createJob: (payload: JobUpsertRequest) =>
    request<JobResponse>('/api/v1/jobs', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  fetchJobHistory: (jobId: string) => request<JobHistoryResponse[]>(`/api/v1/job-history/by-job/${jobId}`),

  fetchJobTemplates: () => request<JobTemplateResponse[]>('/api/v1/job-templates'),

  logsStreamUrl: (jobId?: string) => {
    const query = jobId ? `?jobId=${encodeURIComponent(jobId)}` : '';
    return `${API_BASE_URL}/api/v1/logs/stream${query}`;
  },
};