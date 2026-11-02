import { spawn, type ChildProcess } from 'node:child_process';
import { join } from 'node:path';

// Thread-scoped request: EOF or process exit releases it. No power plan changes.
const windowsScript = `
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Threading;
public static class DshRemotePower {
  [StructLayout(LayoutKind.Sequential)] struct PowerStatus {
    public byte ACLineStatus, BatteryFlag, BatteryLifePercent, SystemStatusFlag;
    public UInt32 BatteryLifeTime, BatteryFullLifeTime;
  }
  [DllImport("kernel32.dll")] static extern bool GetSystemPowerStatus(out PowerStatus status);
  [DllImport("kernel32.dll")] static extern UInt32 SetThreadExecutionState(UInt32 flags);
  public static void Run() {
    var input = System.Threading.Tasks.Task.Run(() => Console.ReadLine());
    string previous = "";
    try {
      while (!input.IsCompleted) {
        PowerStatus power;
        if (!GetSystemPowerStatus(out power)) throw new Exception("power_status_failed");
        bool pluggedIn = power.ACLineStatus == 1;
        if (SetThreadExecutionState(pluggedIn ? 0x80000001u : 0x80000000u) == 0) throw new Exception("power_request_failed");
        string state = pluggedIn ? "active" : "battery";
        if (state != previous) { Console.WriteLine(state); Console.Out.Flush(); previous = state; }
        Thread.Sleep(1000);
      }
    } finally { SetThreadExecutionState(0x80000000u); }
  }
}
'@
[DshRemotePower]::Run()
`;

export function createWakeLock(options: { platform?: string; launch?: typeof spawn } = {}) {
  const platform = options.platform || process.platform, launch = options.launch || spawn;
  const supported = platform === 'win32';
  let child: ChildProcess | undefined;
  let state = supported ? 'off' : 'unsupported', enabled = false;
  let startup: NodeJS.Timeout | undefined;
  const stop = () => {
    clearTimeout(startup); startup = undefined;
    const previous = child; child = undefined;
    previous?.stdin?.end(); previous?.kill();
    state = supported ? 'off' : 'unsupported';
  };
  const setEnabled = (next: boolean) => {
    if (next === enabled && (child || !next)) return;
    enabled = next; stop();
    if (!next || !supported) return;
    state = 'starting';
    try {
      const executable = join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
      const current = launch(executable, ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(windowsScript, 'utf16le').toString('base64')], { windowsHide: true, stdio: ['pipe', 'pipe', 'ignore'] });
      child = current;
      const failed = () => { if (child === current) { stop(); state = 'error'; } };
      current.on('error', failed); current.on('exit', failed);
      current.stdin?.on('error', () => {});
      let buffer = '';
      current.stdout?.on('data', chunk => {
        if (child !== current) return;
        buffer += chunk.toString();
        const lines = buffer.split(/\r?\n/); buffer = lines.pop()!;
        for (const line of lines) if (line === 'active' || line === 'battery') { state = line; clearTimeout(startup); }
      });
      startup = setTimeout(failed, 15000); startup.unref();
      current.unref();
      (current.stdin as any)?.unref?.(); (current.stdout as any)?.unref?.();
    } catch { stop(); state = 'error'; }
  };
  return { setEnabled, status: () => ({ supported, state }), dispose: () => { enabled = false; stop(); } };
}
