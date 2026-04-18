import type { JobTemplateResponse } from '../types/api';

export interface TemplateField {
  path: string[];
  key: string;
  label: string;
  type: 'string' | 'number' | 'boolean' | 'json';
}

export interface TemplateMeta {
  category: string;
  categoryLabel: string;
  action: string;
  actionLabel: string;
}

const CATEGORY_LABELS: Record<string, string> = {
  vsc: 'Загрузка кода',
  build: 'Сборка',
  test: 'Тестирование',
  deploy: 'Деплой',
  fuzzing: 'Фаззинг',
};

const ACTION_LABELS: Record<string, string> = {
  git: 'Git',
  mercurial: 'Mercurial',
  maven: 'Maven',
  gradle: 'Gradle',
  javac: 'Javac',
  gcc: 'GCC',
  windows: 'Windows',
  linux: 'Linux',
  cmd: 'CMD',
  bash: 'Bash',
  fuzzing: 'Fuzzing',
};

export function resolveTemplateMeta(path: string): TemplateMeta {
  const parts = path.split('/').filter(Boolean);
  const category = parts[0] ?? 'custom';
  const action = parts[parts.length - 1] ?? category;

  return {
    category,
    action,
    categoryLabel: CATEGORY_LABELS[category] ?? capitalize(category),
    actionLabel: ACTION_LABELS[action] ?? capitalize(action),
  };
}

export function buildTemplateTitle(template: JobTemplateResponse): string {
  const meta = resolveTemplateMeta(template.path);
  return `${meta.categoryLabel}: ${meta.actionLabel}`;
}

export function extractTemplateFields(schema: Record<string, unknown>): TemplateField[] {
  const fields: TemplateField[] = [];
  walk(schema, [], fields);
  return fields;
}

function walk(current: unknown, path: string[], fields: TemplateField[]) {
  if (isPlainObject(current)) {
    for (const [key, value] of Object.entries(current)) {
      walk(value, [...path, key], fields);
    }
    return;
  }

  const last = path[path.length - 1] ?? 'value';
  fields.push({
    path,
    key: path.join('.'),
    label: toLabel(last),
    type: resolveType(current),
  });
}

function resolveType(value: unknown): TemplateField['type'] {
  if (typeof value === 'boolean') {
    return 'boolean';
  }
  if (typeof value === 'number') {
    return 'number';
  }
  if (typeof value === 'string') {
    return 'string';
  }
  return 'json';
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function setDeepValue<T extends Record<string, unknown>>(
  source: T,
  path: string[],
  value: unknown,
): T {
  if (path.length === 0) {
    return source;
  }

  const root: Record<string, unknown> = structuredClone(source);
  let pointer: Record<string, unknown> = root;

  for (let i = 0; i < path.length - 1; i += 1) {
    const segment = path[i];
    const current = pointer[segment];
    if (!isPlainObject(current)) {
      pointer[segment] = {};
    }
    pointer = pointer[segment] as Record<string, unknown>;
  }

  pointer[path[path.length - 1]] = value;
  return root as T;
}

export function getDeepValue(source: Record<string, unknown>, path: string[]): unknown {
  let pointer: unknown = source;
  for (const segment of path) {
    if (!isPlainObject(pointer)) {
      return undefined;
    }
    pointer = pointer[segment];
  }
  return pointer;
}

export function capitalize(value: string): string {
  if (!value) {
    return value;
  }
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function toLabel(input: string): string {
  return input
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/^./, (char) => char.toUpperCase());
}
