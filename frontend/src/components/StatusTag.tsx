import { Tag } from 'antd';
import type { JobStatus } from '../types/api';

interface Props {
  status: JobStatus | string;
}

const statusMeta: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '\u041e\u0436\u0438\u0434\u0430\u043d\u0438\u0435' },
  QUEUED: { color: 'processing', label: '\u0412 \u043e\u0447\u0435\u0440\u0435\u0434\u0438' },
  RUNNING: { color: 'blue', label: '\u0412\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u0442\u0441\u044f' },
  SUCCESS: { color: 'green', label: '\u0423\u0441\u043f\u0435\u0448\u043d\u043e' },
  FAILED: { color: 'red', label: '\u041e\u0448\u0438\u0431\u043a\u0430' },
  CANCELED: { color: 'orange', label: '\u041e\u0442\u043c\u0435\u043d\u0435\u043d\u043e' },
};

export function StatusTag({ status }: Props) {
  const normalized = status.toUpperCase();
  const meta = statusMeta[normalized] ?? { color: 'default', label: normalized };
  return <Tag color={meta.color}>{meta.label}</Tag>;
}