# NordCommands Velocity 1.1.0

> Release build and installation requirements: see [BUILDING.md](BUILDING.md).
> Older local paths below describe historical test fixtures, not the release build.

Denies proxy-owned commands for players without `nordcommands.bypass`, checking
both the original root and the effective `CommandResult` replacement at early
and final Velocity priorities. No underlying target-command permissions are
granted. Backend command strings/results are not rebuilt; console is untouched.

Fast event handlers use `async=false` so this plugin does not itself force an
extra asynchronous event dispatch. Other handlers and Velocity command execution
can still be asynchronous. No per-command scheduled tasks, file I/O, extra worker
threads or persistent player database are introduced.

Denial messages are limited to one per 250 ms per session, with a 4096-entry
identity-keyed cap and disconnect/shutdown cleanup. Full notice capacity suppresses
messages, never command denial. Inputs exceeding 32767 UTF-16 code units or
256-code-unit roots, slash-prefixed API inputs, controls and Unicode line separators
are rejected for ordinary players. Velocity event strings omit the first slash;
leading spaces, case and registered namespaced roots are recognized.

Available-command roots are hidden for modern clients. This is presentation, not
an authorization barrier for suggestions: a manually sent modern completion request
or trusted plugin calling `offerSuggestions` may still obtain a proxy command's
suggestions. Target commands must enforce their own `hasPermission`/`requires`
checks, including their suggestion providers. This filter does not modify Velocity
internals or wrap other plugins' command registrations. Its declared guarantee is
execution filtering and modern root-list hiding, not total suggestion secrecy.

Trusted plugins at the same final priority can override results afterward, and
`executeImmediatelyAsync`/direct executors bypass `CommandExecuteEvent`. No plugin
event listener is an isolation boundary against other installed trusted code.

Build and checks: run build.ps1 against isolated LOCAL Velocity libraries.
Network harness: test-support/integration.cjs; probes are fixture-only and must
never be installed on production. See SECURITY-1.1.0.md for evidence and limits.
Production installation requires separately approved proxy downtime and backup.
