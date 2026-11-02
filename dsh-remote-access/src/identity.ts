import { randomUUID } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { hostname } from 'node:os';

export function deviceName(name: unknown): string {
  if (typeof name !== 'string' || !name.trim() || name.trim().length > 64 || /[\x00-\x1f\x7f]/.test(name)) throw new Error('invalid_name');
  return name.trim();
}

/** Installation identity is random; a display-name change never changes identity. */
export const defaultComputerName = () => hostname().replace(/[\x00-\x1f\x7f]/g, '').trim().slice(0, 64) || 'Computer';
export function computerIdentity(home: string, name?: string | null): { id: string; name: string } {
  const directory = join(home, 'remote-access'), file = join(directory, 'identity.json');
  const exists = existsSync(file);
  const value = exists ? JSON.parse(readFileSync(file, 'utf8')) : { id: randomUUID(), name: '', nameSource: 'automatic' };
  if (!/^[a-f0-9-]{36}$/.test(value.id) || typeof value.name !== 'string') throw new Error('Invalid computer identity');
  const previous = JSON.stringify(value);
  // Upgrade the old generic default without replacing an existing custom name or identity.
  if (!value.nameSource) value.nameSource = value.name === '电脑' ? 'automatic' : 'custom';
  if (name === null) value.nameSource = 'automatic';
  if (value.nameSource === 'automatic') value.name = defaultComputerName();
  if (name !== undefined && name !== null) {
    value.name = deviceName(name);
    value.nameSource = 'custom';
  }
  if (!exists || previous !== JSON.stringify(value)) {
    mkdirSync(directory, { recursive: true, mode: 0o700 });
    writeFileSync(file + '.tmp', JSON.stringify(value), { mode: 0o600 });
    renameSync(file + '.tmp', file);
  }
  return { id: value.id, name: value.name };
}
