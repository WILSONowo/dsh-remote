export declare function assistantBaseline(attempt: any): {
    text: string;
    reasoning: string;
    startedAfterSeq: any;
};
export declare function createModernAdapter(ctx: any, register: (route: any) => void): {
    dispose: () => Promise<void>;
};
