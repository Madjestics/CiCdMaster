import { Tag } from 'antd';
import type { JobStatus } from '../types/api';

interface Props {
  status: JobStatus | string;
}

const statusMeta: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: 'Ожидание' },
  QUEUED: { color: 'processing', label: 'В очереди' },
  RUNNING: { color: 'blue', label: 'Выполняется' },
  SUCCESS: { color: 'green', label: 'Успешно' },
  FAILED: { color: 'red', label: 'Ошибка' },
  CANCELED: { color: 'orange', label: 'Отменено' },
  UNKNOWN: { color: 'default', label: 'Неизвестно' },
};

export function StatusTag({ status }: Props) {
  const normalized = String(status).toUpperCase();
  const meta = statusMeta[normalized] ?? { color: 'default', label: normalized };
  return <Tag color={meta.color}>{meta.label}</Tag>;
}
