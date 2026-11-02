import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import QRCode from 'qrcode';
import { encodePairingLink } from '../lib/pairing-link.js';
import { decodePairingTestLink } from './pairing-fixture.mjs';

const fixture = JSON.parse(readFileSync(new URL('../../app/src/test/resources/pairing-compact.json', import.meta.url), 'utf8'));

test('compact QR matches the Android vector without shortening ticket or identity', () => {
  const link = encodePairingLink(fixture.payload);
  assert.equal(link, fixture.link);
  assert.deepEqual(decodePairingTestLink(link), fixture.payload);
  const legacy = 'dshremote://pair#' + Buffer.from(JSON.stringify(fixture.payload)).toString('base64url');
  assert.ok(link.length < legacy.length * 0.6);
  assert.ok(QRCode.create(link, { errorCorrectionLevel: 'M' }).modules.size < QRCode.create(legacy, { errorCorrectionLevel: 'M' }).modules.size);
});

test('compact encoder refuses malformed identities, overlong fields and invalid expiry', () => {
  for (const invalid of [{ ticket: 'short' }, { hostId: '../host' }, { computerId: 'hardware' }, { expiresAt: NaN },
    { expiresAt: -1 }, { server: 'x'.repeat(2049) }, { name: '\nname' }, { name: 'x'.repeat(65) }]) {
    assert.throws(() => encodePairingLink({ ...fixture.payload, ...invalid }), /invalid_pairing_payload/);
  }
});
