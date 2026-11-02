/** Installation identity is random; a display-name change never changes identity. */
export declare function computerIdentity(home: string, name?: string): {
    id: string;
    name: string;
};
