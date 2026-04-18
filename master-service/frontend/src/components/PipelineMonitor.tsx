import {
  Button,
  Card,
  Col,
  Empty,
  Flex,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Steps,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ClockCircleOutlined, PauseCircleOutlined, PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { StatusTag } from './StatusTag';
import type {
  JobLogStreamMessage,
  JobResponse,
  JobStatus,
  PipelineResponse,
  StageResponse,
  UUID,
} from '../types/api';

interface StageWithJobs extends StageResponse {
  jobs: JobResponse[];
}

interface Props {
  pipelines: PipelineResponse[];
  onReloadPipelines: () => Promise<void>;
}

export function PipelineMonitor({ pipelines, onReloadPipelines }: Props) {
  const [selectedPipelineId, setSelectedPipelineId] = useState<string>();
  const [loadingPipelineData, setLoadingPipelineData] = useState(false);
  const [stages, setStages] = useState<StageWithJobs[]>([]);

  const [selectedJobId, setSelectedJobId] = useState<string>();
  const [historicalLogs, setHistoricalLogs] = useState<string>('');
  const [liveLogs, setLiveLogs] = useState<string[]>([]);
  const [sseState, setSseState] = useState<'idle' | 'connecting' | 'connected'>('idle');

  const [initiatedBy, setInitiatedBy] = useState('оператор');
  const [runLoading, setRunLoading] = useState(false);
  const [cancelLoading, setCancelLoading] = useState(false);

  const allJobs = useMemo(() => stages.flatMap((stage) => stage.jobs), [stages]);

  useEffect(() => {
    if (!selectedPipelineId && pipelines.length > 0) {
      setSelectedPipelineId(pipelines[0].id);
    }
  }, [pipelines, selectedPipelineId]);

  useEffect(() => {
    if (!selectedPipelineId) {
      setStages([]);
      return;
    }
    void loadPipelineData(selectedPipelineId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedPipelineId]);

  useEffect(() => {
    if (!selectedJobId) {
      setHistoricalLogs('');
      setLiveLogs([]);
      return;
    }

    void loadJobHistory(selectedJobId);
    setSseState('connecting');
    const source = new EventSource(api.logsStreamUrl(selectedJobId));

    const onOpen = () => setSseState('connected');
    const onError = () => setSseState('idle');
    const onLogEvent = (event: MessageEvent<string>) => {
      try {
        const payload = JSON.parse(event.data) as JobLogStreamMessage;
        if (payload.logs?.trim()) {
          setLiveLogs((prev) => [...prev, payload.logs ?? '']);
        }
        if (payload.status) {
          const nextStatus = payload.status as JobStatus;
          patchJobStatus(payload.jobId, nextStatus);
          if (payload.jobId === selectedJobId && isFinalStatus(nextStatus)) {
            setTimeout(() => {
              void loadJobHistory(payload.jobId);
            }, 250);
          }
        }
      } catch {
        // ignore malformed events
      }
    };

    source.addEventListener('open', onOpen);
    source.addEventListener('error', onError);
    source.addEventListener('job-log', onLogEvent as EventListener);

    return () => {
      source.removeEventListener('open', onOpen);
      source.removeEventListener('error', onError);
      source.removeEventListener('job-log', onLogEvent as EventListener);
      source.close();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedJobId]);

  const stageItems = useMemo(() => {
    return stages.map((stage) => {
      const stageStatus = deriveStageStatus(stage.jobs.map((job) => job.status));
      return {
        title: stage.name,
        description: stage.description || `Задач: ${stage.jobs.length}`,
        status: stageStatus,
      };
    });
  }, [stages]);

  async function loadPipelineData(pipelineId: UUID) {
    try {
      setLoadingPipelineData(true);
      const stageResponse = await api.fetchStagesByPipeline(pipelineId);
      const stageEntries: StageWithJobs[] = [];
      for (const stage of stageResponse) {
        const jobs = await api.fetchJobsByStage(stage.id);
        stageEntries.push({ ...stage, jobs });
      }
      setStages(stageEntries);
      const firstJob = stageEntries.flatMap((entry) => entry.jobs)[0];
      setSelectedJobId(firstJob?.id);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Не удалось загрузить этапы пайплайна.');
    } finally {
      setLoadingPipelineData(false);
    }
  }

  async function loadJobHistory(jobId: UUID) {
    try {
      const history = await api.fetchJobHistory(jobId);
      const merged = history
        .slice()
        .reverse()
        .map((record) => `[${record.startDate}]\n${record.logs}`)
        .join('\n\n');
      setHistoricalLogs(merged);
      setLiveLogs([]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Не удалось загрузить историю логов.');
    }
  }

  function patchJobStatus(jobId: UUID, nextStatus: JobStatus) {
    setStages((prev) =>
      prev.map((stage) => ({
        ...stage,
        jobs: stage.jobs.map((job) => (job.id === jobId ? { ...job, status: nextStatus } : job)),
      })),
    );
  }

  async function handleRunPipeline() {
    if (!selectedPipelineId) {
      return;
    }
    if (!initiatedBy.trim()) {
      message.error('Укажите инициатора запуска.');
      return;
    }

    try {
      setRunLoading(true);
      await api.runPipeline(selectedPipelineId, {
        initiatedBy: initiatedBy.trim(),
        parameters: {},
      });
      message.success('Команда запуска отправлена в сервисы-исполнители через Kafka.');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Ошибка отправки команды запуска.');
    } finally {
      setRunLoading(false);
    }
  }

  async function handleCancelPipeline() {
    if (!selectedPipelineId) {
      return;
    }
    try {
      setCancelLoading(true);
      await api.cancelPipeline(selectedPipelineId, { reason: 'manual_cancel_from_ui' });
      message.success('Команда отмены отправлена.');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Ошибка отправки команды отмены.');
    } finally {
      setCancelLoading(false);
    }
  }

  const selectedJob = allJobs.find((job) => job.id === selectedJobId);

  return (
    <div>
      <Card className="soft-card">
        <Flex align="center" justify="space-between" gap={12} wrap>
          <Space wrap>
            <Select
              style={{ width: 320 }}
              size="large"
              value={selectedPipelineId}
              onChange={setSelectedPipelineId}
              options={pipelines.map((pipeline) => ({
                value: pipeline.id,
                label: pipeline.name,
              }))}
              placeholder="Выберите пайплайн"
            />
            <Button icon={<ReloadOutlined />} onClick={() => selectedPipelineId && void loadPipelineData(selectedPipelineId)}>
              Обновить этапы
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => void onReloadPipelines()}>
              Обновить список
            </Button>
          </Space>

          <Space>
            <Input
              prefix={<ClockCircleOutlined />}
              value={initiatedBy}
              onChange={(event) => setInitiatedBy(event.target.value)}
              style={{ width: 210 }}
              placeholder="Кто запускает"
            />
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={runLoading}
              onClick={handleRunPipeline}
              disabled={!selectedPipelineId}
            >
              Запустить
            </Button>
            <Button
              danger
              icon={<PauseCircleOutlined />}
              loading={cancelLoading}
              onClick={handleCancelPipeline}
              disabled={!selectedPipelineId}
            >
              Остановить
            </Button>
          </Space>
        </Flex>
      </Card>

      <div style={{ height: 18 }} />

      {loadingPipelineData ? (
        <Card className="soft-card">
          <Flex justify="center" style={{ padding: 40 }}>
            <Spin size="large" />
          </Flex>
        </Card>
      ) : !selectedPipelineId ? (
        <Card className="soft-card">
          <Empty description="Создайте или выберите пайплайн" />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={15}>
            <Card className="soft-card" title="Прогресс этапов">
              {stageItems.length === 0 ? (
                <Empty description="У пайплайна пока нет этапов" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Steps direction="vertical" size="small" items={stageItems} />
              )}
            </Card>

            <div style={{ height: 16 }} />

            <Card className="soft-card" title="Этапы и задачи">
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                {stages.map((stage) => (
                  <Card
                    key={stage.id}
                    className="stage-card"
                    size="small"
                    title={
                      <Space>
                        <Tag color="blue">#{stage.order}</Tag>
                        <Typography.Text strong>{stage.name}</Typography.Text>
                      </Space>
                    }
                  >
                    <Table
                      size="small"
                      rowKey="id"
                      pagination={false}
                      dataSource={stage.jobs}
                      onRow={(row) => ({
                        onClick: () => setSelectedJobId(row.id),
                      })}
                      columns={[
                        {
                          title: 'Порядок',
                          dataIndex: 'order',
                          width: 90,
                        },
                        {
                          title: 'Тип',
                          render: (_, record: JobResponse) =>
                            record.params?.jobTemplateId ? 'Шаблонная' : 'Ручной скрипт',
                        },
                        {
                          title: 'Статус',
                          dataIndex: 'status',
                          render: (status: JobStatus) => <StatusTag status={status} />,
                          width: 140,
                        },
                      ]}
                    />
                  </Card>
                ))}
              </Space>
            </Card>
          </Col>

          <Col xs={24} xl={9}>
            <Card
              className="soft-card"
             
              title="Логи задачи"
              extra={
                <Space>
                  <Tag color={sseState === 'connected' ? 'green' : 'default'}>
                    SSE: {formatSseState(sseState)}
                  </Tag>
                </Space>
              }
            >
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                <Select
                  value={selectedJobId}
                  onChange={setSelectedJobId}
                  options={allJobs.map((job) => ({
                    value: job.id,
                    label: `Задача #${job.order} (${job.status})`,
                  }))}
                  placeholder="Выберите задачу"
                />

                {selectedJob ? (
                  <>
                    <Space>
                      <Typography.Text type="secondary">Статус:</Typography.Text>
                      <StatusTag status={selectedJob.status} />
                    </Space>
                    <Typography.Text strong>История</Typography.Text>
                    <pre className="log-console">{historicalLogs || 'История пока пустая'}</pre>
                    <Typography.Text strong>Поток в реальном времени</Typography.Text>
                    <pre className="log-console log-console-live">
                      {liveLogs.length ? liveLogs.join('\n') : 'Ожидание новых событий...'}
                    </pre>
                  </>
                ) : (
                  <Empty description="Выберите задачу для просмотра логов" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
              </Space>
            </Card>
          </Col>
        </Row>
      )}
    </div>
  );
}

function deriveStageStatus(statuses: JobStatus[]): 'wait' | 'process' | 'finish' | 'error' {
  if (statuses.length === 0) {
    return 'wait';
  }
  if (statuses.some((status) => status === 'FAILED' || status === 'CANCELED')) {
    return 'error';
  }
  if (statuses.every((status) => status === 'SUCCESS')) {
    return 'finish';
  }
  if (statuses.some((status) => status === 'RUNNING' || status === 'QUEUED')) {
    return 'process';
  }
  return 'wait';
}

function formatSseState(state: 'idle' | 'connecting' | 'connected'): string {
  if (state === 'connected') {
    return 'подключено';
  }
  if (state === 'connecting') {
    return 'подключение';
  }
  return 'ожидание';
}

function isFinalStatus(status: JobStatus): boolean {
  return status === 'SUCCESS' || status === 'FAILED' || status === 'CANCELED';
}
