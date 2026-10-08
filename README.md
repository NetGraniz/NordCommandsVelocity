# NordCommandsVelocity

Filters proxy-owned commands on Velocity. Backend command text and forwarding results pass through unchanged; the proxy console is not filtered.

## Policy

Players without `nordcommands.bypass` cannot execute proxy-owned commands. The listener checks both the original root and any effective `CommandResult` replacement at early and final event stages.

Handlers use `async=false`: NordCommands does not force asynchronous event dispatch. Other handlers and Velocity's execution path can still run asynchronously. The plugin creates no per-command task, file I/O, extra worker or persistent player database.

Denied-command notices are limited to one per 250 ms per session. The identity-keyed limiter holds at most 4096 sessions and cleans up on disconnect and shutdown. Overflow suppresses the notice, not the denial.

## Permissions

| Permission | Allows |
| --- | --- |
| `nordcommands.bypass` | Execute proxy-owned commands through this filter |

Velocity's permission provider decides player access; this plugin registers no default player grant. A Paper/Folia permission assignment does not grant this proxy permission.

Bypass does not grant the target command's own permission. Its handler must still check access.

## Input and visibility

Ordinary players cannot submit command text over 32767 UTF-16 code units or a root over 256 code units. Slash-prefixed API input, controls and Unicode line separators are rejected. Velocity events normally omit the leading slash.

Root recognition handles leading spaces, case differences and registered namespaced roots. Modern command trees hide blocked proxy roots. Manual completion can still receive suggestions from a trusted command's `offerSuggestions`; that command must check its own completion permission.

NordCommands does not wrap command implementations or modify Velocity internals.

## Trust boundary

Another trusted plugin at the same final event priority can override the result after this listener. Calls to `executeImmediatelyAsync` or direct command executors can bypass `CommandExecuteEvent`. An event listener cannot isolate installed server plugins from each other.

## Build and tests

Use Maven 3.9+ and JDK 25: `mvn clean verify` or `./build.ps1`. The current output is `target/NordCommands-Velocity-1.1.1.jar`. See [BUILDING.md](BUILDING.md).

The historical security report is `SECURITY-1.1.0.md`; it is excluded from the public repository. Network checks use `test-support/integration.cjs` and isolated fixtures. Probe plugins belong only on those fixtures, never on production.
