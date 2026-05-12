import { Button, Card, Col, Empty, Flex, Row, Space, Typography } from 'antd';
import { FolderOpenOutlined, FolderOutlined, PlusOutlined, RocketOutlined } from '@ant-design/icons';
import type { FolderResponse, PipelineResponse, UUID } from '../types/api';

interface Props {
  title: string;
  breadcrumbs: string[];
  folders: FolderResponse[];
  pipelines: PipelineResponse[];
  onOpenFolder: (id: UUID) => void;
  onOpenPipeline: (id: UUID) => void;
  onCreateFolder: () => void;
  onCreatePipeline: () => void;
}

export function FolderWorkspace({
  title,
  breadcrumbs,
  folders,
  pipelines,
  onOpenFolder,
  onOpenPipeline,
  onCreateFolder,
  onCreatePipeline,
}: Props) {
  const hasItems = folders.length > 0 || pipelines.length > 0;

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card className="soft-card">
        <Flex align="center" justify="space-between" gap={12} wrap>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              {title}
            </Typography.Title>
            <Typography.Text type="secondary">
              {breadcrumbs.length ? breadcrumbs.join(' / ') : 'Корень рабочего пространства'}
            </Typography.Text>
          </div>
          <Space wrap>
            <Button icon={<PlusOutlined />} onClick={onCreateFolder}>
              Создать подпапку
            </Button>
            <Button type="primary" icon={<RocketOutlined />} onClick={onCreatePipeline}>
              Создать пайплайн
            </Button>
          </Space>
        </Flex>
      </Card>

      {!hasItems ? (
        <Card className="soft-card">
          <Empty description="Папка пока пустая. Создайте подпапку или новый пайплайн." />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={12}>
            <Card
              className="soft-card"
              title={
                <Space>
                  <FolderOutlined />
                  <span>Подпапки</span>
                </Space>
              }
            >
              {folders.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Подпапок пока нет" />
              ) : (
                <Space direction="vertical" style={{ width: '100%' }} size={10}>
                  {folders.map((folder) => (
                    <Button
                      key={folder.id}
                      className="folder-list-btn"
                      icon={<FolderOpenOutlined />}
                      onClick={() => onOpenFolder(folder.id)}
                    >
                      {folder.name}
                    </Button>
                  ))}
                </Space>
              )}
            </Card>
          </Col>

          <Col xs={24} xl={12}>
            <Card
              className="soft-card"
              title={
                <Space>
                  <RocketOutlined />
                  <span>Пайплайны</span>
                </Space>
              }
            >
              {pipelines.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Пайплайнов пока нет" />
              ) : (
                <Space direction="vertical" style={{ width: '100%' }} size={10}>
                  {pipelines.map((pipeline) => (
                    <Button
                      key={pipeline.id}
                      className="pipeline-list-btn"
                      onClick={() => onOpenPipeline(pipeline.id)}
                    >
                      <Flex align="center" justify="space-between" style={{ width: '100%' }}>
                        <span>{pipeline.name}</span>
                        <Typography.Text type="secondary">{pipeline.description || 'Без описания'}</Typography.Text>
                      </Flex>
                    </Button>
                  ))}
                </Space>
              )}
            </Card>
          </Col>
        </Row>
      )}
    </Space>
  );
}
