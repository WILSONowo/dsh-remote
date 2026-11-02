import { spawn } from 'node:child_process';
export declare function createWakeLock(options?: {
    platform?: string;
    launch?: typeof spawn;
}): {
    setEnabled: (next: boolean) => void;
    status: () => {
        supported: boolean;
        state: string;
    };
    dispose: () => void;
};
