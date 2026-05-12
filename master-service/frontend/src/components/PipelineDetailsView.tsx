import {
  Button,
  Card,
  Col,
  Empty,
  Flex,
  Input,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  ClockCircleOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  ScheduleOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type {
  PipelineResponse,
  PipelineRunJobResponse,
  PipelineRunResponse,
} from '../types/api';
import { StatusTag } from './StatusTag';

interface Props {
  pipeline: PipelineResponse;
  onRefreshWorkspace: () => Promise<void>;
}

export function PipelineDetailsView({ pipeline, onRefreshWorkspace }: Props) {
  const [loading, setLoading] = useState(false);
  const [runs, setRuns] = useState<PipelineRunResponse[]>([]);
  const [selectedRunId, setSelectedRunId] = useState<string>();

  const [initiatedBy, setInitiatedBy] = useState('оператор');
  const [runLoading, setRunLoading] = useState(false);
  const [cancelLoading, setCancelLoading] = useState(false);

  useEffect(() => {
    void loadRuns();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pipeline.id]);

  const selectedRun = useMemo(
    () => runs.find((run) => run.runId === selectedRunId) ?? runs[0],
    [runs, selectedRunId],
  );

  const stageGroups = useMemo(() => {
    if (!selectedRun) {
      return [];
    }

    const map = new Map<string, { stageName: string; stageOrder: number; jobs: PipelineRunJobResponse[] }>();
    for (const job of selectedRun.jobs) {
      const key = `${job.stageOrder}:${job.stageId ?? 'none'}`;
      if (!map.has(key)) {
        map.set(key, {
          stageName: job.stageName,
          stageOrder: job.stageOrder,
          jobs: [],
        });
      }
      map.get(key)?.jobs.push(job);
    }

    return [...map.values()]
      .map((group) => ({
        ...group,
        jobs: group.jobs.slice().sort((a, b) => a.jobOrder - b.jobOrder),
      }))
      .sort((a, b) => a.stageOrder - b.stageOrder);
  }, [selectedRun]);

  async function loadRuns() {
    try {
      setLoading(true);
      const payload = await api.fetchPipelineRuns(pipeline.id);
      setRuns(payload);
      if (payload.length > 0) {
        setSelectedRunId((current) => current ?? payload[0].runId);
      } else {
        setSelectedRunId(undefined);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Не удалось загрузить историю запусков пайплайна.');
    } finally {
      setLoading(false);
    }
  }

  async function handleRunPipeline() {
    if (!initiatedBy.trim()) {
      message.error('Укажите инициатора запуска.');
      return;
    }
    try {
      setRunLoading(true);
      await api.runPipeline(pipeline.id, {
        initiatedBy: initiatedBy.trim(),
        parameters: {},
      });
      message.success('Команда запуска отправлена.');
      await onRefreshWorkspace();
      await loadRuns();
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Ошибка отправки команды запуска.');
    } finally {
      setRunLoading(false);
    }
  }

  async function handleCancelPipeline() {
    try {
      setCancelLoading(true);
      await api.cancelPipeline(pipeline.id, { reason: 'manual_cancel_from_ui' });
      message.success('Команда отмены отправлена.');
      await loadRuns();
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Ошибка отправки команды отмены.');
    } finally {
      setCancelLoading(false);
    }
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card className="soft-card">
        <Flex align="center" justify="space-between" gap={12} wrap>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              {pipeline.name}
            </Typography.Title>
            <Typography.Text type="secondary">{pipeline.description || 'Без описания'}</Typography.Text>
          </div>

          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => void loadRuns()}>
              Обновить
            </Button>
            <Input
              prefix={<ClockCircleOutlined />}
              value={initiatedBy}
              onChange={(event) => setInitiatedBy(event.target.value)}
              style={{ width: 220 }}
              placeholder="Кто запускает"
            />
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={runLoading}
              onClick={handleRunPipeline}
            >
              Запустить
            </Button>
            <Button
              danger
              icon={<PauseCircleOutlined />}
              loading={cancelLoading}
              onClick={handleCancelPipeline}
            >
              Остановить
            </Button>
          </Space>
        </Flex>
      </Card>

      {loading ? (
        <Card className="soft-card">
          <Flex justify="center" style={{ padding: 40 }}>
            <Spin size="large" />
          </Flex>
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={8}>
            <Card
              className="soft-card"
              title={
                <Space>
                  <ScheduleOutlined />
                  <span>Запуски пайплайна</span>
                </Space>
              }
            >
              {runs.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Пока нет запусков" />
              ) : (
                <Space direction="vertical" size={10} style={{ width: '100%' }}>
                  {runs.map((run, index) => (
                    <Button
                      key={run.runId}
                      className={`run-list-btn ${selectedRun?.runId === run.runId ? 'run-list-btn-active' : ''}`}
                      onClick={() => setSelectedRunId(run.runId)}
                    >
                      <Flex vertical align="start" style={{ width: '100%' }}>
                        <Space>
                          <Tag color="blue">#{runs.length - index}</Tag>
                          <StatusTag status={run.status} />
                        </Space>
                        <Typography.Text>{formatDateTime(run.requestedAt)}</Typography.Text>
                        <Typography.Text type="secondary">
                          {run.initiatedBy ? `Инициатор: ${run.initiatedBy}` : 'Инициатор не указан'}
                        </Typography.Text>
                        <Typography.Text type="secondary">
                          Задач в запуске: {run.jobs.length}
                        </Typography.Text>
                      </Flex>
                    </Button>
                  ))}
                </Space>
              )}
            </Card>
          </Col>

          <Col xs={24} xl={16}>
            <Card className="soft-card" title="Детали запуска">
              {!selectedRun ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Выберите запуск для просмотра деталей" />
              ) : (
                <Space direction="vertical" size={14} style={{ width: '100%' }}>
                  <Card size="small" className="run-summary-card">
                    <Flex gap={18} wrap>
                      <div>
                        <Typography.Text type="secondary">Статус запуска</Typography.Text>
                        <div>
                          <StatusTag status={selectedRun.status} />
                        </div>
                      </div>
                      <div>
                        <Typography.Text type="secondary">Время запроса</Typography.Text>
                        <div>{formatDateTime(selectedRun.requestedAt)}</div>
                      </div>
                      <div>
                        <Typography.Text type="secondary">Начало</Typography.Text>
                        <div>{formatDateTime(selectedRun.startedAt)}</div>
                      </div>
                      <div>
                        <Typography.Text type="secondary">Длительность</Typography.Text>
                        <div>{selectedRun.duration ?? 0} мс</div>
                      </div>
                    </Flex>
                  </Card>

                  {stageGroups.length === 0 ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="В выбранном запуске нет задач" />
                  ) : (
                    stageGroups.map((group) => (
                      <Card
                        key={`${group.stageOrder}-${group.stageName}`}
                        className="stage-card"
                        size="small"
                        title={
                          <Space>
                            <Tag color="geekblue">Этап {group.stageOrder}</Tag>
                            <Typography.Text strong>{group.stageName}</Typography.Text>
                          </Space>
                        }
                      >
                        <Table
                          size="small"
                          rowKey="historyId"
                          pagination={false}
                          dataSource={group.jobs}
                          columns={[
                            {
                              title: 'Задача',
                              width: 360,
                              render: (_, job: PipelineRunJobResponse) => (
                                <Space direction="vertical" size={2}>
                                  <Typography.Text strong>#{job.jobOrder}</Typography.Text>
                                  <Typography.Text type="secondary">{job.jobLabel}</Typography.Text>
                                </Space>
                              ),
                            },
                            {
                              title: 'Статус',
                              width: 130,
                              render: (_, job: PipelineRunJobResponse) => <StatusTag status={job.status} />,
                            },
                            {
                              title: 'Длительность',
                              dataIndex: 'duration',
                              width: 120,
                              render: (value: number) => `${value ?? 0} мс`,
                            },
                            {
                              title: 'Старт',
                              dataIndex: 'startDate',
                              render: (value: string) => formatDateTime(value),
                            },
                          ]}
                          expandable={{
                            expandedRowRender: (job: PipelineRunJobResponse) => (
                              <div>
                                <Typography.Text type="secondary">Логи задачи</Typography.Text>
                                <pre className="log-console">{job.logs || 'Логи отсутствуют'}</pre>
                              </div>
                            ),
                            rowExpandable: (job: PipelineRunJobResponse) => Boolean(job.logs),
                          }}
                        />
                      </Card>
                    ))
                  )}
                </Space>
              )}
            </Card>
          </Col>
        </Row>
      )}
    </Space>
  );
}

function formatDateTime(value?: string): string {
  if (!value) {
    return '-';
  }
  const asDate = new Date(value);
  if (Number.isNaN(asDate.getTime())) {
    return value;
  }
  return asDate.toLocaleString('ru-RU');
}
