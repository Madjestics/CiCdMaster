import {
  Button,
  Card,
  ConfigProvider,
  Drawer,
  Form,
  Input,
  Layout,
  Modal,
  Space,
  Spin,
  Tree,
  Typography,
  message,
} from 'antd';
import {
  FolderOpenOutlined,
  FolderOutlined,
  PlusOutlined,
  ReloadOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import type { DataNode } from 'antd/es/tree';
import { api } from './api/client';
import type { FolderResponse, JobTemplateResponse, PipelineResponse, UUID } from './types/api';
import { FolderWorkspace } from './components/FolderWorkspace';
import { PipelineBuilder } from './components/PipelineBuilder';
import { PipelineDetailsView } from './components/PipelineDetailsView';

const { Header, Sider, Content } = Layout;

type Selection =
  | { kind: 'root' }
  | { kind: 'folder'; id: UUID }
  | { kind: 'pipeline'; id: UUID };

interface FolderFormValues {
  name: string;
  description?: string;
}

export default function App() {
  const [templates, setTemplates] = useState<JobTemplateResponse[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [folders, setFolders] = useState<FolderResponse[]>([]);
  const [pipelines, setPipelines] = useState<PipelineResponse[]>([]);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selection, setSelection] = useState<Selection>({ kind: 'root' });
  const [expandedKeys, setExpandedKeys] = useState<string[]>(['root']);

  const [isFolderModalOpen, setIsFolderModalOpen] = useState(false);
  const [folderSaving, setFolderSaving] = useState(false);
  const [folderForm] = Form.useForm<FolderFormValues>();

  const [isPipelineDrawerOpen, setIsPipelineDrawerOpen] = useState(false);

  const folderById = useMemo(() => {
    const index = new Map<UUID, FolderResponse>();
    for (const folder of folders) {
      index.set(folder.id, folder);
    }
    return index;
  }, [folders]);

  const pipelineById = useMemo(() => {
    const index = new Map<UUID, PipelineResponse>();
    for (const pipeline of pipelines) {
      index.set(pipeline.id, pipeline);
    }
    return index;
  }, [pipelines]);

  useEffect(() => {
    void bootstrap();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function bootstrap() {
    try {
      setLoading(true);
      await Promise.all([loadTemplates(), loadStructure()]);
    } finally {
      setLoading(false);
    }
  }

  async function loadTemplates() {
    try {
      setTemplatesLoading(true);
      const payload = await api.fetchJobTemplates();
      setTemplates(payload);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Не удалось загрузить шаблоны задач.');
    } finally {
      setTemplatesLoading(false);
    }
  }

  async function loadStructure() {
    try {
      const [folderPayload, pipelinePayload] = await Promise.all([api.fetchFolders(), api.fetchPipelines()]);
      setFolders(folderPayload);
      setPipelines(pipelinePayload);
      keepSelectionValid(folderPayload, pipelinePayload);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Не удалось загрузить структуру папок и пайплайнов.');
    }
  }

  function keepSelectionValid(nextFolders: FolderResponse[], nextPipelines: PipelineResponse[]) {
    if (selection.kind === 'folder' && !nextFolders.some((folder) => folder.id === selection.id)) {
      setSelection({ kind: 'root' });
    }
    if (selection.kind === 'pipeline' && !nextPipelines.some((pipeline) => pipeline.id === selection.id)) {
      setSelection({ kind: 'root' });
    }
  }

  const selectedPipeline =
    selection.kind === 'pipeline' ? pipelineById.get(selection.id) ?? null : null;

  const contentFolderId = useMemo<UUID | null>(() => {
    if (selection.kind === 'root') {
      return null;
    }
    if (selection.kind === 'folder') {
      return selection.id;
    }
    return selectedPipeline?.folderId ?? null;
  }, [selection, selectedPipeline]);

  const currentFolders = useMemo(() => {
    return folders
      .filter((folder) => (folder.parentId ?? null) === contentFolderId)
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [folders, contentFolderId]);

  const currentPipelines = useMemo(() => {
    return pipelines
      .filter((pipeline) => (pipeline.folderId ?? null) === contentFolderId)
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [pipelines, contentFolderId]);

  const breadcrumbs = useMemo(() => {
    if (contentFolderId == null) {
      return ['Рабочее пространство'];
    }
    const result: string[] = ['Рабочее пространство'];
    const visited = new Set<UUID>();
    let cursor: UUID | null = contentFolderId;
    const stack: string[] = [];
    while (cursor && !visited.has(cursor)) {
      visited.add(cursor);
      const folder = folderById.get(cursor);
      if (!folder) {
        break;
      }
      stack.push(folder.name);
      cursor = folder.parentId;
    }
    return [...result, ...stack.reverse()];
  }, [contentFolderId, folderById]);

  const treeData = useMemo<DataNode[]>(() => {
    const folderChildrenIndex = new Map<string, FolderResponse[]>();
    const pipelinesIndex = new Map<string, PipelineResponse[]>();

    for (const folder of folders) {
      const key = folder.parentId ?? 'root';
      if (!folderChildrenIndex.has(key)) {
        folderChildrenIndex.set(key, []);
      }
      folderChildrenIndex.get(key)?.push(folder);
    }

    for (const pipeline of pipelines) {
      const key = pipeline.folderId ?? 'root';
      if (!pipelinesIndex.has(key)) {
        pipelinesIndex.set(key, []);
      }
      pipelinesIndex.get(key)?.push(pipeline);
    }

    const buildFolderNode = (folder: FolderResponse): DataNode => {
      const childFolders = (folderChildrenIndex.get(folder.id) ?? []).sort((a, b) => a.name.localeCompare(b.name));
      const childPipelines = (pipelinesIndex.get(folder.id) ?? []).sort((a, b) => a.name.localeCompare(b.name));
      return {
        key: `folder:${folder.id}`,
        title: (
          <Space size={6}>
            <FolderOutlined />
            <span>{folder.name}</span>
          </Space>
        ),
        children: [
          ...childFolders.map(buildFolderNode),
          ...childPipelines.map((pipeline) => ({
            key: `pipeline:${pipeline.id}`,
            title: (
              <Space size={6}>
                <RocketOutlined />
                <span>{pipeline.name}</span>
              </Space>
            ),
            isLeaf: true,
          })),
        ],
      };
    };

    const rootFolders = (folderChildrenIndex.get('root') ?? []).sort((a, b) => a.name.localeCompare(b.name));
    const rootPipelines = (pipelinesIndex.get('root') ?? []).sort((a, b) => a.name.localeCompare(b.name));

    return [
      {
        key: 'root',
        title: (
          <Space size={6}>
            <FolderOpenOutlined />
            <span>Рабочее пространство</span>
          </Space>
        ),
        children: [
          ...rootFolders.map(buildFolderNode),
          ...rootPipelines.map((pipeline) => ({
            key: `pipeline:${pipeline.id}`,
            title: (
              <Space size={6}>
                <RocketOutlined />
                <span>{pipeline.name}</span>
              </Space>
            ),
            isLeaf: true,
          })),
        ],
      },
    ];
  }, [folders, pipelines]);

  function selectedTreeKey() {
    if (selection.kind === 'root') {
      return ['root'];
    }
    if (selection.kind === 'folder') {
      return [`folder:${selection.id}`];
    }
    return [`pipeline:${selection.id}`];
  }

  function onTreeSelect(keys: Array<string | number>) {
    const [raw] = keys;
    if (!raw || typeof raw !== 'string' || raw === 'root') {
      setSelection({ kind: 'root' });
      return;
    }

    if (raw.startsWith('folder:')) {
      const id = raw.replace('folder:', '') as UUID;
      setSelection({ kind: 'folder', id });
      setExpandedKeys((prev) => (prev.includes(raw) ? prev : [...prev, raw]));
      return;
    }

    if (raw.startsWith('pipeline:')) {
      const id = raw.replace('pipeline:', '') as UUID;
      setSelection({ kind: 'pipeline', id });
    }
  }

  async function handleRefresh() {
    try {
      setRefreshing(true);
      await loadStructure();
    } finally {
      setRefreshing(false);
    }
  }

  function openCreateFolderModal() {
    folderForm.resetFields();
    setIsFolderModalOpen(true);
  }

  async function submitFolder() {
    try {
      const values = await folderForm.validateFields();
      setFolderSaving(true);
      const created = await api.createFolder({
        name: values.name.trim(),
        description: values.description?.trim(),
        parentId: contentFolderId ?? undefined,
      });
      setIsFolderModalOpen(false);
      await loadStructure();
      setSelection({ kind: 'folder', id: created.id });
      setExpandedKeys((prev) => {
        const key = `folder:${created.id}`;
        return prev.includes(key) ? prev : [...prev, key];
      });
      message.success('Папка создана.');
    } catch (error) {
      if (error instanceof Error) {
          message.error(error.message);
      }
    } finally {
      setFolderSaving(false);
    }
  }

  function openPipelineBuilder() {
    setIsPipelineDrawerOpen(true);
  }

  if (loading) {
    return (
      <div className="app-loader">
        <Spin size="large" />
      </div>
    );
  }

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#0a7f70',
          colorInfo: '#0369a1',
          borderRadius: 14,
          wireframe: false,
          fontFamily: "Manrope, 'Trebuchet MS', 'Segoe UI', sans-serif",
        },
      }}
    >
      <Layout className="workspace-shell">
        <Header className="workspace-header">
          <div>
            <Typography.Title level={3} style={{ margin: 0, color: '#f8fafc' }}>
              CI/CD: управление пайплайнами
            </Typography.Title>
            <Typography.Text style={{ color: 'rgba(248,250,252,.78)' }}>
              Иерархия папок, запуски пайплайнов и просмотр логов в одном интерфейсе
            </Typography.Text>
          </div>
          <Space>
            <Button icon={<ReloadOutlined />} loading={refreshing} onClick={() => void handleRefresh()}>
              Обновить
            </Button>
          </Space>
        </Header>

        <Layout>
          <Sider width={320} className="workspace-sider">
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Button icon={<PlusOutlined />} onClick={openCreateFolderModal} block>
                Создать папку
              </Button>
              <Button type="primary" icon={<RocketOutlined />} onClick={openPipelineBuilder} block>
                Создать пайплайн
              </Button>
              <Card className="tree-card">
                <Tree
                  showLine
                  treeData={treeData}
                  selectedKeys={selectedTreeKey()}
                  expandedKeys={expandedKeys}
                  onExpand={(keys) => setExpandedKeys(keys.map(String))}
                  onSelect={onTreeSelect}
                />
              </Card>
            </Space>
          </Sider>

          <Content className="workspace-content">
            {selectedPipeline ? (
              <PipelineDetailsView pipeline={selectedPipeline} onRefreshWorkspace={loadStructure} />
            ) : (
              <FolderWorkspace
                title={contentFolderId ? folderById.get(contentFolderId)?.name ?? 'Папка' : 'Корень рабочего пространства'}
                breadcrumbs={breadcrumbs}
                folders={currentFolders}
                pipelines={currentPipelines}
                onOpenFolder={(id) => setSelection({ kind: 'folder', id })}
                onOpenPipeline={(id) => setSelection({ kind: 'pipeline', id })}
                onCreateFolder={openCreateFolderModal}
                onCreatePipeline={openPipelineBuilder}
              />
            )}
          </Content>
        </Layout>
      </Layout>

      <Modal
        title="Создать папку"
        open={isFolderModalOpen}
        onCancel={() => setIsFolderModalOpen(false)}
        onOk={() => void submitFolder()}
        okText="Создать"
        confirmLoading={folderSaving}
      >
        <Form layout="vertical" form={folderForm}>
          <Form.Item
            name="name"
            label="Название папки"
            rules={[{ required: true, message: 'Укажите название папки' }]}
          >
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="description" label="Описание">
            <Input.TextArea autoSize={{ minRows: 2, maxRows: 5 }} maxLength={300} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="Создать пайплайн"
        width={920}
        open={isPipelineDrawerOpen}
        onClose={() => setIsPipelineDrawerOpen(false)}
        destroyOnClose
      >
        <PipelineBuilder
          templates={templates}
          templatesLoading={templatesLoading}
          defaultFolderId={contentFolderId}
          onCancel={() => setIsPipelineDrawerOpen(false)}
          onPipelineCreated={async (pipelineId) => {
            setIsPipelineDrawerOpen(false);
            await loadStructure();
            setSelection({ kind: 'pipeline', id: pipelineId });
          }}
        />
      </Drawer>
    </ConfigProvider>
  );
}
