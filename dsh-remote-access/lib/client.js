(() => {
  // src/client-copy.ts
  var en = {
    connections: "Connections",
    paused: "Connections paused",
    allowConnections: "Allow connections",
    refresh: "Refresh devices",
    otherSettings: "Other settings",
    keepAwake: "Keep this PC awake",
    awakeHint: "Prevent sleep when this computer is plugged in and remote access is enabled.",
    awakeUnsupported: "Not available on this operating system.",
    awakeBattery: "Paused while running on battery.",
    awakeStarting: "Starting\u2026",
    awakeFailed: "Could not keep this computer awake. Turn this option off and try again.",
    manage: "Manage",
    addDevice: "Add a device",
    serverConnection: "Server connection",
    serverChoice: "Scan or paste temporary connection info.",
    directChoice: "Connect using this computer\u2019s address.",
    reset: "Reset",
    directQr: "Connection QR code",
    directUrl: "Computer address",
    directQrHint: "Scan to fill in the address, then pair using a code or computer approval.",
    codeDirect: "Pairing code connection",
    codeDirectChoice: "Discover this computer on the local network. No IP needed.",
    discoveryInstructions: "On your device, choose Add computer \u2192 Pairing code connection, select this computer, then enter the code.",
    discoveryMarker: "Identifier: {id}",
    discoveryActive: "Visible on the local network while this page is open.",
    discoveryFailed: "Could not advertise on the local network. Check the firewall, or use IP connection with this code.",
    discoveryEnded: "This pairing window has ended. Generate a new code to add another device.",
    copyAddress: "Copy address",
    chooseAddress: "Choose a computer address",
    serverApprovalInstructions: "After scanning, approve the device\u2019s connection request on this computer.",
    back: "Back",
    computerApproval: "Computer approval",
    directAddressHint: "Scan the QR code or enter this address on your device. The address must be reachable.",
    noAddress: "No network address found. Enter the address of this computer\u2019s DeepSeek Harness service on your device.",
    approvalInstructions: "On your device, select \u201CWait for computer confirmation\u201D, then approve the request here.",
    renameComputer: "Rename this computer",
    renameNamed: "Rename {name}",
    neverConnected: "No connection recorded",
    justConnected: "Last connected just now",
    lastConnected: "Last connected {time}",
    minutesAgo: "{count}m ago",
    hoursAgo: "{count}h ago",
    daysAgo: "{count}d ago",
    nav: "Remote control",
    intro: "Connect your device to this computer.",
    server: "Server",
    serverHint: "Connect through your own server.",
    enable: "Enable online service",
    address: "Server address",
    ca: "Custom CA certificate path (optional)",
    caHint: "Optional for a private certificate authority.",
    save: "Save",
    saving: "Saving\u2026",
    saved: "Saved",
    disabled: "Off",
    registering: "Registering",
    connecting: "Connecting",
    connected: "Connected",
    reconnecting: "Reconnecting",
    error: "Connection failed",
    loading: "Loading\u2026",
    retry: "Retry",
    close: "Close",
    cancel: "Cancel",
    qrTitle: "Add a device",
    qrHint: "Scan with DSH Remote.",
    qrAlt: "Device pairing QR code",
    qrExpiry: "Expires in {time}",
    qrExpired: "Connection information expired. Generate a new code.",
    regenerate: "Generate again",
    copyLink: "Copy connection info",
    copied: "Copied",
    copyFailed: "Copy failed. Try again.",
    connectionInfo: "Connection info",
    pasteHint: "On your device, choose \u201CPaste connection info\u201D.",
    direct: "IP connection",
    directHint: "Enter this code on a device connected directly to this computer.",
    code: "Pairing code",
    codeRules: "Single use \xB7 10 minutes \xB7 5 attempts",
    expires: "Expires in {time}",
    generate: "Generate code",
    generating: "Generating\u2026",
    devices: "Devices that can control this PC",
    devicesHint: "Each device has its own access. Revoking it disconnects that device.",
    thisComputer: "This computer",
    rename: "Rename",
    renameDevice: "Rename device",
    deviceName: "Device name",
    invalidName: "Enter a name of 1\u201364 characters without control characters.",
    noDevices: "No paired devices",
    revoke: "Revoke access",
    revoking: "Revoking\u2026",
    approveTitle: "Pair \u201C{name}\u201D?",
    approveHint: "Allow this device to control this computer?",
    allow: "Allow",
    deny: "Deny",
    responding: "Responding\u2026",
    hosts: "Trusted domains",
    hostsHint: "For direct connections through a public tunnel. Server connections do not need this.",
    hostFormat: "Domain or domain:port, without https://. Use Punycode for international domains.",
    domain: "Domain",
    noHosts: "No trusted domains",
    add: "Add",
    remove: "Remove",
    working: "Working\u2026",
    loadFailed: "Could not load settings. Check the computer connection and try again.",
    accessRestart: "Connection settings are unavailable. After updating the plugin, fully quit DeepSeek Harness (including the tray icon) and reopen it. If this continues, reinstall the complete plugin package.",
    accessDenied: "Connection settings can only be changed on this computer. Reopen its DeepSeek Harness settings.",
    accessLoadFailed: "Could not load connection and wake settings. Check the connection to this computer and retry.",
    accessSaveFailed: "Could not save connection and wake settings. Check this computer\u2019s plugin data directory permissions, then try again.",
    requestFailed: "Request failed. Try again.",
    saveFailed: "Could not save. Check the server address, certificate and file permissions.",
    invalidDomain: "Enter a valid domain or domain:port, without a URL scheme.",
    connectFirst: "Save your settings and wait for the server to connect.",
    legacyConfig: "Set the server address again, then pair your device with a new QR code.",
    certificateFailed: "Check the server address, network and TLS certificate.",
    caFailed: "Could not read the CA certificate. Check its path and permissions.",
    registrationDisabled: "Computer registration is disabled. Contact the server administrator.",
    serverUpdate: "Update the server to support automatic registration.",
    rateLimited: "Too many registration requests. Try again later.",
    unavailable: "The server is unavailable or has reached its computer limit.",
    identityMismatch: "The server returned a different computer identity. Check the server configuration.",
    duplicate: "This computer could not connect. Check its registration and close any duplicate plugin instance."
  };
  var zh = {
    connections: "\u8FDE\u63A5",
    paused: "\u8FDE\u63A5\u5DF2\u6682\u505C",
    allowConnections: "\u5141\u8BB8\u8FDE\u63A5",
    refresh: "\u5237\u65B0\u8BBE\u5907",
    otherSettings: "\u5176\u4ED6\u8BBE\u7F6E",
    keepAwake: "\u4FDD\u6301\u7535\u8111\u5524\u9192",
    awakeHint: "\u7535\u8111\u63A5\u901A\u7535\u6E90\u4E14\u5141\u8BB8\u8FDC\u7A0B\u8FDE\u63A5\u65F6\uFF0C\u9632\u6B62\u81EA\u52A8\u7761\u7720\u3002",
    awakeUnsupported: "\u5F53\u524D\u64CD\u4F5C\u7CFB\u7EDF\u6682\u4E0D\u652F\u6301\u3002",
    awakeBattery: "\u4F7F\u7528\u7535\u6C60\u65F6\u6682\u505C\u3002",
    awakeStarting: "\u6B63\u5728\u542F\u52A8\u2026",
    awakeFailed: "\u65E0\u6CD5\u4FDD\u6301\u7535\u8111\u5524\u9192\uFF0C\u8BF7\u5173\u95ED\u6B64\u9009\u9879\u540E\u91CD\u8BD5\u3002",
    manage: "\u7BA1\u7406",
    addDevice: "\u6DFB\u52A0\u8BBE\u5907",
    serverConnection: "\u670D\u52A1\u5668\u8FDE\u63A5",
    serverChoice: "\u626B\u7801\u6216\u7C98\u8D34\u4E34\u65F6\u8FDE\u63A5\u4FE1\u606F\u3002",
    directChoice: "\u4F7F\u7528\u8FD9\u53F0\u7535\u8111\u7684\u5730\u5740\u8FDE\u63A5\u3002",
    reset: "\u91CD\u7F6E",
    directQr: "\u8FDE\u63A5\u4E8C\u7EF4\u7801",
    directUrl: "\u7535\u8111\u5730\u5740",
    directQrHint: "\u626B\u7801\u586B\u5199\u5730\u5740\u540E\uFF0C\u4ECD\u9700\u4F7F\u7528\u914D\u5BF9\u7801\u6216\u7535\u8111\u786E\u8BA4\u5B8C\u6210\u914D\u5BF9\u3002",
    codeDirect: "\u914D\u5BF9\u7801\u76F4\u8FDE",
    codeDirectChoice: "\u5C40\u57DF\u7F51\u81EA\u52A8\u53D1\u73B0\u7535\u8111\uFF0C\u65E0\u9700\u586B\u5199 IP\u3002",
    discoveryInstructions: "\u5728\u8BBE\u5907\u4E0A\u9009\u62E9\u300C\u6DFB\u52A0\u7535\u8111 \u2192 \u914D\u5BF9\u7801\u76F4\u8FDE\u300D\uFF0C\u9009\u62E9\u8FD9\u53F0\u7535\u8111\u540E\u8F93\u5165\u4E0B\u65B9\u914D\u5BF9\u7801\u3002",
    discoveryMarker: "\u8BC6\u522B\u6807\u8BB0\uFF1A{id}",
    discoveryActive: "\u6B64\u9875\u9762\u6253\u5F00\u671F\u95F4\uFF0C\u5C40\u57DF\u7F51\u5185\u7684\u8BBE\u5907\u53EF\u4EE5\u53D1\u73B0\u8FD9\u53F0\u7535\u8111\u3002",
    discoveryFailed: "\u5C40\u57DF\u7F51\u53D1\u73B0\u672A\u80FD\u542F\u52A8\uFF0C\u8BF7\u68C0\u67E5\u9632\u706B\u5899\uFF0C\u6216\u901A\u8FC7 IP \u76F4\u8FDE\u4F7F\u7528\u6B64\u914D\u5BF9\u7801\u3002",
    discoveryEnded: "\u672C\u6B21\u914D\u5BF9\u7A97\u53E3\u5DF2\u7ED3\u675F\u3002\u5982\u9700\u6DFB\u52A0\u53E6\u4E00\u53F0\u8BBE\u5907\uFF0C\u8BF7\u91CD\u65B0\u751F\u6210\u914D\u5BF9\u7801\u3002",
    copyAddress: "\u590D\u5236\u5730\u5740",
    chooseAddress: "\u9009\u62E9\u7535\u8111\u5730\u5740",
    serverApprovalInstructions: "\u626B\u7801\u540E\uFF0C\u8BF7\u5728\u672C\u7535\u8111\u786E\u8BA4\u8BBE\u5907\u7684\u8FDE\u63A5\u8BF7\u6C42\u3002",
    back: "\u8FD4\u56DE",
    computerApproval: "\u7535\u8111\u786E\u8BA4",
    directAddressHint: "\u5728\u8BBE\u5907\u4E0A\u626B\u7801\u6216\u586B\u5199\u5730\u5740\uFF0C\u7136\u540E\u5B8C\u6210\u914D\u5BF9\u3002\u5730\u5740\u9700\u80FD\u4ECE\u8BBE\u5907\u8BBF\u95EE\u3002",
    noAddress: "\u672A\u627E\u5230\u7F51\u7EDC\u5730\u5740\uFF0C\u8BF7\u5728\u8BBE\u5907\u586B\u5199\u8FD9\u53F0\u7535\u8111\u7684 DeepSeek Harness \u670D\u52A1\u5730\u5740\u3002",
    approvalInstructions: "\u8BBE\u5907\u9009\u62E9\u201C\u7B49\u5F85\u7535\u8111\u786E\u8BA4\u201D\uFF0C\u968F\u540E\u5728\u6B64\u786E\u8BA4\u914D\u5BF9\u8BF7\u6C42\u3002",
    renameComputer: "\u91CD\u547D\u540D\u672C\u7535\u8111",
    renameNamed: "\u91CD\u547D\u540D{name}",
    neverConnected: "\u6682\u65E0\u8FDE\u63A5\u8BB0\u5F55",
    justConnected: "\u6700\u8FD1\u8FDE\u63A5\uFF1A\u521A\u521A",
    lastConnected: "\u6700\u8FD1\u8FDE\u63A5\uFF1A{time}",
    minutesAgo: "{count} \u5206\u949F\u524D",
    hoursAgo: "{count} \u5C0F\u65F6\u524D",
    daysAgo: "{count} \u5929\u524D",
    nav: "\u8FDC\u7A0B\u63A7\u5236",
    intro: "\u7528\u8BBE\u5907\u8FDE\u63A5\u5E76\u63A7\u5236\u8FD9\u53F0\u7535\u8111\u3002",
    server: "\u670D\u52A1\u5668",
    serverHint: "\u901A\u8FC7\u81EA\u5EFA\u670D\u52A1\u5668\u8FDE\u63A5\u3002",
    enable: "\u542F\u7528\u5728\u7EBF\u670D\u52A1",
    address: "\u670D\u52A1\u5668\u5730\u5740",
    ca: "\u81EA\u5B9A\u4E49 CA \u8BC1\u4E66\u8DEF\u5F84\uFF08\u53EF\u9009\uFF09",
    caHint: "\u4F7F\u7528\u79C1\u6709\u8BC1\u4E66\u9881\u53D1\u673A\u6784\u65F6\u586B\u5199\u3002",
    save: "\u4FDD\u5B58",
    saving: "\u4FDD\u5B58\u4E2D\u2026",
    saved: "\u5DF2\u4FDD\u5B58",
    disabled: "\u672A\u542F\u7528",
    registering: "\u6CE8\u518C\u4E2D",
    connecting: "\u8FDE\u63A5\u4E2D",
    connected: "\u5DF2\u8FDE\u63A5",
    reconnecting: "\u6B63\u5728\u91CD\u8FDE",
    error: "\u8FDE\u63A5\u5931\u8D25",
    loading: "\u52A0\u8F7D\u4E2D\u2026",
    retry: "\u91CD\u8BD5",
    close: "\u5173\u95ED",
    cancel: "\u53D6\u6D88",
    qrTitle: "\u6DFB\u52A0\u8BBE\u5907",
    qrHint: "\u7528 DSH Remote \u626B\u7801\u3002",
    qrAlt: "\u8BBE\u5907\u914D\u5BF9\u4E8C\u7EF4\u7801",
    qrExpiry: "{time} \u540E\u8FC7\u671F",
    qrExpired: "\u8FDE\u63A5\u4FE1\u606F\u5DF2\u8FC7\u671F\uFF0C\u8BF7\u91CD\u65B0\u751F\u6210\u3002",
    regenerate: "\u91CD\u65B0\u751F\u6210",
    copyLink: "\u590D\u5236\u8FDE\u63A5\u4FE1\u606F",
    copied: "\u5DF2\u590D\u5236",
    copyFailed: "\u590D\u5236\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5\u3002",
    connectionInfo: "\u8FDE\u63A5\u4FE1\u606F",
    pasteHint: "\u5728\u8BBE\u5907\u4E0A\u9009\u62E9\u201C\u7C98\u8D34\u8FDE\u63A5\u4FE1\u606F\u201D\u3002",
    direct: "IP \u76F4\u8FDE",
    directHint: "\u8BBE\u5907\u76F4\u8FDE\u8FD9\u53F0\u7535\u8111\u540E\uFF0C\u8F93\u5165\u4E0B\u65B9\u914D\u5BF9\u7801\u3002",
    code: "\u914D\u5BF9\u7801",
    codeRules: "\u4E00\u6B21\u6709\u6548 \xB7 10 \u5206\u949F \xB7 \u6700\u591A\u5C1D\u8BD5 5 \u6B21",
    expires: "{time} \u540E\u8FC7\u671F",
    generate: "\u751F\u6210\u914D\u5BF9\u7801",
    generating: "\u751F\u6210\u4E2D\u2026",
    devices: "\u53EF\u63A7\u5236\u672C\u7535\u8111\u7684\u8BBE\u5907",
    devicesHint: "\u6BCF\u53F0\u8BBE\u5907\u72EC\u7ACB\u6388\u6743\uFF0C\u64A4\u9500\u540E\u7ACB\u5373\u65AD\u5F00\u8BE5\u8BBE\u5907\u3002",
    thisComputer: "\u672C\u7535\u8111",
    rename: "\u91CD\u547D\u540D",
    renameDevice: "\u91CD\u547D\u540D\u8BBE\u5907",
    deviceName: "\u8BBE\u5907\u540D\u79F0",
    invalidName: "\u540D\u79F0\u9700\u4E3A 1\u201364 \u4E2A\u5B57\u7B26\uFF0C\u4E0D\u80FD\u5305\u542B\u63A7\u5236\u5B57\u7B26\u3002",
    noDevices: "\u6682\u65E0\u5DF2\u914D\u5BF9\u8BBE\u5907",
    revoke: "\u64A4\u9500\u8BBF\u95EE\u6743\u9650",
    revoking: "\u64A4\u9500\u4E2D\u2026",
    approveTitle: "\u5141\u8BB8\u201C{name}\u201D\u914D\u5BF9\uFF1F",
    approveHint: "\u5141\u8BB8\u8FD9\u53F0\u8BBE\u5907\u8FDC\u7A0B\u63A7\u5236\u672C\u7535\u8111\uFF1F",
    allow: "\u5141\u8BB8",
    deny: "\u62D2\u7EDD",
    responding: "\u5904\u7406\u4E2D\u2026",
    hosts: "\u53EF\u4FE1\u57DF\u540D",
    hostsHint: "\u4EC5\u7528\u4E8E\u901A\u8FC7\u516C\u7F51\u96A7\u9053\u76F4\u8FDE\uFF0C\u670D\u52A1\u5668\u8FDE\u63A5\u65E0\u9700\u8BBE\u7F6E\u3002",
    hostFormat: "\u586B\u5199\u57DF\u540D\u6216\u57DF\u540D:\u7AEF\u53E3\uFF0C\u4E0D\u5E26 https://\uFF1B\u4E2D\u6587\u57DF\u540D\u8BF7\u8F6C\u4E3A Punycode\u3002",
    domain: "\u57DF\u540D",
    noHosts: "\u6682\u65E0\u53EF\u4FE1\u57DF\u540D",
    add: "\u6DFB\u52A0",
    remove: "\u5220\u9664",
    working: "\u5904\u7406\u4E2D\u2026",
    loadFailed: "\u52A0\u8F7D\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u7535\u8111\u8FDE\u63A5\u540E\u91CD\u8BD5\u3002",
    requestFailed: "\u8BF7\u6C42\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5\u3002",
    accessRestart: "\u8FDE\u63A5\u8BBE\u7F6E\u63A5\u53E3\u4E0D\u53EF\u7528\u3002\u66F4\u65B0\u63D2\u4EF6\u540E\uFF0C\u8BF7\u5B8C\u6574\u9000\u51FA DeepSeek Harness\uFF08\u5305\u62EC\u6258\u76D8\u540E\u53F0\uFF09\u518D\u542F\u52A8\uFF1B\u82E5\u4ECD\u5931\u8D25\uFF0C\u8BF7\u91CD\u65B0\u5B89\u88C5\u5B8C\u6574\u63D2\u4EF6\u5305\u3002",
    accessDenied: "\u8FDE\u63A5\u8BBE\u7F6E\u4EC5\u5141\u8BB8\u5728\u672C\u7535\u8111\u64CD\u4F5C\uFF0C\u8BF7\u91CD\u65B0\u6253\u5F00\u8FD9\u53F0\u7535\u8111\u7684 DeepSeek Harness \u8BBE\u7F6E\u3002",
    accessLoadFailed: "\u65E0\u6CD5\u52A0\u8F7D\u5141\u8BB8\u8FDE\u63A5\u548C\u5524\u9192\u8BBE\u7F6E\uFF0C\u8BF7\u68C0\u67E5\u4E0E\u672C\u7535\u8111\u7684\u8FDE\u63A5\u540E\u91CD\u8BD5\u3002",
    accessSaveFailed: "\u65E0\u6CD5\u4FDD\u5B58\u5141\u8BB8\u8FDE\u63A5\u548C\u5524\u9192\u8BBE\u7F6E\uFF0C\u8BF7\u68C0\u67E5\u672C\u7535\u8111\u63D2\u4EF6\u6570\u636E\u76EE\u5F55\u7684\u5199\u5165\u6743\u9650\u540E\u91CD\u8BD5\u3002",
    saveFailed: "\u4FDD\u5B58\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u670D\u52A1\u5668\u5730\u5740\u3001\u8BC1\u4E66\u548C\u6587\u4EF6\u6743\u9650\u3002",
    invalidDomain: "\u8BF7\u586B\u5199\u6709\u6548\u7684\u57DF\u540D\u6216\u57DF\u540D:\u7AEF\u53E3\uFF0C\u4E0D\u5E26\u534F\u8BAE\u524D\u7F00\u3002",
    connectFirst: "\u8BF7\u4FDD\u5B58\u8BBE\u7F6E\u5E76\u7B49\u5F85\u670D\u52A1\u5668\u8FDE\u63A5\u6210\u529F\u3002",
    legacyConfig: "\u8BF7\u91CD\u65B0\u586B\u5199\u670D\u52A1\u5668\u5730\u5740\uFF0C\u518D\u8BA9\u8BBE\u5907\u626B\u63CF\u65B0\u4E8C\u7EF4\u7801\u914D\u5BF9\u3002",
    certificateFailed: "\u8BF7\u68C0\u67E5\u670D\u52A1\u5668\u5730\u5740\u3001\u7F51\u7EDC\u548C TLS \u8BC1\u4E66\u3002",
    caFailed: "\u65E0\u6CD5\u8BFB\u53D6 CA \u8BC1\u4E66\uFF0C\u8BF7\u68C0\u67E5\u8DEF\u5F84\u548C\u6743\u9650\u3002",
    registrationDisabled: "\u670D\u52A1\u5668\u672A\u5F00\u653E\u7535\u8111\u6CE8\u518C\uFF0C\u8BF7\u8054\u7CFB\u7EF4\u62A4\u8005\u3002",
    serverUpdate: "\u8BF7\u66F4\u65B0\u670D\u52A1\u5668\u540E\u7AEF\u4EE5\u652F\u6301\u81EA\u52A8\u6CE8\u518C\u3002",
    rateLimited: "\u6CE8\u518C\u8BF7\u6C42\u8FC7\u4E8E\u9891\u7E41\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5\u3002",
    unavailable: "\u670D\u52A1\u5668\u6682\u4E0D\u53EF\u7528\u6216\u5DF2\u8FBE\u5230\u7535\u8111\u6570\u91CF\u4E0A\u9650\u3002",
    identityMismatch: "\u670D\u52A1\u5668\u8FD4\u56DE\u7684\u7535\u8111\u8EAB\u4EFD\u4E0D\u4E00\u81F4\uFF0C\u8BF7\u68C0\u67E5\u670D\u52A1\u5668\u914D\u7F6E\u3002",
    duplicate: "\u7535\u8111\u8FDE\u63A5\u88AB\u62D2\u7EDD\uFF0C\u8BF7\u68C0\u67E5\u767B\u8BB0\u72B6\u6001\u6216\u662F\u5426\u91CD\u590D\u8FD0\u884C\u63D2\u4EF6\u3002"
  };
  function detailKey(detail) {
    const value = String(detail || "");
    if (/旧版|手动方案|配置已失效/.test(value)) return "legacyConfig";
    if (/无法读取 CA/.test(value)) return "caFailed";
    if (/未启用自动注册/.test(value)) return "registrationDisabled";
    if (/版本不支持/.test(value)) return "serverUpdate";
    if (/过于频繁/.test(value)) return "rateLimited";
    if (/容量上限|暂不可用/.test(value)) return "unavailable";
    if (/身份不一致/.test(value)) return "identityMismatch";
    if (/连接被拒绝/.test(value)) return "duplicate";
    return "certificateFailed";
  }

  // src/client-style.ts
  var settingsCss = `
.dsh-remote { color:var(--dsw-alias-label-primary); font-family:inherit; font-size:14px; line-height:22px; min-width:0; }
.dsh-remote h2,.dsh-remote h3,.dsh-remote p { margin:0; }
.dsh-remote h2 { font-size:28px; font-weight:600; line-height:36px; }
.dsh-remote h3 { font-size:16px; font-weight:500; line-height:24px; }
.dsh-remote .remote-intro { padding:0 0 12px; }
.dsh-remote .remote-section { padding:24px 0 16px; }
.dsh-remote .remote-head,.dsh-remote .remote-row { display:flex; align-items:center; justify-content:space-between; gap:24px; }
.dsh-remote .remote-head { margin-bottom:16px; }
.dsh-remote .remote-head:last-child { margin-bottom:0; }
.dsh-remote .remote-muted { color:var(--dsw-alias-label-secondary); font-size:13px; line-height:19px; margin-top:4px; }
.dsh-remote .remote-form { display:grid; gap:16px; }
.dsh-remote .remote-field { display:grid; grid-template-columns:minmax(110px,1fr) minmax(0,2fr); align-items:center; gap:20px; }
.dsh-remote .remote-input { box-sizing:border-box; width:100%; min-width:0; border:1px solid var(--dsw-alias-border-l2); border-radius:var(--dsw-radius-md); background:var(--dsw-alias-bg-layer-1); color:var(--dsw-alias-label-primary); padding:8px 12px; font:inherit; font-size:13px; line-height:20px; }
.dsh-remote .remote-input:hover { border-color:var(--dsw-alias-border-l1); }
.dsh-remote .remote-input:focus-visible,.dsh-remote summary:focus-visible { outline:2px solid var(--dsw-alias-brand-primary); outline-offset:2px; }
.dsh-remote .remote-input:disabled { opacity:.5; }
.dsh-remote .remote-input::placeholder { color:var(--dsw-alias-label-tertiary); }
.dsh-remote .remote-actions { display:flex; flex-shrink:0; flex-wrap:wrap; align-items:center; justify-content:flex-end; gap:8px; }
.dsh-remote .remote-status { display:inline-flex; align-items:center; gap:6px; font-size:12px; color:var(--dsw-alias-label-secondary); white-space:nowrap; }
.dsh-remote .remote-status::before { content:''; width:6px; height:6px; border-radius:50%; background:currentColor; }
.dsh-remote .remote-status[data-state=connected] { color:var(--dsw-alias-state-success-primary); }
.dsh-remote .remote-status[data-state=error],.dsh-remote .remote-error { color:var(--dsw-alias-state-error-primary); }
.dsh-remote .remote-error { font-size:12px; line-height:18px; margin-top:8px; }
.dsh-remote .remote-list { margin:12px 0 0; padding:0; list-style:none; }
.dsh-remote .remote-list li { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:10px 0; border-bottom:.5px solid var(--dsw-alias-border-l2); }
.dsh-remote .remote-list li:last-child { border-bottom:0; }
.dsh-remote .remote-name { min-width:0; overflow-wrap:anywhere; }
.dsh-remote .remote-code { font:500 28px/40px ui-monospace,monospace; letter-spacing:.22em; font-variant-numeric:tabular-nums; }
.dsh-remote .remote-code-row { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-top:12px; }
.dsh-remote .remote-empty { padding:8px 0; color:var(--dsw-alias-label-secondary); font-size:13px; }
.dsh-remote details>summary { cursor:pointer; width:fit-content; font-size:13px; color:var(--dsw-alias-label-secondary); }
.dsh-remote details[open]>summary { margin-bottom:14px; }
.dsh-remote .remote-host-form { display:flex; gap:8px; margin-top:12px; }
.dsh-remote .remote-host-form .remote-input { flex:1; }
.dsh-remote .remote-qr { display:block; width:224px; max-width:100%; height:auto; image-rendering:pixelated; margin:12px auto; border-radius:var(--dsw-radius-md); }
.dsh-remote .remote-qr-meta { text-align:center; margin:8px 0 16px; }
.dsh-remote .remote-link { resize:vertical; min-height:64px; max-height:160px; font:12px/18px ui-monospace,monospace; overflow-wrap:anywhere; }
.dsh-remote .remote-modal-copy { overflow-wrap:anywhere; }
.dsh-remote .remote-card { border:1px solid var(--dsw-alias-border-l2); border-radius:20px; padding:4px 20px; }
.dsh-remote .remote-card.remote-form { padding:20px; }
.dsh-remote .remote-device-row { display:flex; justify-content:space-between; align-items:center; gap:16px; padding:18px 0; }
.dsh-remote .remote-device-row + .remote-device-row { border-top:1px solid var(--dsw-alias-border-l2); }
.dsh-remote .remote-device-info { display:flex; align-items:center; gap:14px; min-width:0; }
.dsh-remote .remote-device-info strong { font-size:14px; font-weight:500; }
.dsh-remote .remote-device-info .remote-muted { margin-top:2px; }
.dsh-remote .remote-icon { width:20px; height:20px; flex-shrink:0; }
.dsh-remote .remote-computer { display:flex; align-items:center; gap:8px; margin:-8px 0 12px; font-size:12px; color:var(--dsw-alias-label-secondary); overflow-wrap:anywhere; }
.dsh-remote .remote-computer .remote-icon { width:14px; height:14px; }
.dsh-remote .remote-head button,.dsh-remote .remote-device-row button { border-radius:999px; }
.dsh-remote button.remote-pill { background:var(--dsw-alias-interactive-bg-hover); border-radius:999px; }
.dsh-remote .remote-choices { display:grid; gap:12px; }
.dsh-remote button.remote-choice { display:flex; flex-direction:column; align-items:flex-start; height:auto; white-space:normal; gap:8px; padding:20px; border-radius:16px; text-align:left; }
.dsh-remote .remote-choice .remote-muted { text-align:left; }
.dsh-remote .remote-methods { justify-content:flex-start; margin:20px 0; }
.dsh-remote .remote-addresses { list-style:none; padding:0; margin:12px 0; overflow-wrap:anywhere; }
.dsh-remote .remote-addresses li { padding:8px 12px; margin-top:8px; border-radius:10px; background:var(--dsw-alias-bg-layer-1); }
.dsh-remote .remote-hosts-modal .remote-section { padding:0; }
.dsh-remote .remote-hosts-modal .remote-head { display:none; }
.dsh-remote .remote-empty { padding:18px 0; }
.dsh-remote .remote-add-body { max-height:calc(100dvh - 280px); overflow:auto; padding-right:4px; }
.dsh-remote .remote-choice-title,.dsh-remote .remote-discovery-computer { display:flex; align-items:center; gap:10px; }
.dsh-remote .remote-discovery-computer { margin:24px 0 16px; flex-wrap:wrap; }
.dsh-remote .remote-address-label { display:block; margin:16px 0 6px; }
.dsh-remote button.remote-choice { padding:14px 16px; gap:4px; }
.dsh-remote .remote-add-footer { width:100%; min-width:0; }
.dsh-remote .remote-footer-hint { text-align:left; margin:0 0 12px; }
.dsh-remote .remote-footer-row { display:flex; align-items:center; gap:8px; width:100%; }
.dsh-remote .remote-footer-copy { margin-right:auto; }
.dsh-remote .remote-footer-actions { margin-left:auto; flex-wrap:nowrap; }
.dsh-remote .remote-address-picker { position:relative; margin-bottom:16px; }
.dsh-remote .remote-address-control { display:flex; align-items:center; border:1px solid var(--dsw-alias-border-l2); border-radius:10px; background:var(--dsw-alias-bg-layer-1); min-height:40px; }
.dsh-remote .remote-address-control:focus-within { border-color:var(--dsw-alias-label-secondary); box-shadow:0 0 0 2px var(--dsw-alias-interactive-bg-hover); }
.dsh-remote .remote-address-control input { color:inherit; background:transparent; border:0; outline:none; min-width:0; width:100%; font:inherit; font-size:13px; padding:10px 12px; }
.dsh-remote .remote-address-toggle { display:grid; place-items:center; flex-shrink:0; width:36px; height:32px; margin-right:4px; border:0; border-radius:7px; color:var(--dsw-alias-label-secondary); background:transparent; cursor:pointer; }
.dsh-remote .remote-address-toggle:hover { background:var(--dsw-alias-interactive-bg-hover); }
.dsh-remote .remote-address-toggle:disabled { opacity:.4; cursor:default; }
.dsh-remote .remote-address-menu { position:absolute; z-index:10; top:calc(100% + 6px); left:0; right:0; padding:5px; border:1px solid var(--dsw-alias-border-l2); border-radius:10px; background:var(--dsw-alias-bg-layer-2); box-shadow:0 8px 24px #0004; max-height:150px; overflow:auto; }
.dsh-remote .remote-address-menu button { display:flex; align-items:center; justify-content:space-between; gap:8px; width:100%; padding:9px 10px; border:0; border-radius:6px; text-align:left; background:transparent; color:var(--dsw-alias-label-primary); font:inherit; font-size:13px; cursor:pointer; }
.dsh-remote .remote-address-menu button span:first-child { overflow-wrap:anywhere; min-width:0; }
.dsh-remote .remote-address-menu button[data-active=true],.dsh-remote .remote-address-menu button:hover { background:var(--dsw-alias-interactive-bg-hover); }
@media(max-height:650px) { .dsh-remote .remote-qr { width:180px; } }
@media(max-width:640px) {
 .dsh-remote .remote-field { grid-template-columns:1fr; gap:6px; }
 .dsh-remote .remote-head,.dsh-remote .remote-row { gap:12px; }
 .dsh-remote .remote-section { padding:16px 0; }
 .dsh-remote .remote-card { padding:4px 14px; }
 .dsh-remote .remote-device-row { flex-wrap:wrap; }
 .dsh-remote .remote-device-row>.remote-actions { margin-left:auto; }
}
`;

  // src/client-request.ts
  var RequestError = class extends Error {
    constructor(status, code) {
      super(code);
      this.status = status;
      this.code = code;
    }
  };
  async function request(route, body, signal) {
    const response = await fetch("/api/remote-access/" + route, {
      method: body === void 0 ? "GET" : "POST",
      signal,
      ...body === void 0 ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }
    });
    let value;
    try {
      value = await response.json();
    } catch {
      throw new RequestError(response.status, "invalid_response");
    }
    if (!response.ok || !value?.ok) throw new RequestError(response.status, value?.error || "request_failed");
    if (route === "access/settings" && (typeof value.allowConnections !== "boolean" || typeof value.keepAwake !== "boolean" || typeof value.wake?.supported !== "boolean" || typeof value.wake?.state !== "string")) {
      throw new RequestError(response.status, "invalid_response");
    }
    return value;
  }
  function accessErrorKey(error, saving = false) {
    if (error instanceof RequestError) {
      if ([404, 405].includes(error.status) || error.status === 200 && error.code === "invalid_response") return "accessRestart";
      if ([401, 403].includes(error.status)) return "accessDenied";
      if (error.code === "wake_unsupported") return "awakeUnsupported";
    }
    return saving ? "accessSaveFailed" : "accessLoadFailed";
  }

  // src/client.ts
  window.__ModuleLoader__.load({
    id: "dsh-remote-access",
    factory(require2) {
      const React = require2("react");
      const { createElement: h, useState, useEffect, useRef } = React;
      const { Button, Switch, Modal } = require2("@deepseek-ai/dsh-client-ui-primitives");
      const NS = "dsh-remote-access";
      const time = (seconds) => `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
      const button = (text, props = {}) => h(Button, { size: "sm", variant: "outline", ...props }, text);
      const muted = (text) => h("p", { className: "remote-muted" }, text);
      const failure = (text) => text ? h("p", { className: "remote-error", role: "alert" }, text) : null;
      function Icon({ name }) {
        const paths = {
          computer: "M4 4h16v12H4z M2 19h20 M8 16v3 M16 16v3",
          device: "M8 2h8v20H8z M11 18h2",
          server: "M3 4h18v6H3z M3 14h18v6H3z M6 7h1 M6 17h1",
          refresh: "M20 7a8 8 0 0 0-14-2L3 8 M3 3v5h5 M4 17a8 8 0 0 0 14 2l3-3 M21 21v-5h-5",
          edit: "m4 16 12-12 4 4-12 12H4z M14 6l4 4",
          sun: "M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l2 2 M17 17l2 2 M5 19l2-2 M17 7l2-2 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
          globe: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M3 12h18 M12 3c-5 5-5 13 0 18 M12 3c5 5 5 13 0 18"
        };
        return h("svg", { className: "remote-icon", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.5, strokeLinecap: "round", strokeLinejoin: "round", "aria-hidden": true }, h("path", { d: paths[name] }));
      }
      function lastConnected(t, at) {
        if (!at) return t("neverConnected");
        const minutes = Math.max(0, Math.floor((Date.now() - at) / 6e4));
        if (!minutes) return t("justConnected");
        const key = minutes < 60 ? "minutesAgo" : minutes < 1440 ? "hoursAgo" : "daysAgo";
        return t("lastConnected", { time: t(key, { count: minutes < 60 ? minutes : minutes < 1440 ? Math.floor(minutes / 60) : Math.floor(minutes / 1440) }) });
      }
      function Section({ title, hint, action, children }) {
        return h(
          "section",
          { className: "remote-section" },
          h("div", { className: "remote-head" }, h("div", {}, h("h3", {}, title), hint && muted(hint)), action),
          children
        );
      }
      function Field({ title, name, value, onChange, disabled, placeholder, hint }) {
        const id = "dsh-remote-" + name;
        return h(
          "div",
          { className: "remote-field" },
          h("label", { htmlFor: id }, title),
          h("div", {}, h("input", {
            id,
            className: "remote-input",
            value: value || "",
            disabled,
            placeholder,
            autoComplete: "off",
            spellCheck: false,
            onChange: (e) => onChange(e.target.value)
          }), hint && muted(hint))
        );
      }
      function RelaySection({ t, onState, allowConnections }) {
        const [config, setConfig] = useState(null);
        const [live, setLive] = useState(null);
        const [loadFailed, setLoadFailed] = useState(false);
        const [busy, setBusy] = useState(false);
        const [error, setError] = useState("");
        const [saved, setSaved] = useState(false);
        const mounted = useRef(false);
        const loading = useRef(false);
        async function refresh(initial = false) {
          if (loading.current) return;
          loading.current = true;
          try {
            const r = await request("relay/config");
            if (!mounted.current) return;
            setConfig((c) => initial || !c ? r : c);
            setLive(r);
            onState(r);
            setLoadFailed(false);
          } catch {
            if (mounted.current) {
              setLoadFailed(true);
              onState(null);
            }
          } finally {
            loading.current = false;
          }
        }
        useEffect(() => {
          mounted.current = true;
          refresh(true);
          const timer = setInterval(() => refresh(), 3e3);
          return () => {
            mounted.current = false;
            clearInterval(timer);
          };
        }, []);
        function change(key, value) {
          setConfig((c) => ({ ...c, [key]: value }));
          setSaved(false);
          setError("");
        }
        async function save() {
          setBusy(true);
          setError("");
          setSaved(false);
          try {
            const r = await request("relay/config", {
              enabled: !!config.serverUrl.trim(),
              serverUrl: config.serverUrl,
              caFile: config.caFile,
              allowInsecureLoopback: config.allowInsecureLoopback
            });
            if (!mounted.current) return;
            setLive(r);
            setSaved(true);
            await refresh(true);
          } catch {
            if (mounted.current) setError("saveFailed");
          } finally {
            if (mounted.current) setBusy(false);
          }
        }
        const state = allowConnections === false && live?.enabled ? "paused" : live?.state || "disabled";
        const knownState = ["disabled", "paused", "registering", "connecting", "connected", "reconnecting", "error"].includes(state) ? state : "error";
        return h(
          Section,
          { title: t("server") },
          !config && !loadFailed && muted(t("loading")),
          loadFailed && h("div", {}, failure(t("loadFailed")), button(t("retry"), { onClick: () => refresh() })),
          config && h(
            "div",
            { className: "remote-card remote-form" },
            h(Field, {
              title: t("address"),
              name: "server",
              value: config.serverUrl,
              disabled: busy,
              placeholder: "https://example.com",
              onChange: (v) => change("serverUrl", v)
            }),
            h(Field, { title: t("ca"), name: "ca", value: config.caFile, disabled: busy, hint: t("caHint"), onChange: (v) => change("caFile", v) }),
            h(
              "div",
              { className: "remote-row" },
              h("span", { className: "remote-status", "data-state": knownState, role: "status" }, t(knownState)),
              h(
                "div",
                { className: "remote-actions" },
                saved && h("span", { className: "remote-muted", role: "status" }, t("saved")),
                button(t(busy ? "saving" : "save"), { disabled: busy, onClick: save })
              )
            ),
            live?.detail && failure(t(detailKey(live.detail)))
          ),
          failure(error && t(error))
        );
      }
      function AddressPicker({ t, value, addresses, disabled, onChange, onCommit }) {
        const [open, setOpen] = useState(false), [active, setActive] = useState(-1);
        const input = useRef(null);
        const choose = (address) => {
          onChange(address);
          onCommit(address);
          setOpen(false);
          setActive(-1);
          input.current?.focus();
        };
        return h(
          "div",
          { className: "remote-address-picker", onBlur: (e) => {
            if (!e.currentTarget.contains(e.relatedTarget)) {
              setOpen(false);
              setActive(-1);
              if (value.trim()) onCommit(value.trim());
            }
          } },
          h(
            "div",
            { className: "remote-address-control" },
            h("input", {
              ref: input,
              id: "dsh-direct-address",
              role: "combobox",
              autoComplete: "off",
              spellCheck: false,
              "aria-expanded": open && addresses.length > 0,
              "aria-controls": "dsh-direct-options",
              "aria-autocomplete": "none",
              "aria-activedescendant": open && active >= 0 ? "dsh-direct-option-" + active : void 0,
              value,
              placeholder: "https://example.com",
              disabled,
              onChange: (e) => {
                onChange(e.target.value);
                setOpen(false);
                setActive(-1);
              },
              onKeyDown: (e) => {
                if (e.key === "Escape" && open) {
                  e.preventDefault();
                  e.stopPropagation();
                  setOpen(false);
                  return;
                }
                if ((e.key === "ArrowDown" || e.key === "ArrowUp") && addresses.length) {
                  e.preventDefault();
                  setOpen(true);
                  setActive((i) => i < 0 ? e.key === "ArrowDown" ? 0 : addresses.length - 1 : (i + (e.key === "ArrowDown" ? 1 : addresses.length - 1)) % addresses.length);
                }
                if (e.key === "Enter") {
                  e.preventDefault();
                  if (open && active >= 0) choose(addresses[active]);
                  else if (value.trim()) onCommit(value.trim());
                }
              }
            }),
            h(
              "button",
              {
                type: "button",
                className: "remote-address-toggle",
                disabled: disabled || !addresses.length,
                "aria-label": t("chooseAddress"),
                "aria-expanded": open,
                onClick: () => {
                  setOpen(!open);
                  setActive(Math.max(0, addresses.indexOf(value)));
                  input.current?.focus();
                }
              },
              h(
                "svg",
                { viewBox: "0 0 20 20", width: 16, height: 16, fill: "none", stroke: "currentColor", strokeWidth: 1.5, "aria-hidden": true },
                h("path", { d: open ? "m5 12 5-5 5 5" : "m5 8 5 5 5-5" })
              )
            )
          ),
          open && addresses.length > 0 && h(
            "div",
            { id: "dsh-direct-options", role: "listbox", "aria-label": t("chooseAddress"), className: "remote-address-menu" },
            addresses.map((address, i) => h(
              "button",
              {
                type: "button",
                key: address,
                id: "dsh-direct-option-" + i,
                role: "option",
                "aria-selected": address === value,
                tabIndex: -1,
                "data-active": i === active,
                onMouseDown: (e) => e.preventDefault(),
                onMouseEnter: () => setActive(i),
                onClick: () => choose(address)
              },
              h("span", {}, address),
              address === value && h("span", { "aria-hidden": true }, "\u2713")
            ))
          )
        );
      }
      function AddDevice({ t, server, onClose }) {
        const [mode, setMode] = useState(""), [pairing, setPairing] = useState(null);
        const [addresses, setAddresses] = useState([]), [directAddress, setDirectAddress] = useState("");
        const [directQr, setDirectQr] = useState(null), [qrBusy, setQrBusy] = useState(false);
        const [busy, setBusy] = useState(false), [error, setError] = useState(""), [copied, setCopied] = useState(false);
        const [now, setNow] = useState(Date.now());
        const generation = useRef(0), qrGeneration = useRef(0), discoverySession = useRef(null);
        function stopDiscovery() {
          const session = discoverySession.current;
          discoverySession.current = null;
          if (session) void request("discovery/stop", { session }).catch(() => {
          });
        }
        useEffect(() => {
          const clock = setInterval(() => setNow(Date.now()), 1e3);
          let renewing = false;
          const heartbeat = setInterval(async () => {
            const session = discoverySession.current;
            if (!session || renewing) return;
            renewing = true;
            try {
              const value = await request("discovery/renew", { session });
              if (discoverySession.current === session) setPairing({ ...value, expiresAt: Date.now() + value.expiresInSec * 1e3 });
            } catch {
              if (discoverySession.current === session) {
                stopDiscovery();
                setPairing(null);
                setError("discoveryEnded");
              }
            } finally {
              renewing = false;
            }
          }, 1e4);
          return () => {
            generation.current++;
            qrGeneration.current++;
            stopDiscovery();
            clearInterval(clock);
            clearInterval(heartbeat);
          };
        }, []);
        async function showDirectQr(address) {
          const current = ++qrGeneration.current;
          setDirectQr(null);
          setQrBusy(true);
          setError("");
          try {
            const value = await request("direct/qr", { address });
            if (current === qrGeneration.current) {
              setDirectQr(value);
              try {
                localStorage.setItem("dsh-remote.last-direct-address", address);
              } catch {
              }
            }
          } catch {
            if (current === qrGeneration.current) setError("requestFailed");
          } finally {
            if (current === qrGeneration.current) setQrBusy(false);
          }
        }
        async function load(nextMode) {
          const current = ++generation.current;
          qrGeneration.current++;
          stopDiscovery();
          setMode(nextMode);
          setPairing(null);
          setDirectQr(null);
          setError("");
          setCopied(false);
          setBusy(true);
          setQrBusy(false);
          try {
            if (nextMode === "server" || nextMode === "code") {
              const value = await request(nextMode === "server" ? "relay/pairing" : "discovery/start", {});
              if (generation.current !== current) {
                if (value.session) void request("discovery/stop", { session: value.session }).catch(() => {
                });
                return;
              }
              if (nextMode === "code") discoverySession.current = value.session;
              setPairing({ ...value, expiresAt: value.expiresAt || Date.now() + value.expiresInSec * 1e3 });
              setNow(Date.now());
            } else if (nextMode === "direct") {
              const info = await request("direct/info");
              if (generation.current !== current) return;
              const list = info.addresses || [];
              setAddresses(list);
              let last = "";
              try {
                last = localStorage.getItem("dsh-remote.last-direct-address") || "";
              } catch {
              }
              const address = list.includes(last) ? last : list.find((a) => /^https?:\/\/192\.168\./.test(a)) || list[0] || "";
              setDirectAddress(address);
              if (address) void showDirectQr(address);
            }
          } catch {
            if (generation.current === current) setError("requestFailed");
          } finally {
            if (generation.current === current) setBusy(false);
          }
        }
        async function copy(value) {
          try {
            await navigator.clipboard.writeText(value);
            setCopied(true);
          } catch {
            setError("copyFailed");
          }
        }
        const seconds = pairing ? Math.max(0, Math.min(mode === "server" ? 120 : 600, Math.ceil((pairing.expiresAt - now) / 1e3))) : 0;
        const codeDisplay = pairing && h(
          "div",
          { className: "remote-code-row" },
          h(
            "div",
            {},
            h("div", { className: "remote-code" }, seconds > 0 ? pairing.code : "\u2014 \u2014 \u2014"),
            muted(seconds > 0 ? t("expires", { time: time(seconds) }) : t("qrExpired"))
          ),
          button(t("regenerate"), { disabled: busy, onClick: () => load("code") })
        );
        const choice = (key, icon, hint, target, disabled = false) => button(
          h(
            React.Fragment,
            {},
            h("div", { className: "remote-choice-title" }, h(Icon, { name: icon }), h("strong", {}, t(key))),
            muted(t(hint))
          ),
          { className: "remote-choice", disabled, onClick: () => load(target) }
        );
        return h(
          Modal,
          {
            className: "dsh-remote",
            open: true,
            title: mode === "direct" ? t("direct") : mode === "code" ? t("codeDirect") : t("addDevice"),
            closeLabel: t("close"),
            onClose,
            footer: h(
              "div",
              { className: "remote-add-footer" },
              (mode === "direct" || mode === "server") && h("p", { className: "remote-muted remote-footer-hint" }, t(mode === "direct" ? "approvalInstructions" : "serverApprovalInstructions")),
              h(
                "div",
                { className: "remote-footer-row" },
                (mode === "direct" || mode === "server") && button(t(copied ? "copied" : "copyAddress"), {
                  className: "remote-footer-copy",
                  disabled: busy || (mode === "direct" ? !directQr || qrBusy : !pairing || seconds <= 0),
                  onClick: () => copy(mode === "direct" ? directQr.link : pairing.link)
                }),
                h("div", { className: "remote-actions remote-footer-actions" }, mode && button(t("back"), { onClick: () => {
                  generation.current++;
                  qrGeneration.current++;
                  stopDiscovery();
                  setMode("");
                  setPairing(null);
                  setBusy(false);
                  setError("");
                } }), button(t("close"), { onClick: onClose }))
              )
            )
          },
          h(
            "div",
            { className: "dsh-remote remote-modal-copy remote-add-body" },
            !mode && h(
              "div",
              { className: "remote-choices" },
              choice("direct", "computer", "directChoice", "direct"),
              choice("codeDirect", "device", "codeDirectChoice", "code"),
              choice("serverConnection", "server", "serverChoice", "server", server?.state !== "connected"),
              server?.state !== "connected" && muted(t("connectFirst"))
            ),
            busy && muted(t("loading")),
            mode === "server" && !busy && (pairing && seconds > 0 ? h(
              React.Fragment,
              {},
              muted(t("qrHint")),
              h("img", { className: "remote-qr", src: pairing.image, alt: t("qrAlt") }),
              h("div", { className: "remote-qr-meta" }, muted(t("expires", { time: time(seconds) })))
            ) : button(t("regenerate"), { onClick: () => load("server") })),
            mode === "code" && !busy && h(
              React.Fragment,
              {},
              muted(t("discoveryInstructions")),
              pairing && h(
                "div",
                { className: "remote-discovery-computer" },
                h(Icon, { name: "computer" }),
                h("strong", {}, pairing.name),
                muted(t("discoveryMarker", { id: pairing.session?.slice(-6) }))
              ),
              codeDisplay,
              pairing && h("p", { className: "remote-muted", role: "status" }, t(pairing.discoveryFailed ? "discoveryFailed" : "discoveryActive")),
              !pairing && button(t("regenerate"), { onClick: () => load("code") })
            ),
            mode === "direct" && h(
              React.Fragment,
              {},
              muted(t("directAddressHint")),
              h("label", { className: "remote-address-label", htmlFor: "dsh-direct-address" }, t("directUrl")),
              h(AddressPicker, {
                t,
                value: directAddress,
                addresses,
                disabled: busy,
                onChange: (value) => {
                  if (value === directAddress) return;
                  qrGeneration.current++;
                  setDirectAddress(value);
                  setDirectQr(null);
                  setQrBusy(false);
                  setCopied(false);
                },
                onCommit: (address) => {
                  if (address && directQr?.link !== address) void showDirectQr(address);
                }
              }),
              !addresses.length && !busy && muted(t("noAddress")),
              qrBusy && muted(t("generating")),
              directQr && h("img", { className: "remote-qr", src: directQr.image, alt: t("directQr") })
            ),
            failure(error && t(error))
          )
        );
      }
      function DevicesSection({ t, access, updateAccess, accessBusy, server }) {
        const [adding, setAdding] = useState(false);
        useEffect(() => {
          if (!access?.allowConnections) setAdding(false);
        }, [access?.allowConnections]);
        const [devices, setDevices] = useState(null), [pending, setPending] = useState(null);
        const [computer, setComputer] = useState(null), [editing, setEditing] = useState(null);
        const [resetName, setResetName] = useState(false);
        const [name, setName] = useState(""), [renaming, setRenaming] = useState(false), [renameError, setRenameError] = useState("");
        const [busyId, setBusyId] = useState(null), [responding, setResponding] = useState(false);
        const [error, setError] = useState(false), [responseError, setResponseError] = useState(false);
        const mounted = useRef(false), polling = useRef(false);
        async function refresh() {
          if (polling.current) return;
          polling.current = true;
          try {
            const [list, status] = await Promise.all([request("pair/list"), request("pair/status")]);
            if (!mounted.current) return;
            setDevices(list.devices || []);
            setComputer(list.computer);
            setError(false);
            setPending(status.state === "pending" ? status.pendingDevice : null);
            if (status.state === "pending") setAdding(false);
          } catch {
            if (mounted.current) setError(true);
          } finally {
            polling.current = false;
          }
        }
        useEffect(() => {
          mounted.current = true;
          refresh();
          const timer = setInterval(refresh, 2e3);
          return () => {
            mounted.current = false;
            clearInterval(timer);
          };
        }, []);
        async function revoke(id) {
          setBusyId(id);
          setError(false);
          try {
            await request("pair/revoke", { deviceId: id });
            await refresh();
          } catch {
            if (mounted.current) setError(true);
          } finally {
            if (mounted.current) setBusyId(null);
          }
        }
        async function respond(outcome) {
          if (!pending || responding) return;
          setResponding(true);
          setResponseError(false);
          try {
            await request("pair/respond", { deviceId: pending.deviceId, outcome });
            if (mounted.current) setPending(null);
            await refresh();
          } catch {
            if (mounted.current) setResponseError(true);
          } finally {
            if (mounted.current) setResponding(false);
          }
        }
        function edit(device, isComputer = false) {
          setEditing({ ...device, isComputer });
          setName(device.name);
          setResetName(false);
          setRenameError("");
        }
        async function rename() {
          if (!editing || renaming) return;
          if (!name.trim() || name.trim().length > 64 || /[\x00-\x1f\x7f]/.test(name)) {
            setRenameError("invalidName");
            return;
          }
          setRenaming(true);
          setRenameError("");
          try {
            await request(editing.isComputer ? "computer/rename" : "pair/rename", { deviceId: editing.deviceId, ...editing.isComputer && resetName ? { reset: true } : { name: name.trim() } });
            if (mounted.current) setEditing(null);
            await refresh();
          } catch {
            if (mounted.current) setRenameError("requestFailed");
          } finally {
            if (mounted.current) setRenaming(false);
          }
        }
        return h(
          Section,
          { title: t("devices"), action: h(
            "div",
            { className: "remote-actions" },
            button(h(Icon, { name: "refresh" }), { variant: "ghost", "aria-label": t("refresh"), onClick: refresh }),
            button(t("add"), { variant: "primary", disabled: !access?.allowConnections || accessBusy, onClick: () => setAdding(true) })
          ) },
          computer && h(
            "div",
            { className: "remote-computer" },
            h("span", {}, t("thisComputer") + " \xB7 " + computer.name),
            button(h(Icon, { name: "edit" }), { variant: "ghost", "aria-label": t("renameComputer"), onClick: () => edit(computer, true) })
          ),
          h(
            "div",
            { className: "remote-card remote-devices" },
            h(
              "div",
              { className: "remote-device-row" },
              h("span", {}, t("allowConnections")),
              h(Switch, { checked: !!access?.allowConnections, disabled: !access || accessBusy, label: t("allowConnections"), onChange: (v) => updateAccess({ allowConnections: v }) })
            ),
            !devices ? muted(t("loading")) : devices.length ? devices.map((d) => h(
              "div",
              { key: d.deviceId, className: "remote-device-row" },
              h(
                "div",
                { className: "remote-device-info" },
                h(Icon, { name: "device" }),
                h("div", { className: "remote-name" }, h("strong", {}, d.name || d.deviceId), muted(lastConnected(t, d.lastConnectedAt)))
              ),
              h(
                "div",
                { className: "remote-actions" },
                button(h(Icon, { name: "edit" }), { variant: "ghost", "aria-label": t("renameNamed", { name: d.name }), disabled: !!busyId, onClick: () => edit(d) }),
                button(t(busyId === d.deviceId ? "revoking" : "revoke"), { variant: "ghost", className: "remote-pill", disabled: !!busyId, onClick: () => revoke(d.deviceId) })
              )
            )) : h("p", { className: "remote-empty" }, t("noDevices"))
          ),
          failure(error && t("requestFailed")),
          adding && access?.allowConnections && h(AddDevice, { t, server, onClose: () => setAdding(false) }),
          h(
            Modal,
            {
              className: "dsh-remote",
              open: !!editing,
              title: t("renameDevice"),
              closeLabel: t("cancel"),
              onClose: () => {
                if (!renaming) setEditing(null);
              },
              footer: h(
                "div",
                { className: "remote-actions" },
                button(t("cancel"), { disabled: renaming, onClick: () => setEditing(null) }),
                editing?.isComputer && button(t("reset"), { disabled: renaming || !computer?.defaultName, onClick: () => {
                  setName(computer.defaultName);
                  setResetName(true);
                  setRenameError("");
                } }),
                button(t(renaming ? "saving" : "save"), { variant: "primary", disabled: renaming || !name.trim(), onClick: rename })
              )
            },
            h(
              "form",
              { className: "dsh-remote", onSubmit: (e) => {
                e.preventDefault();
                rename();
              } },
              h(Field, { title: t("deviceName"), name: "device-name", value: name, disabled: renaming, onChange: (v) => {
                setName(v);
                setResetName(false);
                setRenameError("");
              } }),
              failure(renameError && t(renameError))
            )
          ),
          h(
            Modal,
            {
              className: "dsh-remote",
              open: !!pending,
              title: t("approveTitle", { name: pending?.name || "" }),
              description: t("approveHint"),
              closeLabel: t("deny"),
              onClose: () => respond("deny"),
              footer: h(
                React.Fragment,
                {},
                button(t("deny"), { disabled: responding, onClick: () => respond("deny") }),
                button(t(responding ? "responding" : "allow"), { variant: "primary", disabled: responding, onClick: () => respond("approve") })
              )
            },
            h("div", { className: "dsh-remote" }, failure(responseError && t("requestFailed")))
          )
        );
      }
      function HostsSection({ t }) {
        const [hosts, setHosts] = useState(null), [input, setInput] = useState("");
        const [busy, setBusy] = useState(false), [error, setError] = useState("");
        const mounted = useRef(false);
        async function refresh() {
          try {
            const r = await request("trusted-hosts");
            if (mounted.current) {
              setHosts(r.hosts || []);
              setError("");
            }
          } catch {
            if (mounted.current) setError("loadFailed");
          }
        }
        useEffect(() => {
          mounted.current = true;
          refresh();
          return () => {
            mounted.current = false;
          };
        }, []);
        async function mutate(action, host) {
          setBusy(true);
          setError("");
          try {
            const r = await request("trusted-hosts", { action, host });
            if (mounted.current) {
              setHosts(r.hosts || []);
              if (action === "add") setInput("");
            }
          } catch (e) {
            if (mounted.current) setError(/invalid|无效|域名|host/i.test(e.message) ? "invalidDomain" : "requestFailed");
          } finally {
            if (mounted.current) setBusy(false);
          }
        }
        return h(
          Section,
          { title: t("hosts"), hint: t("hostsHint") },
          !hosts ? muted(t("loading")) : !hosts.length ? h("p", { className: "remote-empty" }, t("noHosts")) : h("ul", { className: "remote-list" }, hosts.map((host) => h(
            "li",
            { key: host },
            h("span", { className: "remote-name" }, host),
            button(t("remove"), { disabled: busy, onClick: () => mutate("remove", host) })
          ))),
          h(
            "form",
            { className: "remote-host-form", onSubmit: (e) => {
              e.preventDefault();
              if (!busy && input.trim()) mutate("add", input.trim());
            } },
            h("input", {
              className: "remote-input",
              value: input,
              disabled: busy,
              placeholder: "tunnel.example.com",
              "aria-label": t("domain"),
              autoComplete: "off",
              spellCheck: false,
              onChange: (e) => setInput(e.target.value)
            }),
            button(t(busy ? "working" : "add"), { type: "submit", disabled: busy || !input.trim() })
          ),
          muted(t("hostFormat")),
          failure(error && t(error)),
          error === "loadFailed" && button(t("retry"), { onClick: refresh })
        );
      }
      function Settings({ t }) {
        const [access, setAccess] = useState(null), [server, setServer] = useState(null);
        const [busy, setBusy] = useState(false), [loadError, setLoadError] = useState(""), [saveError, setSaveError] = useState(""), [hostsOpen, setHostsOpen] = useState(false);
        const mounted = useRef(false), updating = useRef(false), revision = useRef(0), loading = useRef(false);
        async function refresh() {
          if (updating.current || loading.current) return;
          loading.current = true;
          const current = revision.current;
          try {
            const r = await request("access/settings");
            if (mounted.current && current === revision.current) {
              setAccess(r);
              setLoadError("");
            }
          } catch (e) {
            if (mounted.current && current === revision.current) setLoadError(accessErrorKey(e));
          } finally {
            loading.current = false;
          }
        }
        useEffect(() => {
          mounted.current = true;
          refresh();
          const timer = setInterval(refresh, 2e3);
          return () => {
            mounted.current = false;
            clearInterval(timer);
          };
        }, []);
        async function updateAccess(patch) {
          if (updating.current) return;
          revision.current++;
          updating.current = true;
          setBusy(true);
          setSaveError("");
          try {
            const r = await request("access/settings", patch);
            if (mounted.current) {
              setAccess(r);
              setLoadError("");
            }
          } catch (e) {
            if (mounted.current) setSaveError(accessErrorKey(e, true));
          } finally {
            updating.current = false;
            if (mounted.current) setBusy(false);
          }
        }
        return h(
          "div",
          { className: "dsh-remote" },
          h("header", { className: "remote-intro" }, h("h2", {}, t("connections"))),
          h(RelaySection, { t, onState: setServer, allowConnections: access?.allowConnections }),
          failure(loadError && t(loadError)),
          loadError && button(t("retry"), { onClick: refresh }),
          failure(saveError && t(saveError)),
          h(DevicesSection, { t, access, updateAccess, accessBusy: busy || !!loadError, server }),
          h(Section, { title: t("otherSettings") }, h(
            "div",
            { className: "remote-card" },
            h(
              "div",
              { className: "remote-device-row" },
              h(
                "div",
                { className: "remote-device-info" },
                h(Icon, { name: "sun" }),
                h(
                  "div",
                  {},
                  h("strong", {}, t("keepAwake")),
                  muted(t("awakeHint")),
                  access?.wake?.state === "unsupported" && muted(t("awakeUnsupported")),
                  access?.keepAwake && access?.allowConnections && access?.wake?.state === "battery" && muted(t("awakeBattery")),
                  access?.keepAwake && access?.allowConnections && access?.wake?.state === "starting" && muted(t("awakeStarting")),
                  access?.wake?.state === "error" && failure(t("awakeFailed"))
                )
              ),
              h(Switch, { checked: !!access?.keepAwake, disabled: busy || !!loadError || !access || !access.wake?.supported && !access.keepAwake, label: t("keepAwake"), onChange: (v) => updateAccess({ keepAwake: v }) })
            ),
            h("div", { className: "remote-device-row" }, h(
              "div",
              { className: "remote-device-info" },
              h(Icon, { name: "globe" }),
              h("div", {}, h("strong", {}, t("hosts")), muted(t("hostsHint")))
            ), button(t("manage"), { variant: "ghost", className: "remote-pill", onClick: () => setHostsOpen(true) }))
          )),
          h(
            Modal,
            { className: "dsh-remote", open: hostsOpen, title: t("hosts"), closeLabel: t("close"), onClose: () => setHostsOpen(false) },
            hostsOpen && h("div", { className: "dsh-remote remote-hosts-modal" }, h(HostsSection, { t }))
          )
        );
      }
      function apply(ctx) {
        ctx.effect(() => ctx.locale.register(NS, { en, zh }));
        ctx.effect(() => {
          const style = document.createElement("style");
          style.dataset.plugin = NS;
          style.textContent = settingsCss;
          document.head.appendChild(style);
          return () => style.remove();
        });
        const t = ctx.locale.bind(NS);
        ctx.slots.inject("settings.section", () => ctx.slots.register({
          name: "settings.section",
          id: NS,
          order: 40,
          label: () => t("nav"),
          locale: NS
        }, Settings));
      }
      return { apply, inject: ["slots", "locale"] };
    }
  });
})();
