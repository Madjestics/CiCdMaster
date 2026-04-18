import { Layout, Segmented, Space, Typography, ConfigProvider, message } from 'antd';
import { BuildOutlined, DashboardOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { api } from './api/client';
import { PipelineBuilder } from './components/PipelineBuilder';
import { PipelineMonitor } from './components/PipelineMonitor';
import type { JobTemplateResponse, PipelineResponse } from './types/api';

const { Header, Content } = Layout;

type ViewMode = 'builder' | 'monitor';

export default function App() {
  const [mode, setMode] = useState<ViewMode>('builder');
  const [templates, setTemplates] = useState<JobTemplateResponse[]>([]);
  const [pipelines, setPipelines] = useState<PipelineResponse[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);

  useEffect(() => {
    void Promise.all([loadTemplates(), loadPipelines()]);
  }, []);

  async function loadTemplates() {
    try {
      setTemplatesLoading(true);
      const payload = await api.fetchJobTemplates();
      setTemplates(payload);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : 'Не удалось загрузить шаблоны задач.',
      );
    } finally {
      setTemplatesLoading(false);
    }
  }

  async function loadPipelines() {
    try {
      const payload = await api.fetchPipelines();
      setPipelines(payload);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : 'Не удалось загрузить пайплайны.',
      );
    }
  }

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#0f766e',
          colorInfo: '#0369a1',
          borderRadius: 14,
          wireframe: false,
          fontFamily: "Manrope, 'Trebuchet MS', 'Segoe UI', sans-serif",
        },
      }}
    >
      <Layout className="app-shell">
        <Header className="app-header">
          <div className="brand-area">
            <Typography.Title level={3} style={{ margin: 0, color: '#f9fafb' }}>
              CI/CD Управляющий сервис
            </Typography.Title>
            <Typography.Text style={{ color: 'rgba(249,250,251,.8)' }}>
              {
                'Оркестрация пайплайнов, этапов и журналов сервисов-исполнителей'
              }
            </Typography.Text>
          </div>

          <Segmented<ViewMode>
            className="mode-switch"
            value={mode}
            onChange={setMode}
            options={[
              { label: <Space><BuildOutlined /> Конструктор</Space>, value: 'builder' },
              { label: <Space><DashboardOutlined /> Мониторинг</Space>, value: 'monitor' },
            ]}
          />
        </Header>

        <Content className="app-content">
          {mode === 'builder' ? (
            <PipelineBuilder
              templates={templates}
              templatesLoading={templatesLoading}
              onPipelineCreated={async () => {
                await loadPipelines();
                setMode('monitor');
              }}
            />
          ) : (
            <PipelineMonitor pipelines={pipelines} onReloadPipelines={loadPipelines} />
          )}
        </Content>
      </Layout>
    </ConfigProvider>
  );
}
