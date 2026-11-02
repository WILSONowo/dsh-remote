export declare function deviceName(name: unknown): string;
/** Installation identity is random; a display-name change never changes identity. */
export declare const defaultComputerName: () => string;
export declare function computerIdentity(home: string, name?: string | null): {
    id: string;
    name: string;
};
