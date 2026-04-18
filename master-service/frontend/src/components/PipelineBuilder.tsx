import {
  Button,
  Card,
  Col,
  Divider,
  Empty,
  Flex,
  Input,
  List,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  CodeOutlined,
  DeleteOutlined,
  NodeIndexOutlined,
  PlusOutlined,
  RocketOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import type { BuilderJobDraft, BuilderJobMode, BuilderStageDraft, JobTemplateResponse, UUID } from '../types/api';
import { buildTemplateTitle, extractTemplateFields, resolveTemplateMeta } from '../utils/template';
import { TemplateParamForm } from './TemplateParamForm';

interface Props {
  templates: JobTemplateResponse[];
  templatesLoading: boolean;
  onPipelineCreated?: (pipelineId: UUID) => void;
}

interface JobModalState {
  open: boolean;
  stageId?: string;
}

const initialStage = (): BuilderStageDraft => ({
  localId: createId(),
  name: 'Загрузка и сборка',
  description: 'Базовый этап загрузки и сборки',
  jobs: [],
});

export function PipelineBuilder({ templates, templatesLoading, onPipelineCreated }: Props) {
  const [pipelineName, setPipelineName] = useState('');
  const [pipelineDescription, setPipelineDescription] = useState('');
  const [stages, setStages] = useState<BuilderStageDraft[]>([initialStage()]);
  const [saving, setSaving] = useState(false);

  const [jobModal, setJobModal] = useState<JobModalState>({ open: false });
  const [jobMode, setJobMode] = useState<BuilderJobMode>('template');
  const [selectedCategory, setSelectedCategory] = useState<string>();
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>();
  const [paramsDraft, setParamsDraft] = useState<Record<string, unknown>>({});
  const [scriptDraft, setScriptDraft] = useState('');

  const templatesByCategory = useMemo(() => {
    const grouped = new Map<string, JobTemplateResponse[]>();
    for (const template of templates) {
      const meta = resolveTemplateMeta(template.path);
      if (!grouped.has(meta.category)) {
        grouped.set(meta.category, []);
      }
      grouped.get(meta.category)?.push(template);
    }
    return grouped;
  }, [templates]);

  const categoryOptions = useMemo(() => {
    return [...templatesByCategory.entries()].map(([category, entries]) => {
      const label = resolveTemplateMeta(entries[0].path).categoryLabel;
      return { value: category, label };
    });
  }, [templatesByCategory]);

  const availableTemplates = useMemo(() => {
    if (!selectedCategory) {
      return [];
    }
    return templatesByCategory.get(selectedCategory) ?? [];
  }, [templatesByCategory, selectedCategory]);

  const selectedTemplate = useMemo(
    () => templates.find((template) => template.id === selectedTemplateId),
    [templates, selectedTemplateId],
  );

  const templateFields = useMemo(() => {
    if (!selectedTemplate) {
      return [];
    }
    return extractTemplateFields(selectedTemplate.paramsTemplate);
  }, [selectedTemplate]);

  const selectedStage = useMemo(
    () => stages.find((stage) => stage.localId === jobModal.stageId),
    [stages, jobModal.stageId],
  );

  const openJobModal = (stageId: string) => {
    setJobModal({ open: true, stageId });
    setJobMode('template');
    setSelectedCategory(categoryOptions[0]?.value);
    const initialTemplate = categoryOptions.length
      ? (templatesByCategory.get(categoryOptions[0].value) ?? [])[0]
      : undefined;
    setSelectedTemplateId(initialTemplate?.id);
    setParamsDraft(initialTemplate ? structuredClone(initialTemplate.paramsTemplate) : {});
    setScriptDraft('');
  };

  const closeJobModal = () => {
    setJobModal({ open: false });
  };

  const handleCategoryChange = (nextCategory: string) => {
    setSelectedCategory(nextCategory);
    const first = (templatesByCategory.get(nextCategory) ?? [])[0];
    setSelectedTemplateId(first?.id);
    setParamsDraft(first ? structuredClone(first.paramsTemplate) : {});
  };

  const handleTemplateChange = (templateId: string) => {
    setSelectedTemplateId(templateId);
    const template = templates.find((entry) => entry.id === templateId);
    setParamsDraft(template ? structuredClone(template.paramsTemplate) : {});
  };

  const addStage = () => {
    setStages((prev) => [
      ...prev,
      {
        localId: createId(),
        name: `Этап ${prev.length + 1}`,
        description: '',
        jobs: [],
      },
    ]);
  };

  const updateStage = (stageId: string, patch: Partial<BuilderStageDraft>) => {
    setStages((prev) =>
      prev.map((stage) => (stage.localId === stageId ? { ...stage, ...patch } : stage)),
    );
  };

  const removeStage = (stageId: string) => {
    setStages((prev) => prev.filter((stage) => stage.localId !== stageId));
  };

  const removeJob = (stageId: string, jobId: string) => {
    setStages((prev) =>
      prev.map((stage) =>
        stage.localId === stageId
          ? { ...stage, jobs: stage.jobs.filter((job) => job.localId !== jobId) }
          : stage,
      ),
    );
  };

  const appendJobToStage = () => {
    if (!jobModal.stageId) {
      return;
    }

    if (jobMode === 'template' && !selectedTemplate) {
      message.error('Выберите шаблон задачи.');
      return;
    }

    if (jobMode === 'script' && !scriptDraft.trim()) {
      message.error('Введите скрипт для ручной задачи.');
      return;
    }

    const nextJob: BuilderJobDraft =
      jobMode === 'template'
        ? {
            localId: createId(),
            mode: 'template',
            templateId: selectedTemplate?.id,
            templatePath: selectedTemplate?.path,
            params: paramsDraft,
            script: '',
            scriptPrimary: false,
          }
        : {
            localId: createId(),
            mode: 'script',
            script: scriptDraft,
            scriptPrimary: true,
          };

    setStages((prev) =>
      prev.map((stage) =>
        stage.localId === jobModal.stageId
          ? {
              ...stage,
              jobs: [...stage.jobs, nextJob],
            }
          : stage,
      ),
    );

    closeJobModal();
  };

  const submitPipeline = async () => {
    if (!pipelineName.trim()) {
      message.error('Укажите название пайплайна.');
      return;
    }

    if (stages.length === 0) {
      message.error('Добавьте хотя бы один этап.');
      return;
    }

    if (stages.some((stage) => stage.jobs.length === 0)) {
      message.error('Каждый этап должен содержать минимум одну задачу.');
      return;
    }

    try {
      setSaving(true);

      const pipeline = await api.createPipeline({
        name: pipelineName.trim(),
        description: pipelineDescription.trim(),
      });

      for (let stageIndex = 0; stageIndex < stages.length; stageIndex += 1) {
        const stage = stages[stageIndex];
        const createdStage = await api.createStage({
          pipelineId: pipeline.id,
          order: stageIndex + 1,
          name: stage.name.trim() || `Этап ${stageIndex + 1}`,
          description: stage.description?.trim() || '',
        });

        for (let jobIndex = 0; jobIndex < stage.jobs.length; jobIndex += 1) {
          const job = stage.jobs[jobIndex];
          await api.createJob({
            stageId: createdStage.id,
            order: jobIndex + 1,
            status: 'PENDING',
            script: job.script?.trim() || '',
            scriptPrimary: job.scriptPrimary,
            jobTemplateId: job.mode === 'template' ? job.templateId : undefined,
            params: job.mode === 'template' ? job.params : undefined,
          });
        }
      }

      message.success('Пайплайн успешно создан.');
      setPipelineName('');
      setPipelineDescription('');
      setStages([initialStage()]);
      onPipelineCreated?.(pipeline.id);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Не удалось создать пайплайн.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <Card className="soft-card">
        <Flex vertical gap={18}>
          <div>
            <Typography.Title level={3} style={{ marginBottom: 8 }}>
              Конструктор пайплайна
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              Соберите пайплайн как конструктор: этапы, шаблонные задачи и ручные скрипты.
            </Typography.Paragraph>
          </div>

          <Row gutter={[16, 16]}>
            <Col xs={24} md={12}>
              <Typography.Text strong>Название пайплайна</Typography.Text>
              <Input
                size="large"
                placeholder="Например: Поставка Java-сервиса"
                value={pipelineName}
                onChange={(event) => setPipelineName(event.target.value)}
              />
            </Col>
            <Col xs={24} md={12}>
              <Typography.Text strong>Описание</Typography.Text>
              <Input
                size="large"
                placeholder="Кратко о назначении"
                value={pipelineDescription}
                onChange={(event) => setPipelineDescription(event.target.value)}
              />
            </Col>
          </Row>
        </Flex>
      </Card>

      <div style={{ height: 18 }} />

      <Card className="soft-card" loading={templatesLoading}>
        <Flex align="center" justify="space-between" style={{ marginBottom: 10 }}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            Этапы и задачи
          </Typography.Title>
          <Button type="dashed" icon={<PlusOutlined />} onClick={addStage}>
            Добавить этап
          </Button>
        </Flex>

        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          {stages.map((stage, index) => (
            <Card
              key={stage.localId}
              className="stage-card"
              variant="outlined"
              title={
                <Flex align="center" gap={10}>
                  <Tag color="geekblue">Этап {index + 1}</Tag>
                  <Input
                    placeholder="Название этапа"
                    value={stage.name}
                    onChange={(event) => updateStage(stage.localId, { name: event.target.value })}
                    style={{ maxWidth: 360 }}
                  />
                </Flex>
              }
              extra={
                <Tooltip title="Удалить этап">
                  <Button
                    danger
                    type="text"
                    icon={<DeleteOutlined />}
                    onClick={() => removeStage(stage.localId)}
                    disabled={stages.length === 1}
                  />
                </Tooltip>
              }
            >
              <Flex vertical gap={12}>
                <Input
                  placeholder="Описание этапа"
                  value={stage.description}
                  onChange={(event) => updateStage(stage.localId, { description: event.target.value })}
                />

                {stage.jobs.length === 0 ? (
                  <Empty
                    description="Пока нет задач"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    styles={{ image: { height: 44 } }}
                  />
                ) : (
                  <List
                    dataSource={stage.jobs}
                    renderItem={(job, jobIndex) => (
                      <List.Item
                        actions={[
                          <Button
                            key="remove"
                            danger
                            type="text"
                            icon={<DeleteOutlined />}
                            onClick={() => removeJob(stage.localId, job.localId)}
                          />,
                        ]}
                      >
                        <List.Item.Meta
                          avatar={job.mode === 'template' ? <NodeIndexOutlined /> : <CodeOutlined />}
                          title={
                            <Space>
                              <Tag color="cyan">Задача {jobIndex + 1}</Tag>
                              {job.mode === 'template'
                                ? buildTemplateTitle({
                                    id: job.templateId ?? '',
                                    path: job.templatePath ?? 'template',
                                    paramsTemplate: {},
                                  } as JobTemplateResponse)
                                : 'Ручной скрипт'}
                            </Space>
                          }
                          description={
                            job.mode === 'template'
                              ? `Путь шаблона: ${job.templatePath}`
                              : (job.script || '').slice(0, 110)
                          }
                        />
                      </List.Item>
                    )}
                  />
                )}

                <Button icon={<PlusOutlined />} onClick={() => openJobModal(stage.localId)}>
                  Добавить задачу
                </Button>
              </Flex>
            </Card>
          ))}
        </Space>

        <Divider />

        <Flex justify="end">
          <Button
            size="large"
            type="primary"
            icon={<RocketOutlined />}
            loading={saving}
            onClick={submitPipeline}
          >
            Сохранить пайплайн
          </Button>
        </Flex>
      </Card>

      <Modal
        title={`Новая задача${selectedStage ? ` для этапа «${selectedStage.name}»` : ''}`}
        open={jobModal.open}
        onCancel={closeJobModal}
        onOk={appendJobToStage}
        okText="Добавить"
        width={720}
      >
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            value={jobMode}
            onChange={(event) => setJobMode(event.target.value as BuilderJobMode)}
            options={[
              { value: 'template', label: 'Шаблонная задача' },
              { value: 'script', label: 'Ручной скрипт' },
            ]}
          />

          {jobMode === 'template' ? (
            <>
              <Row gutter={12}>
                <Col span={11}>
                  <Typography.Text strong>Тип задачи</Typography.Text>
                  <Select
                    style={{ width: '100%' }}
                    value={selectedCategory}
                    options={categoryOptions}
                    onChange={handleCategoryChange}
                    placeholder="Выберите тип"
                  />
                </Col>
                <Col span={13}>
                  <Typography.Text strong>Вариант шаблона</Typography.Text>
                  <Select
                    style={{ width: '100%' }}
                    value={selectedTemplateId}
                    onChange={handleTemplateChange}
                    options={availableTemplates.map((template) => ({
                      value: template.id,
                      label: `${buildTemplateTitle(template)} (${template.path})`,
                    }))}
                    placeholder="Выберите шаблон"
                  />
                </Col>
              </Row>

              <Card size="small" title={<Space><SettingOutlined /> Параметры шаблона</Space>}>
                <TemplateParamForm fields={templateFields} value={paramsDraft} onChange={setParamsDraft} />
              </Card>
            </>
          ) : (
            <>
              <Typography.Text strong>Скрипт выполнения</Typography.Text>
              <Input.TextArea
                placeholder="Введите shell/cmd/bash скрипт"
                autoSize={{ minRows: 8, maxRows: 16 }}
                value={scriptDraft}
                onChange={(event) => setScriptDraft(event.target.value)}
              />
            </>
          )}
        </Space>
      </Modal>
    </div>
  );
}

function createId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return Math.random().toString(16).slice(2);
}
