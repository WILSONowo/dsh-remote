import { randomUUID } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

/** Installation identity is random; a display-name change never changes identity. */
export function computerIdentity(home: string, name?: string): { id: string; name: string } {
  const directory = join(home, 'remote-access'), file = join(directory, 'identity.json');
  const exists = existsSync(file);
  const value = exists ? JSON.parse(readFileSync(file, 'utf8')) : { id: randomUUID(), name: '电脑' };
  if (!/^[a-f0-9-]{36}$/.test(value.id) || typeof value.name !== 'string') throw new Error('Invalid computer identity');
  if (name !== undefined) {
    if (!name.trim() || name.length > 64 || /[\x00-\x1f\x7f]/.test(name)) throw new Error('电脑名称需为 1–64 个字符');
    value.name = name.trim();
  }
  if (!exists || name !== undefined) {
    mkdirSync(directory, { recursive: true, mode: 0o700 });
    writeFileSync(file + '.tmp', JSON.stringify(value), { mode: 0o600 });
    renameSync(file + '.tmp', file);
  }
  return value;
}
