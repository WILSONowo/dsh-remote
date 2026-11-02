import Bonjour from 'bonjour-service';
import { networkInterfaces } from 'node:os';

export type DiscoveryAdvertisement = { session: string; name: string; port: number; tls: boolean };
export type DiscoveryPublisher = (value: DiscoveryAdvertisement, failed: () => void) => () => void;

/** Open sockets only during an explicitly started pairing window. No credentials in TXT. */
export const publishDiscovery: DiscoveryPublisher = (value, failed) => {
  let closed = false;
  const bonjour = new Bonjour(undefined, () => { if (!closed) failed(); });
  const ipv4 = [...new Set(Object.values(networkInterfaces()).flatMap(list => (list || [])
    .filter(address => !address.internal && address.family === 'IPv4').map(address => address.address)))].slice(0, 8).join(',');
  let service: Bonjour.Service;
  try { service = bonjour.publish({
    name: 'DSH-' + value.session, type: 'dsh-pair', protocol: 'tcp', port: value.port,
    host: 'dsh-' + value.session + '.local',
    txt: { v: '1', session: value.session, name: value.name, tls: value.tls ? '1' : '0', ipv4 },
  }); } catch (error) { bonjour.destroy(); throw error; }
  service.on('error', () => { if (!closed) failed(); });
  return () => {
    if (closed) return;
    closed = true;
    // Send goodbye records before closing the socket; bound cleanup if sending fails.
    let destroyed = false;
    const destroy = () => { if (!destroyed) { destroyed = true; bonjour.destroy(); } };
    const timer = setTimeout(destroy, 1000);
    timer.unref();
    try { bonjour.unpublishAll(() => { clearTimeout(timer); destroy(); }); }
    catch { clearTimeout(timer); destroy(); }
  };
};
