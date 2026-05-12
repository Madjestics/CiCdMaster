export type UUID = string;

export type JobStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELED';

export interface PipelineResponse {
  id: UUID;
  name: string;
  description: string | null;
  folderId: UUID | null;
}

export interface FolderResponse {
  id: UUID;
  name: string;
  description: string | null;
  parentId: UUID | null;
}

export interface StageResponse {
  id: UUID;
  pipelineId: UUID;
  order: number;
  name: string;
  description: string | null;
}

export interface JobParamsView {
  jobTemplateId: UUID;
  params: Record<string, unknown>;
}

export interface JobResponse {
  id: UUID;
  stageId: UUID;
  order: number;
  status: JobStatus;
  script: string | null;
  scriptPrimary: boolean;
  params: JobParamsView | null;
}

export interface JobTemplateResponse {
  id: UUID;
  path: string;
  paramsTemplate: Record<string, unknown>;
}

export interface JobHistoryResponse {
  id: number;
  jobId: UUID;
  duration: number;
  startDate: string;
  logs: string;
  additionalData: Record<string, unknown> | null;
}

export interface PipelineRunJobResponse {
  historyId: number;
  jobId: UUID;
  stageId: UUID | null;
  stageName: string;
  stageOrder: number;
  jobOrder: number;
  jobLabel: string;
  status: string;
  duration: number;
  startDate: string;
  logs: string;
  additionalData: Record<string, unknown> | null;
}

export interface PipelineRunResponse {
  runId: string;
  requestedAt: string;
  initiatedBy: string | null;
  status: string;
  startedAt: string;
  finishedAt: string;
  duration: number;
  jobs: PipelineRunJobResponse[];
}

export interface PipelineUpsertRequest {
  name: string;
  description?: string;
  folderId?: UUID;
}

export interface FolderUpsertRequest {
  name: string;
  description?: string;
  parentId?: UUID;
}

export interface StageUpsertRequest {
  pipelineId: UUID;
  order: number;
  name: string;
  description?: string;
}

export interface JobUpsertRequest {
  stageId: UUID;
  order: number;
  status: JobStatus;
  script?: string;
  scriptPrimary: boolean;
  jobTemplateId?: UUID;
  params?: Record<string, unknown>;
}

export interface PipelineRunRequest {
  initiatedBy: string;
  parameters?: Record<string, unknown>;
}

export interface PipelineCancelRequest {
  reason?: string;
}

export interface JobLogStreamMessage {
  jobId: UUID;
  pipelineId: UUID;
  eventType: string;
  status: string;
  historyId: number | null;
  logs: string | null;
  timestamp: string;
  additionalData: Record<string, unknown> | null;
}

export type BuilderJobMode = 'template' | 'script';

export interface BuilderJobDraft {
  localId: string;
  mode: BuilderJobMode;
  templateId?: UUID;
  templatePath?: string;
  params?: Record<string, unknown>;
  script?: string;
  scriptPrimary: boolean;
}

export interface BuilderStageDraft {
  localId: string;
  name: string;
  description?: string;
  jobs: BuilderJobDraft[];
}
