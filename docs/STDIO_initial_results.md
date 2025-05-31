Summary
After a thorough investigation of STDIO transport configuration for Android MCP servers, I have determined that this approach is not feasible due to fundamental Android platform limitations.
Key Findings:
Android apps cannot access System.in/System.out - This is a core requirement for STDIO transport that simply doesn't exist in the Android application framework.
ADB cannot bridge this gap - While adb can communicate with Android devices, it cannot pipe STDIO streams to/from Android app processes.
Architecture mismatch - MCP STDIO transport expects traditional process-level I/O, but Android apps run in a managed runtime with sandboxed execution.
What I Implemented:
Created a conceptual AndroidStdioTransport class that demonstrates the approach
Added JSON-RPC message handling for MCP protocol
Updated the MCP server to attempt STDIO transport initialization
Built a sample app that shows the limitation in practice
Documented the technical analysis and alternative solutions
Why It Doesn't Work:
The MCP Kotlin SDK's StdioServerTransport expects direct access to System.in and System.out, which Android apps simply don't have. Even if we could work around this, adb has no mechanism to pipe data directly to/from Android app processes via STDIO.
Working Alternatives:
WebSocket Transport recommended - Android app hosts WebSocket server, accessed via adb port forwarding
HTTP/SSE Transport - Similar approach using HTTP with Server-Sent Events
External Proxy Pattern - Like the existing android-mcp-server project that runs on the host machine and uses adb commands
The investigation confirms that while STDIO transport is elegant for traditional server applications, it's fundamentally incompatible with Android's application architecture. For practical Android MCP server implementation, WebSocket or HTTP transports are the viable paths forward.