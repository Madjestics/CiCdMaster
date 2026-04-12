import { Input, InputNumber, Switch, Typography } from 'antd';
import type { TemplateField } from '../utils/template';
import { getDeepValue, setDeepValue } from '../utils/template';

interface Props {
  fields: TemplateField[];
  value: Record<string, unknown>;
  onChange: (next: Record<string, unknown>) => void;
}

export function TemplateParamForm({ fields, value, onChange }: Props) {
  if (fields.length === 0) {
    return (
      <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
        {'\u0414\u043b\u044f \u0432\u044b\u0431\u0440\u0430\u043d\u043d\u043e\u0433\u043e \u0448\u0430\u0431\u043b\u043e\u043d\u0430 \u043d\u0435\u0442 \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u043e\u0432.'}
      </Typography.Paragraph>
    );
  }

  return (
    <div className="param-grid">
      {fields.map((field) => (
        <div className="param-item" key={field.key}>
          <Typography.Text className="param-label">{field.label}</Typography.Text>
          {renderField(field, value, onChange)}
        </div>
      ))}
    </div>
  );
}

function renderField(
  field: TemplateField,
  source: Record<string, unknown>,
  onChange: (next: Record<string, unknown>) => void,
) {
  const current = getDeepValue(source, field.path);

  if (field.type === 'boolean') {
    return (
      <Switch
        checked={Boolean(current)}
        onChange={(checked) => onChange(setDeepValue(source, field.path, checked))}
      />
    );
  }

  if (field.type === 'number') {
    return (
      <InputNumber
        style={{ width: '100%' }}
        value={typeof current === 'number' ? current : undefined}
        onChange={(num) => onChange(setDeepValue(source, field.path, num ?? 0))}
      />
    );
  }

  if (field.type === 'json') {
    return (
      <Input.TextArea
        autoSize={{ minRows: 2, maxRows: 5 }}
        value={current == null ? '' : JSON.stringify(current)}
        onChange={(event) => {
          const raw = event.target.value;
          try {
            const parsed = raw.trim() ? JSON.parse(raw) : {};
            onChange(setDeepValue(source, field.path, parsed));
          } catch {
            onChange(setDeepValue(source, field.path, raw));
          }
        }}
      />
    );
  }

  return (
    <Input
      value={typeof current === 'string' ? current : ''}
      onChange={(event) => onChange(setDeepValue(source, field.path, event.target.value))}
    />
  );
}