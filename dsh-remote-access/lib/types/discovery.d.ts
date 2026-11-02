export type DiscoveryAdvertisement = {
    session: string;
    name: string;
    port: number;
    tls: boolean;
};
export type DiscoveryPublisher = (value: DiscoveryAdvertisement, failed: () => void) => () => void;
/** Open sockets only during an explicitly started pairing window. No credentials in TXT. */
export declare const publishDiscovery: DiscoveryPublisher;
