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
          : '\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u0448\u0430\u0431\u043b\u043e\u043d\u044b \u0434\u0436\u043e\u0431.',
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
          : '\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u043f\u0430\u0439\u043f\u043b\u0430\u0439\u043d\u044b.',
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
              CI/CD Master Console
            </Typography.Title>
            <Typography.Text style={{ color: 'rgba(249,250,251,.8)' }}>
              {
                '\u041e\u0440\u043a\u0435\u0441\u0442\u0440\u0430\u0446\u0438\u044f \u043f\u0430\u0439\u043f\u043b\u0430\u0439\u043d\u043e\u0432, \u044d\u0442\u0430\u043f\u043e\u0432 \u0438 live-\u043b\u043e\u0433\u043e\u0432 executor \u0441\u0435\u0440\u0432\u0438\u0441\u043e\u0432'
              }
            </Typography.Text>
          </div>

          <Segmented<ViewMode>
            className="mode-switch"
            value={mode}
            onChange={setMode}
            options={[
              { label: <Space><BuildOutlined /> Builder</Space>, value: 'builder' },
              { label: <Space><DashboardOutlined /> Monitor</Space>, value: 'monitor' },
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