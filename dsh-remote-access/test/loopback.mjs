import { once } from 'node:events';

// Windows may allocate ephemeral ports that Fetch blocks. Keep synthetic services
// on loopback, retrying only those reserved application ports above 1023.
const blocked = new Set([1719, 1720, 1723, 2049, 3659, 4045, 4190, 5060, 5061, 6000, 6566,
  6665, 6666, 6667, 6668, 6669, 6679, 6697, 10080]);
export async function listenLoopback(server) {
  for (let attempt = 0; attempt < 20; attempt++) {
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    if (server.address().port > 1023 && !blocked.has(server.address().port)) return;
    await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  }
  throw new Error('Could not allocate a Fetch-compatible loopback port');
}
