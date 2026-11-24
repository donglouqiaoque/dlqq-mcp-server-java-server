# DongLouQiaoQue MCP Server (dlqq-mcp-server)

A **Model Context Protocol (MCP) server** written in Java. It provides local tool capabilities to AI agents (DeepSeek Harness, Qwen Code, Claude Code, Cursor, Cline, VS Code, Windsurf, …): files, serial ports, BLE, USB, Wi-Fi, Office, ZIP, screenshots, image conversion, and more.

- **Transport**: stdio (default) and Streamable HTTP
- **Tools**: 183 registered (plus 1 source-masked and 2 planned definitions — 185 in total); see [Tool risk levels and visibility](#tool-risk-levels-and-visibility)
- **Runtime**: Java 25+ (**full JRE required**, see below)
- **JAR name**: `dlqq_mcp_server.jar`

**Project history**: this project was originally `donglouqiaoque-ai-tools`, later renamed to `dlqq-ai-mcp-server`, and is now `dlqq-mcp-server`. The previous repository was deleted and recreated, so the current one is a new repository whose commit history starts from scratch; the implementation and architecture are unchanged, and only the license changed from Apache 2.0 to MIT.

> The Chinese README ([README.md](README.md)) is the reference document; this file mirrors its structure and tool detail in English.

## Features

- **Files & directories** – read/write/create/overwrite/append/clear, line-level insert/delete/replace, regex replace, copy/move/rename/delete with confirmation
- **Binary & BOM** – hex read/write, UTF-8 BOM add/remove
- **Search** – search files/directories and text inside files, partial match or regex, context lines
- **Dialogs** – text input, yes/no confirmation, file picker (Swing)
- **Serial (UART)** – graphical tool for CH340-style USB-to-serial adapters; open/close, send text/hex, read logs
- **BLE** – scan/connect/discover/read/write/subscribe via Python `bleak` and a local socket
- **USB** – HID/CDC scan, open, read/write (`usb4java`)
- **Wi-Fi** – scan/connect/disconnect/status/profiles via `netsh` (Windows) or `nmcli` (Linux)
- **Mouse / keyboard** – `java.awt.Robot`: move, click, drag, scroll, type, hotkeys
- **Screen & images** – capture the desktop (with the real cursor drawn), convert SVG/PNG/JPG/GIF/BMP
- **Office & ZIP** – Office → Markdown, Office format conversion, password-protected ZIP, multi-format unzip
- **Calculators, command execution, project scripts**
- **MCP management** – version/build time, hot-reload check, dynamic tool management, workspace root, properties, cleanup

## Requirements

### Java

- **Java 25 or newer**
- **A full JRE is required.** Trimmed `jlink` images lack `jdk.crypto.ec`, `jdk.zipfs`, `java.net.http`, etc., which breaks PDF, TLS, and zipfs use cases.
- Generate a full JRE (about 130 MB):

```cmd
"<JDK25>\bin\jlink" --module-path "<JDK25>\jmods" --add-modules ALL-MODULE-PATH --output "<target>\jre" --strip-debug --no-man-pages --no-header-files
```

The Java runtime ships with the package: `.runtime\java` is Microsoft Build of OpenJDK 25 (GPLv2+CE). To upgrade, replace the directory and keep `legal\`.

- `WARNING: Using incubator modules: ...` is **normal** (`ALL-MODULE-PATH` includes incubator modules); the exit code is 0 and the JRE works.

### Python (BLE tool)

- **No system Python required**: the BLE tool always uses the bundled `.runtime\python\` (interpreter, tkinter, Tcl/Tk, bleak and ttkbootstrap included)
- Ship the whole `.runtime\` directory; if `python\python.exe` is missing the BLE tool fails with an explicit error and never falls back to a system Python

## Build

Requirements: Java 25+ and Maven.

```cmd
REM Build only (output in target\)
script\build.bat

REM Build and copy to the release folder (recommended)
script\build_and_copy.bat
```

Artifacts:

- `target\dlqq_mcp_server.jar`: thin JAR, project code only
- `target\libs\`: runtime dependencies

`script\build_and_copy.bat` syncs both to `.runtime\`, which is the release directory. The version comes from `pom.xml` `<revision>`; there is no debug/release split.

Or call Maven directly:

```bash
mvn clean package -DskipTests
```

## Run

### Start arguments

| Argument | Meaning |
|----------|---------|
| `--transport http\|stdio` | Transport, default `stdio` |
| `--port N` | HTTP port, default `49129` |
| `--path /mcp` | HTTP path, default `/mcp` |
| `--workspace <dir>` | Workspace root for all tool relative paths (highest priority) |
| `--session-meta-key <key>` | `_meta` key for the conversation session id, default `dsh/sessionId` |
| `--session-agent-meta-key <key>` | `_meta` key for the agent id, default `dsh/agentId` |
| `--session-workspace-meta-key <key>` | `_meta` key for the session workspace dir (cwd), default `dsh/workspace` |
| `--session-idle-minutes <n>` | Idle session reclamation in minutes, default 30; `0` disables |

### stdio (default)

```cmd
java -Dfile.encoding=UTF-8 -jar dlqq_mcp_server.jar
```

### Streamable HTTP

```cmd
java -Dfile.encoding=UTF-8 -jar dlqq_mcp_server.jar --transport http --port 49129 --path /mcp
```

- Endpoint: `http://127.0.0.1:49129/mcp`
- Health check: `GET http://127.0.0.1:49129/mcp` → `{"status":"ok", ...}`
- If the port is busy, the console asks for another port; **Enter** retries the default port; a non-interactive console exits with code 1.
- The HTTP server is unauthenticated; session isolation is driven by `params._meta` only (`Mcp-Session-Id` is for the protocol/SSE, not the workspace). Clients only need the URL.

### Deployment layout

```text
.runtime\                        # the whole directory is the deployment package
├── start-http.bat               # ASCII-only launcher (port 49129)
├── dlqq_mcp_server.jar          # thin JAR
├── install_dlqq_mcp_dsh_client.bat / .ps1  # install the DSH client plugin automatically
├── make_python_env.bat / .py    # rebuild the Python runtime
├── java\                        # Microsoft OpenJDK 25
│   └── bin\java.exe
├── python\                      # CPython 3.14.9 with dependencies
│   └── python.exe
└── libs\                        # all Maven dependencies
```

### Launcher behavior (`start-http.bat`)

Double-click `start-http.bat`: it **first asks for the workspace directory** (must be an existing absolute directory; press **Enter** to use the current directory), validates it, and only then starts the HTTP server with `--workspace`, using the bundled `java\bin\java.exe`. If Enter is pressed, no `--workspace` is passed and the server workspace defaults to the process CWD. After the workspace is fixed, runtime data (`cache`/`log`/`settings`, ...) lives under `<jar dir>\.dlqq` (one global directory, never inside workspaces).

### Auto-install the DSH client plugin (`install_dlqq_mcp_dsh_client.bat`)

- Downloads the `dlqq-mcp-server-dsh-client` source package from **Gitee (preferred)**, and automatically falls back to **GitHub** when Gitee fails;
- Installs it to `%USERPROFILE%\.dsh\plugins\dlqq-mcp-client` and runs `npm install` for the plugin dependencies;
- Registers the plugin in the DSH profile `package.json` (`link:` dependency + `dsh.profile.bundles`) and, when `cordis.patch.yml` has **no** `dlqq-mcp-client` entry yet, appends an enabled entry whose `command`/`args` point at this deployment's `java\bin\java.exe` and `dlqq_mcp_server.jar`;
- Usage (**close DeepSeek Harness first**):

  ```cmd
  install_dlqq_mcp_dsh_client.bat
  install_dlqq_mcp_dsh_client.bat "D:\path\to\other-profile"
  ```

  The default profile is `%USERPROFILE%\.dsh\profiles\desktop`; Node.js 20+ (`npm` on PATH) is required.
- Restart DSH after the installation.

Port handling is implemented inside Java: an occupied port asks for a new one at the console; **Enter** retries the default port; a non-interactive console exits with code 1.

## Connect an AI agent

The server is a standard MCP server (JSON-RPC 2.0) and is not tied to a client. Examples below use `<deploy dir>` as a placeholder — replace it with your real deployment directory.

### DeepSeek Harness (DSH)

File: `C:\Users\<you>\.dsh\profiles\desktop\cordis.patch.yml`.

stdio:

```yaml
- insert:
    - id: dlqq-mcp-server
      name: "@deepseek-ai/dsh-mcp-client"
      config:
        serverName: dlqq-mcp
        transport: stdio
        command: '<deploy dir>\java\bin\java.exe'
        args:
          - "-Dfile.encoding=UTF-8"
          - "-jar"
          - '<deploy dir>\dlqq_mcp_server.jar'
```

Streamable HTTP:

```yaml
- insert:
    - id: dlqq-mcp-server-http
      name: "@deepseek-ai/dsh-mcp-client"
      config:
        serverName: dlqq-mcp-http
        transport: streamable-http
        url: http://127.0.0.1:49129/mcp
```

Restart DSH after editing.

### Qwen Code

`settings.json` (not `mcp.json`):

```json
{
  "mcpServers": {
    "dlqq-mcp-server": {
      "command": "java",
      "args": ["-Dfile.encoding=UTF-8", "-jar", "E:\\path\\to\\dlqq_mcp_server.jar"],
      "env": { "JAVA_TOOL_OPTIONS": "-Dfile.encoding=UTF-8" }
    }
  }
}
```

HTTP: `"httpUrl": "http://127.0.0.1:49129/mcp"` instead of `command`/`args`.

### Claude Code

```json
{
  "mcpServers": {
    "dlqq-mcp-server": {
      "command": "java",
      "args": ["-Dfile.encoding=UTF-8", "-jar", "E:\\path\\to\\dlqq_mcp_server.jar"]
    }
  }
}
```

HTTP: `claude mcp add --transport http dlqq-mcp-server http://127.0.0.1:49129/mcp`, or JSON with `"type": "http"` and `"url"`.

### Cursor

`.cursor\mcp.json`:

```json
{
  "mcpServers": {
    "dlqq-mcp-server": {
      "command": "java",
      "args": ["-Dfile.encoding=UTF-8", "-jar", "E:\\path\\to\\dlqq_mcp_server.jar"]
    }
  }
}
```

HTTP: `"url": "http://127.0.0.1:49129/mcp"`.

### VS Code (GitHub Copilot)

`.vscode\mcp.json` (uses `servers` and `type`):

```json
{
  "servers": {
    "dlqq-mcp-server": {
      "type": "stdio",
      "command": "java",
      "args": ["-Dfile.encoding=UTF-8", "-jar", "E:\\path\\to\\dlqq_mcp_server.jar"]
    }
  }
}
```

HTTP: `"type": "http"` + `"url": "http://127.0.0.1:49129/mcp"`.

### Cline / Roo Code

`cline_mcp_settings.json`:

```json
{
  "mcpServers": {
    "dlqq-mcp-server": {
      "command": "java",
      "args": ["-Dfile.encoding=UTF-8", "-jar", "E:\\path\\to\\dlqq_mcp_server.jar"]
    }
  }
}
```

HTTP: `"type": "streamableHttp"` + `"url"`.

### Windsurf

```json
{
  "mcpServers": {
    "dlqq-mcp-server": {
      "command": "java",
      "args": ["-Dfile.encoding=UTF-8", "-jar", "E:\\path\\to\\dlqq_mcp_server.jar"]
    }
  }
}
```

HTTP: `"serverUrl": "http://127.0.0.1:49129/mcp"`.

## Workspace

The workspace is the **base directory for all relative paths** used by tools. Default: the process working directory; set per launch with `--workspace <dir>` or change it at runtime.

**The workspace path is never persisted**: `dlqq_workspace_set` only affects the current run; after a restart, specify `--workspace` again. This avoids saving a wrong path that would break later workspace selection.

| Tool | Purpose | Parameters |
|------|---------|------------|
| `dlqq_workspace_set` | Set the workspace (existing absolute directory); effective immediately, not persisted | `path` (string, required) |
| `dlqq_workspace_get` | Show workspace, source, CLI value, write permission, permission file and process CWD | none |
| `dlqq_workspace_reset` | Fall back to `--workspace` or the process CWD; permission config untouched | none |
| `dlqq_workspace_permission_set` | Set the workspace write permission and persist it in the workspace | `mode`: `readonly` / `workspace` / `approval` / `full` |

**Write permission** is stored **inside the workspace** at `.dlqq/settings/settings.properties` (`workspace.write.mode`) and reloaded whenever the workspace changes:

| Mode | Meaning | Behavior |
|------|---------|----------|
| `readonly` | Read-only | Every file modification is rejected (create/write/append/delete/move/rename/batch replace/BOM/image output/ZIP output, …) |
| `workspace` | Workspace-only (default) | Only files inside the workspace may be modified; outside paths are rejected |
| `approval` | Outside-workspace approval | Inside the workspace: direct writes; outside: a confirmation dialog asks the user first (previous behavior) |
| `full` | Full permission | Any location may be modified; no outside-workspace confirmation dialog |

The workspace affects relative paths in file/directory/search/binary/ZIP/Office/image/screen/dialog/command tools, the script execution directory, BLE script directory, and the base of `.aiignore/.aiwriterules/.aireadrules`. The workspace `.dlqq` directory (logs, cache and settings) follows the workspace; the workspace defaults to the process CWD.

## Dynamic tool management (runtime)

| Tool | Purpose | Parameters |
|------|---------|------------|
| `dlqq_mcp_remove_tools` | Hide/disable tools; `tools/list` no longer returns them and `tools/call` reports “tool removed”; persisted | `tool_names` (array/CSV, required) |
| `dlqq_mcp_refresh_tools` | Broadcast `notifications/tools/list_changed` only | none |
| `dlqq_mcp_reset_tools` | Restore every removed tool, clear the exclusion list, broadcast a refresh | none |
| `dlqq_mcp_run_tools` | Call any registered tool by name, bypassing the “removed / not loaded by the client” restriction | `name` (string, required), `arguments` (object) |

- Removed names are stored in `<workspace>/.dlqq/settings/settings.properties` as `mcp.tools.excluded` and re-applied at startup.
- The four management tools are protected and cannot be removed.
- Over HTTP, immediate client refresh requires an open SSE stream (`GET /mcp` with `Accept: text/event-stream`); without SSE the change still applies and persists.
- **Notification policy on connect**: the server does **not** push `notifications/tools/list_changed` immediately when a client connects. Only after `initialize` / `notifications/initialized`, if the client still has not requested `tools/list` within the delay (default **10 s**, configurable with `-Dmcp.listChanged.delay.ms=5000`), the server sends the notification once as a fallback. Any client message cancels it, and after `tools/list` it is never sent. A normal client that lists tools itself never receives it.

## Permissions and timeouts

### Two permission dimensions (the old workspace.write.mode is gone)

| Setting | Meaning |
|---|---|
| `permission.resource.level` | **Resource permission**: files, directories, commands/processes, archives, MCP data |
| `permission.operation.level` | **Operation permission**: mouse, keyboard, screen, serial, BLE, WiFi, USB |
| `tool.policy.<tool>` | Per-tool override when the level is `custom` (allow / approve / deny) |

Resource permission defaults to `chat` (conversation mode: no resource operations).
Levels: `chat` < `readonly` < `workspace` < `approval` < `highrisk` < `full` < `custom`.

Operation permission defaults to `chat` (conversation mode: no peripheral operations).
Levels: `chat` < `readonly` < `approval` < `highrisk` < `full` < `custom`.

- A new workspace (including process CWD and `default_workspace`) starts as `chat`; escalate with the permission tool first;
- All sessions pointing at the same workspace share one settings file; switching workspace switches settings and permissions;
- The legacy `workspace.write.mode` is deprecated: no longer read and removed when the new keys are written;
- AI can never read or write anything under `.dlqq` at any permission level, except the content inside the current
  workspace cache directory (`.dlqq/cache/<workspace hash>/`, workspace-level only, no per-session split);
- Permissions can only be changed through `dlqq_workspace_permission_set`
  (`dlqq_set_mcp_properties` rejects permission-related keys).

### Switching the workspace

- `dlqq_workspace_set` / `dlqq_workspace_reset` are callable under **any** permission level;
- Same target as the current workspace -> returns "no change" without any dialog;
- Switching/resetting the workspace **always requires user approval** in a countdown dialog;
- A failed set (missing / not a directory / under `.dlqq`) is **rejected and the current workspace is kept** (no fallback).

### Escalation

- Downgrade or same level: executed directly, no dialog;
- Escalation: countdown approval dialog, auto-cancelled on timeout.

### Tool timeouts

- Client (DSH plugin) default per-call timeout: **60 s**, configurable via `toolCallTimeoutMs`;
- Server-side total timeout = **approval time + execution time**, default **50 s**;
- Override per call with `params._meta["dlqq/toolTimeoutSeconds"]` or `tool_timeout_seconds`;
- Background calls (`no_wait=true`) return immediately and are not bounded by the total timeout;
- On timeout the approval window is closed, execution is aborted and a clear error is returned;
- Every approval window counts down the remaining tool time and auto-closes on timeout.

## Runtime directory (single global .dlqq)

All runtime data lives in **one directory next to the jar**; no `.dlqq` is created inside user workspaces anymore:

```text
<deploy dir>/
├── dlqq_mcp_server.jar
├── default_workspace/                 # fallback workspace (used when a workspace is unusable)
└── .dlqq/
    ├── workspace_config.json          # workspace absolute path -> hash mapping
    ├── cache/<workspace hash>/        # cache (workspace level only, no per-session split)
    ├── data/                          # shared data (BLE scripts, icons, ...)
    ├── log/stdio/<startup time>.log   # one log file per process start, per transport
    ├── log/http/<startup time>.log
    └── settings/<workspace hash>/settings.properties
```

- **Workspace resolution**: session `_meta` workspace -> `--workspace` -> process CWD; any fallback goes to
  `default_workspace` (CWD is only the default value, never a fallback target);
- **Workspace identity**: normalized absolute path (case-insensitive) hashed with SHA-256 (8..32 hex chars,
  lengthened on collision);
- **Startup failure**: if `.dlqq` or `default_workspace` cannot be created or written, startup fails
  (dialog + stderr + exit code); there is no user-directory fallback;
- **Logs**: one new file per process start; the startup timestamp is **millisecond-precise** (`yyyy-MM-dd_HH-mm-ss-SSS`), logger and stderr merged, with `-2`, `-3` suffixes only if a name still collides;
- **Cleanup tools**: `dlqq_clear_mcp_cache` accepts `workspace`/`all`; `dlqq_clear_mcp_data` only clears
  cache/data/log and **never deletes settings** (permission config is protected);
- **Protocol discovery**: the MCP 2026-07-28+ `server/discover` probe is supported (advertising compatible
  2025-era protocol versions), so modern clients fall back to the legacy `initialize` handshake instead of
  killing and respawning the process after a failed probe;
- **JAR replacement watcher (poor-man's hot reload)**: every **2 seconds** while running, the server checks
  `dlqq_mcp_server.jar` on disk: (1) compares the in-memory compile-time constant `BuildInfo.BUILD_TIME` with the JAR
  manifest `Build-Time` (build-version match); (2) falls back to file mtime/size. Any difference means the JAR was
  rebuilt/overwritten, so it **logs an error, shows a topmost notice dialog and rejects new tool calls**; foreground
  tool calls are awaited for at most **5 s** (forced exit on timeout), while **background (`no_wait`) calls are not
  waited for and end with the process**; the notice dialog stays visible for **at least 2 s**. The process then exits and
  the DSH client auto-reconnects and starts a fresh process with the new JAR.
  **Stopping the server before rebuilding is still recommended**.

## Server console window

When a graphical environment is available, the console **starts hidden in the system tray** (no window pops up);
**single-click** the tray icon or use the tray menu "Show console" to open it:

- The menu bar centre shows "联系作者：https://www.donglouqiaoque.com" (click to open the default browser); the
  "Refresh" button is on its right with a small right margin;
- The native title bar text is cleared (no window title is shown); the session/workspace table sits right below the menu bar;
- The table is sorted by **last activity (descending)** and shows two kinds of rows: active sessions, plus workspaces recorded in
  `workspace_config.json` that currently have no active session; columns: session key (workspace rows show "（工作区）"), workspace,
  source (`session cwd (...)`, `server console` or `workspace_config.json`), resource permission, operation permission and last
  activity (`yyyy-MM-dd HH:mm:ss`, refreshed every 2 s);
- The **resource permission, operation permission and last-activity columns (headers included) are centre-aligned**; clicking an empty
  area of the table clears the current selection;
- **The workspace, resource permission and operation permission columns are edited directly in the table by double-clicking a cell**
  (only session rows can change the workspace; it must be an existing absolute directory and must not be under `.dlqq`; the workspace
  cell has a **directory-picker button** on its right that opens a folder chooser; permissions are chosen from a dropdown);
  changes apply immediately and are written into that workspace's settings, so no separate bottom controls are needed;
  only an **actual change** of a permission value or the workspace path counts as an operation; merely opening the
  dropdown/picker and closing it with the original value writes nothing and updates no timestamps;
- The bottom area shows the deploy directory, runtime directory, current log file, active session count and table row count; long
  paths can be scrolled horizontally to read the full path;
- **Closing the window does not terminate the process: it hides the console to the system tray**; single-click the tray icon to restore it;
- The tray right-click menu (and the window menu) offers "Show console / Exit server";
- Only choosing "Exit server" terminates the MCP server process (clients lose the connection);
- No window is created in a headless environment.

## Multi-session in one process (session isolation)

One server process serves many conversations and many agents; each session keeps its
own workspace and configuration.

### Session identity (stdio and HTTP alike)

Only `params._meta` is used, sent by the client on every `tools/call`:

| Key (default, configurable) | Meaning |
|---|---|
| `dsh/sessionId` (`--session-meta-key`) | Conversation/session id |
| `dsh/agentId` (`--session-agent-meta-key`) | Agent id (root agent or subagent) |
| `dsh/workspace` (`--session-workspace-meta-key`) | Session workspace dir (cwd); initialized on that session's first request (invalid/missing/`.dlqq` paths fall back silently) |

Resulting server-side session key:

- both present → `session:<session>|agent:<agent>` (conversation × agent isolation);
- session only → `session:<session>`;
- agent only → `agent:<agent>`;
- **neither → default session** (process-level, no multi-session split; keeps clients
  that do not support multi-session fully compatible).

HTTP `Mcp-Session-Id` is used for the protocol and SSE only and never drives the workspace.

### Session-scoped state

Each session owns:

- its workspace root (`dlqq_workspace_set` / `dlqq_workspace_get` / `dlqq_workspace_reset`
  affect only the current session; when not set explicitly it can be initialized from the
  client-provided session cwd in `_meta` on the first request);
- its resource permission (`permission.resource.level`) and operation permission
  (`permission.operation.level`);
- every `settings.properties` value under that workspace (script paths, search config,
  properties, excluded tools, ...).

Different conversations can therefore point at different projects without interfering.

### Lifecycle

| Event | Handling |
|---|---|
| A new session key first appears | Session created with the startup base workspace (`--workspace` or process CWD) |
| Idle longer than `--session-idle-minutes` | Reclaimed by a background thread (default 30 min, `0` disables) |
| HTTP `DELETE` | Removes the protocol session only; `_meta` sessions are reclaimed by the idle sweep |

### Diagnostics

`dlqq_workspace_get` reports the current session key, both meta key names, the live
session count, and the current workspace and permission.

### Client support (DSH)

The official `@deepseek-ai/dsh-mcp-client` sends no `_meta`, so every conversation stays
on the default session. Pair the server with `dlqq-mcp-server-dsh-client`, the DSH plugin
fork in this project, which writes on every `tools/call`:

```json
"_meta": { "dsh/sessionId": "<session id>", "dsh/agentId": "<agent id>" }
```

## Updating the JAR (hot reload)

### Standard flow (restart is mandatory)

1. Build the new JAR (`script\build_and_copy.bat`).
2. **Stop the HTTP server** and verify the port is free.
3. Copy the new JAR over the deployed one.
4. Restart the server.
5. Call `dlqq_get_mcp_version`: if `Build time (in code, real value)` equals `JAR manifest build time (on disk)` and both are the new value, the new code is loaded; if the in-code time is still old, the process was not restarted.
6. If tools were added/removed/renamed or schemas changed, **restart the client** too.

### Why

- **Class loading** – the JVM loads classes at startup; replacing the JAR on disk does not reload them. `BuildInfo.BUILD_TIME` is a compile-time constant (templating-maven-plugin), so the version tool reports the code actually running.
- **SSE** – restarting the server closes old TCP connections and clears its in-memory SSE client list; a new process cannot inherit old connections.
- **Client behavior** – with DSH (protocol 2025-11-25), after a server restart the client does not reconnect SSE and does not re-run `initialize`/`tools/list`; it only posts `tools/call`. The client keeps its old tool list until restarted.

### Scope and limitations

| Capability | Supported | Notes |
|-----------|-----------|-------|
| Load new code after a restart | ✅ | Stop server → replace JAR → restart |
| Replace the JAR without restarting | ❌ | JVM cannot reload loaded classes |
| Remove/restore tools at runtime | ✅ | Persisted; removed tools callable via `dlqq_mcp_run_tools` |
| Immediate client refresh after a tool change | ⚠️ only while SSE is alive | Client re-fetches `tools/list` ~2 ms after the push (measured) |
| Client refreshes automatically after a server restart | ❌ | Restart the client |
| New tools visible without restarting the client | ❌ | Client caches the tool list at connect time |

## Parameter conventions

- Parameters marked `*` are required; the rest are optional.
- Path parameters accept absolute paths or paths relative to the workspace; all paths are resolved through the workspace.
- Line numbers start at 1; `end_line = -1` means “to end of file”.
- `mode` in search/replace tools is `partial` (substring match) or `regex`.
- `format` in serial/BLE/USB/Wi-Fi tools is `text` or `hex`.
- Charset parameters default to UTF-8.
- File-writing tools automatically create missing parent directories and respect `.aiignore/.aiwriterules/.aireadrules`; whether writes outside the workspace are allowed depends on the workspace write permission (`workspace` rejects them, `approval` asks for confirmation, `full` allows them).

## Settings properties

All settings live in `<workspace>/.dlqq/settings/settings.properties` (the workspace defaults to the process CWD when neither `--workspace` nor a runtime workspace is set):

| Key | Default | Meaning |
|-----|---------|---------|
| `response.max.size` | 25804 | Max response bytes (~25 KB, max 258048) |
| `search.max.files` | 5000 | Max files scanned by `dlqq_search_text_in_dir` |
| `search.max.file.size` | 10485760 | Max size of a file to scan |
| `search.max.results` | 50 | Default `max_results` |
| `search.text.extensions` | (list) | Default text extensions |
| `script.*` | empty | Project script paths (build/rebuild/run/debug/chip/erase/download) |
| `mcp.tools.excluded` | empty | Removed (disabled) tool names, comma-separated |

> The write permission (`workspace.write.mode`) is stored in the same workspace settings file as every other configuration key.

## HTTP protocol & SSE

- `POST /mcp` – JSON-RPC requests (`initialize`, `tools/list`, `tools/call`, …).
- `GET /mcp` – health JSON; with `Accept: text/event-stream` it opens the SSE channel used for `notifications/tools/list_changed`.
- `DELETE /mcp` – returns 200 (session teardown, stateless server).
- CORS headers are returned for browser-based clients.
- The server is stateless and unauthenticated: bind it to loopback or protect it at the network layer.

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `Could not initialize class org.apache.pdfbox.io.IOUtils` | Trimmed JRE; use the full JRE (`ALL-MODULE-PATH`) |
| Port already in use | Stop the old server, or answer the prompt with another port |
| New tools not visible | Client caches `tools/list`; restart the client (see “Updating the JAR”) |
| Removed tools still callable | By design via `dlqq_mcp_run_tools`; use it to call hidden/removed tools |
| No `notifications/tools/list_changed` | No open SSE stream; reconnect the client |
| Script tools fail with “configure the path first” | Set the corresponding `dlqq_set_*_script` |

## Behavior details

### File & directory operations

- Write operations create missing parent directories.
- Delete operations (`dlqq_delete_file`, `dlqq_delete_dir`) open a JFrame confirmation showing the target; cancel returns “operation cancelled”.
- Batch replace (`dlqq_replace_text_in_dir`) returns a per-file report and respects the AI rule files.
- Line-based edits accept `end_line = -1` (end of file); out-of-range values are clamped.
- Text operations support `charset`; the default is UTF-8. Binary reads/writes use hex text.

### Search

- `mode=partial` matches substrings; `mode=regex` uses Java regex.
- `dlqq_search_text_in_dir` skips files larger than `search.max.file.size`, scans at most `search.max.files`, returns at most `search.max.results` (overridable with `max_results`), and includes 3 context lines around each hit.
- `file_extensions` overrides `search.text.extensions`; empty means “use the configured defaults”.

### Serial (UART)

1. `dlqq_run_uart(port)` opens the GUI for one instance.
2. `dlqq_uart_get_com` / `dlqq_uart_refresh_com` list ports.
3. `dlqq_uart_open_com(port, com, baud_rate)` starts capturing.
4. `dlqq_uart_send_text` / `dlqq_uart_send_hex` write data (also recorded in the buffer).
5. `dlqq_uart_get_log*` reads with three modes: range (`start_line`/`end_line`), start+count (`start_line`/`line_count`), or wait for new data (`wait_time`/`line_count`, `wait_time` ≤ 30 s).
6. `dlqq_uart_switch_text_hex` changes the display/return format; `dlqq_exit_uart` closes the instance.

### BLE

1. `.runtime\python` (bundled) provides the interpreter and `bleak`; `dlqq_ble_release_script` extracts `ble_tools.py` to `.dlqq/script/python`.
2. `dlqq_run_ble(port)` opens the GUI; `dlqq_ble_scan` then `dlqq_ble_get_devices`.
3. `dlqq_ble_connect(device_address)`, `dlqq_ble_discover`, then read/write/subscribe with service and characteristic UUIDs.
4. Logs are read with the same three modes as UART; `dlqq_ble_set_buffer_size` controls memory (default 5 MB).

### USB

1. `dlqq_run_usb(port)` opens the GUI; `dlqq_usb_scan` returns VID/PID/name.
2. `dlqq_usb_open(port, vid, pid, type)` opens a HID/CDC device.
3. `dlqq_usb_send_text` / `dlqq_usb_send_hex` write; `dlqq_usb_get_log*` read (text or hex).
4. `dlqq_exit_usb` closes the instance.

### Wi-Fi

1. `dlqq_run_wifi(port)` opens the GUI.
2. `dlqq_wifi_scan` / `dlqq_wifi_get_networks` list networks.
3. `dlqq_wifi_connect(ssid, password?)`, `dlqq_wifi_disconnect`, `dlqq_wifi_status`, `dlqq_wifi_profiles`.
4. `dlqq_wifi_get_log` / `dlqq_wifi_clear_log` manage the log buffer.

### Mouse & keyboard

- `dlqq_mouse_move` accepts absolute (`x`/`y`) or relative (`dx`/`dy`) movement.
- `dlqq_mouse_click` supports left/right/middle, single/double click, optional coordinates.
- `dlqq_keyboard_type` uses clipboard paste so any Unicode text works (focus must be in the target input).
- `dlqq_keyboard_hotkey` uses `+`-joined combos such as `ctrl+shift+s`.

### Screen & images

- `dlqq_capture_screen` saves to `output_path` or `.dlqq/cache`; the PNG/JPG includes the real mouse cursor.
- `dlqq_convert_image` writes to `output_path` or `.dlqq/cache`; SVG → raster is rendered by Batik, raster → SVG embeds the bitmap (not vectorized).

### Office & ZIP

- Office files are converted to Markdown/cache first; page/paragraph/sheet/slide ranges are supported.
- `dlqq_convert_office_file` targets DOCX/XLSX/PPTX/MD/TXT (no PDF output).
- `dlqq_zip` supports multiple sources, password protection and compression level 1–9; `dlqq_unzip` supports password-protected ZIP and extracts to `output_dir` or `.dlqq/cache`.

### Project scripts

- Every script tool maps to a `script.*` key; **no built-in defaults**.
- Unconfigured scripts return an error that names the matching `dlqq_set_*_script` tool.
- Scripts run with the workspace as working directory.

### MCP data & workspace

- Removed tools stay registered and can still be called through `dlqq_mcp_run_tools`.
- `dlqq_mcp_run_tools` rejects calling itself (recursion guard).
- Workspace changes affect relative paths immediately; `dlqq_workspace_set` persists, `reset` clears.
- `.dlqq` (logs, cache and settings) lives inside the workspace and follows it (workspace defaults to the process CWD).

## Tool reference

Parameter lists below: `name` = required, `[name]` = optional. Path parameters are resolved against the workspace.

### File tools (15)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_read_file` | `file_path`*, `[charset]`, `[start_line]`, `[end_line]`, `[read_lines]` | Read a line range; `start_line` + `read_lines` or `start_line` + `end_line` |
| `dlqq_create_file` | `file_path`*, `[content]`, `[charset]` | Creates/overwrites; creates parent directories |
| `dlqq_overwrite_file` | `file_path`*, `[content]`, `[charset]` | Always writes, creates parent directories |
| `dlqq_clear_file` | `file_path`*, `[charset]` | Empties the file but keeps it |
| `dlqq_append_file` | `file_path`*, `content`*, `[charset]` | Creates the file if missing |
| `dlqq_remove_lines_in_file` | `file_path`*, `start_line`*, `end_line`*, `[charset]` | `end_line = -1` means end of file |
| `dlqq_replace_lines_in_file` | `file_path`*, `start_line`*, `end_line`*, `content`*, `[charset]` | Multi-line replacement |
| `dlqq_insert_text_in_file` | `file_path`*, `line`*, `content`*, `[charset]` | Inserts before the given line |
| `dlqq_replace_text_in_file` | `file_path`*, `search_text`*, `replace_text`*, `[mode]`, `[charset]`, `[start_line]`, `[end_line]` | `mode`: text / regex; shows changed lines with ±5 context |
| `dlqq_replace_text_in_dir` | `dir_path`*, `search_text`*, `replace_text`*, `[mode]`, `[charset]`, `[file_extensions]`, `[max_files]` | Batch replace with report; respects AI rules |
| `dlqq_remove_text_in_file` | `file_path`*, `search_text`*, `[mode]`, `[charset]`, `[start_line]`, `[end_line]` | Replace matched text with empty string |
| `dlqq_delete_file` | `file_path`* | Confirmation dialog |
| `dlqq_copy_file` | `source_path`*, `target_path`* | Overwrites target; creates directories |
| `dlqq_move_file` | `source_path`*, `target_path`* | Overwrites target; creates directories |
| `dlqq_rename_file` | `source_path`*, `new_name`* | Renames within the same directory |

### Directory tools (8)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_mdir` | `dir_path`* | Parent must exist |
| `dlqq_mdirs` | `dir_path`* | Creates all parents |
| `dlqq_delete_dir` | `dir_path`* | Recursive, confirmation dialog |
| `dlqq_copy_dir` | `source_path`*, `target_path`* | Recursive copy |
| `dlqq_move_dir` | `source_path`*, `target_path`* | Existing target is deleted first |
| `dlqq_rename_dir` | `source_path`*, `new_name`* | Same directory |
| `dlqq_list_dir_tree` | `dir_path`*, `[show_hidden]`, `[max_depth]`, `[max_entries]` | Recursive tree |
| `dlqq_list_dir_files` | `dir_path`*, `[show_hidden]` | One level only |

### Binary & BOM tools (4)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_read_bin_file` | `file_path`*, `[start_byte]`, `[end_byte]`, `[read_bytes]` | Returns hex text |
| `dlqq_write_bin_file` | `file_path`*, `hex_content`*, `[append]` | Hex text → binary; spaces/newlines allowed |
| `dlqq_add_unicode_bom` | `path`*, `[is_directory]`, `[file_extensions]` | Recursive in directory mode; skips existing BOM |
| `dlqq_remove_unicode_bom` | `path`*, `[is_directory]`, `[file_extensions]` | Removes UTF-8 BOM |

### Search tools (4)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_search_file` | `search_path`*, `pattern`*, `[mode]` | `mode`: partial / regex |
| `dlqq_search_dir` | `search_path`*, `pattern`*, `[mode]` | Search directories |
| `dlqq_search_text_in_dir` | `search_path`*, `pattern`*, `[mode]`, `[ignore_case]`, `[file_extensions]`, `[max_results]` | Returns file, line number, text and ±3 context |
| `dlqq_search_text_in_file` | `file_path`*, `pattern`*, `[mode]`, `[ignore_case]`, `[max_results]` | Single file |

### Dialog tools (3)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_dialog_text` | `title`*, `message`* | Text input window |
| `dlqq_dialog_check` | `title`*, `message`* | Yes/No window |
| `dlqq_dialog_file_path` | `title`*, `[extensions]` | File picker |

### Serial (UART) tools (17)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_run_uart` | `port`* | Open the GUI; `port` is the local service port |
| `dlqq_uart_get_com` | `port`* | List serial ports |
| `dlqq_uart_refresh_com` | `port`* | Refresh the list |
| `dlqq_uart_open_com` | `port`*, `com`*, `[baud_rate]` | Default baud rate 1000000 |
| `dlqq_uart_reopen_com` | `port`*, `com`*, `[baud_rate]` | Change baud rate |
| `dlqq_uart_close_com` | `port`* | Close the channel |
| `dlqq_uart_send_text` | `port`*, `text`* | Also stored in the buffer |
| `dlqq_uart_send_hex` | `port`*, `hex_array`* | Each item is even-length hex |
| `dlqq_uart_get_log` | `port`*, `[start_line]`, `[end_line]`, `[line_count]`, `[wait_time]` | 3 read modes; format follows the text/hex switch |
| `dlqq_uart_get_log_text` | `port`*, `[start_line]`, `[end_line]`, `[line_count]`, `[wait_time]` | Force text |
| `dlqq_uart_get_log_hex` | `port`*, `[start_line]`, `[end_line]`, `[line_count]`, `[wait_time]` | Force hex |
| `dlqq_uart_clear_text` | `port`* | Clear buffer |
| `dlqq_uart_get_line_count` | `port`* | Buffer line count |
| `dlqq_uart_set_text_size` | `port`*, `size_bytes`* | Buffer size, default 5 MB |
| `dlqq_uart_switch_text_hex` | `port`*, `format`* | text / hex |
| `dlqq_exit_uart` | `port`* | Close window and process |
| `dlqq_rerun_uart` | `port`* | Restart the tool |

### BLE script tools

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_ble_release_script` | none | Release `ble_tools.py` into `.dlqq/script/python` |

### BLE tools (20)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_run_ble` | `port`* | Open the BLE GUI |
| `dlqq_ble_scan` | `port`*, `[timeout]` | Scan nearby devices |
| `dlqq_ble_get_devices` | `port`* | Devices from the last scan |
| `dlqq_ble_connect` | `port`*, `device_address`* | Connect |
| `dlqq_ble_disconnect` | `port`* | Disconnect |
| `dlqq_ble_discover` | `port`* | Discover services/characteristics |
| `dlqq_ble_read` | `port`*, `service_uuid`*, `char_uuid`* | Read characteristic |
| `dlqq_ble_write_text` | `port`*, `service_uuid`*, `char_uuid`*, `text`* | Write text |
| `dlqq_ble_write_hex` | `port`*, `service_uuid`*, `char_uuid`*, `hex_array`* | Write hex |
| `dlqq_ble_subscribe` | `port`*, `service_uuid`*, `char_uuid`*, `enable`* | Subscribe/unsubscribe notifications |
| `dlqq_ble_get_log` | `port`*, `[start_line]`, `[end_line]`, `[line_count]`, `[wait_time]` | 3 read modes |
| `dlqq_ble_get_log_text` | `port`*, same as above | Force text |
| `dlqq_ble_get_log_hex` | `port`*, same as above | Force hex |
| `dlqq_ble_clear` | `port`* | Clear buffer |
| `dlqq_ble_get_line_count` | `port`* | Buffer line count |
| `dlqq_ble_set_buffer_size` | `port`*, `size_bytes`* | Default 5 MB |
| `dlqq_ble_switch_text_hex` | `port`*, `format`* | text / hex |
| `dlqq_exit_ble` | `port`* | Close window and process |
| `dlqq_rerun_ble` | `port`* | Restart the tool |
| `dlqq_check_bluetooth_open` | none | Check the Windows Bluetooth adapter |

### USB tools (15)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_run_usb` | `port`* | Open the USB GUI |
| `dlqq_usb_scan` | `port`* | Scan HID/CDC devices |
| `dlqq_usb_get_devices` | `port`* | Last scan results |
| `dlqq_usb_open` | `port`*, `vid`*, `pid`*, `[type]` | Open a device |
| `dlqq_usb_close` | `port`* | Close |
| `dlqq_usb_send_text` | `port`*, `text`* | UTF-8 text |
| `dlqq_usb_send_hex` | `port`*, `hex_array`* | Hex data |
| `dlqq_usb_get_log` | `port`*, `[start_line]`, `[end_line]` | Format follows the switch |
| `dlqq_usb_get_log_text` | `port`*, `[start_line]`, `[line_count]` | Force text |
| `dlqq_usb_get_log_hex` | `port`*, `[start_line]`, `[line_count]` | Force hex |
| `dlqq_usb_clear` | `port`* | Clear buffer |
| `dlqq_usb_get_line_count` | `port`* | Buffer line count |
| `dlqq_usb_switch_text_hex` | `port`* | text / hex |
| `dlqq_exit_usb` | `port`* | Close window and process |
| `dlqq_rerun_usb` | `port`* | Restart the tool |

### Wi-Fi tools (12)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_run_wifi` | `port`* | Open the Wi-Fi GUI |
| `dlqq_wifi_scan` | `port`*, `[timeout]` | Scan networks |
| `dlqq_wifi_get_networks` | `port`* | Last scan results |
| `dlqq_wifi_connect` | `port`*, `ssid`*, `[password]`, `[timeout]` | WPA/WPA2-PSK or open |
| `dlqq_wifi_disconnect` | `port`* | Disconnect |
| `dlqq_wifi_status` | `port`* | Connection state/SSID/signal |
| `dlqq_wifi_profiles` | `port`* | Saved profiles |
| `dlqq_wifi_get_log` | `port`*, `[start_line]`, `[end_line]` | Log buffer |
| `dlqq_wifi_clear_log` | `port`* | Clear log |
| `dlqq_wifi_get_line_count` | `port`* | Log line count |
| `dlqq_exit_wifi` | `port`* | Close window |
| `dlqq_rerun_wifi` | `port`* | Restart the tool |

### Mouse tools (7)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_mouse_move` | `[x]`, `[y]`, `[dx]`, `[dy]` | Absolute or relative |
| `dlqq_mouse_click` | `[button]`, `[x]`, `[y]`, `[clicks]`, `[interval]` | left/right/middle |
| `dlqq_mouse_drag` | `start_x`*, `end_x`*, `end_y`*, `[start_y]`, `[button]`, `[duration]`, `[steps]` | Press → move → release |
| `dlqq_mouse_scroll` | `[amount]`, `[direction]` | Positive scrolls up |
| `dlqq_mouse_get_position` | none | Cursor position + screen size |
| `dlqq_mouse_press` | `[button]`, `[x]`, `[y]` | Press and hold |
| `dlqq_mouse_release` | `[button]` | Release |

### Keyboard tools (6)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_keyboard_type` | `text`*, `[interval]` | Types any text via clipboard paste |
| `dlqq_keyboard_tap` | `key`*, `[times]`, `[interval]` | Press + release |
| `dlqq_keyboard_press` | `key`* | Hold |
| `dlqq_keyboard_release` | `key`* | Release |
| `dlqq_keyboard_hotkey` | `combo`*, `[interval]` | e.g. `ctrl+c` |
| `dlqq_keyboard_key_list` | none | Supported key names |

### Screen & image tools (2)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_capture_screen` | `[x]`, `[y]`, `[width]`, `[height]`, `[format]`, `[output_path]` | Full screen or region; draws the real cursor |
| `dlqq_convert_image` | `source_path`*, `target_format`*, `[output_path]`, `[quality]` | SVG/PNG/JPG/GIF/BMP; raster→SVG embeds the bitmap |

### Time & calculator tools (4)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_get_current_time` | `[timezone]`, `[format]` | Date/time, timestamp, weekday, zone |
| `dlqq_calculator_math` | `expression`* | Precise math: powers, logs, trig, factorial |
| `dlqq_calculator_programmer` | `expression`*, `[type]` | Bitwise, shifts, base conversion |
| `dlqq_calculator_function` | `expression`*, `[variables]` | Custom functions with variables |

### Command tools (3)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_run_command` | `command`*, `[working_dir]`, `[timeout]`, `[no_wait]` | Runs through `cmd /c` (or `sh -c`); JFrame confirmation before execution; `no_wait=true` starts it in the background and returns the PID and log path immediately |
| `dlqq_run_powershell` | `command`*, `[working_dir]`, `[timeout]`, `[no_wait]` | Runs through `powershell.exe` (Windows PowerShell 5.1); JFrame confirmation; same parameters as `dlqq_run_command` |
| `dlqq_run_pwsh` | `command`*, `[working_dir]`, `[timeout]`, `[no_wait]` | Runs through `pwsh.exe` (PowerShell 7+); JFrame confirmation; same parameters as `dlqq_run_command` |

### Office tools (2)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_read_office_file` | `file_path`*, `[start_page]`, `[end_page]` | PDF/DOC/DOCX/XLS/XLSX/PPT/PPTX → Markdown in `.dlqq/cache` |
| `dlqq_convert_office_file` | `source_path`*, `target_format`*, `[output_path]`, `[start_page]`, `[end_page]` | Target: DOCX/XLSX/PPTX/MD/TXT; default output `.dlqq/cache` |

### ZIP tools (2)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_zip` | `source_paths`*, `[output_path]`, `[password]`, `[compression_level]`, `[format]` | `zip` (multi-source, password, level 1–9) or `zstd` (single file → `.zst`, dir/multi → `.tar.zst`); default output `.dlqq/cache` |
| `dlqq_unzip` | `zip_path`*, `[output_dir]`, `[password]`, `[detect_mode]` | Extracts zip/7z/tar/gz/bz2/xz/zstd/…; `detect_mode`: 0=strict suffix, 1=suffix then magic, 2=magic only |

### MCP data, properties & workspace tools (16)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_get_mcp_version` | none | Version, compiled build time, manifest time, runtime info, workspace |
| `dlqq_test_hot_reload` | none | Returns the hard-coded hot-reload marker |
| `dlqq_mcp_remove_tools` | `tool_names`* | Remove and persist |
| `dlqq_mcp_refresh_tools` | none | Push `tools/list_changed` only |
| `dlqq_mcp_reset_tools` | none | Restore all removed tools |
| `dlqq_mcp_run_tools` | `name`*, `[arguments]` | Direct call, bypasses the removed/not-loaded restriction |
| `dlqq_workspace_set` | `path`* | Set and persist the workspace |
| `dlqq_workspace_get` | none | Show workspace and source |
| `dlqq_workspace_reset` | none | Clear persisted workspace |
| `dlqq_workspace_permission_set` | `mode`* | Set workspace write permission (readonly/workspace/approval/full), persisted in the workspace |
| `dlqq_clear_mcp_data` | none | Delete `.dlqq` |
| `dlqq_clear_mcp_cache` | none | Delete `.dlqq/cache` |
| `dlqq_clear_mcp_log` | none | Delete `.dlqq/log` files |
| `dlqq_get_mcp_properties` | `[key]` | Read settings |
| `dlqq_set_mcp_properties` | `[key]`, `[value]` | Write settings (e.g. `response.max.size`) |
| `dlqq_reset_mcp_properties` | `[key]` | Reset to defaults |

### Project script tools (13)

Build/run/debug scripts are configured via `settings.properties` (`script.*`). **No built-in default paths**: when unset they return an error telling you to configure the path first.

| Tool | Script key |
|------|------------|
| `dlqq_build_project` | `script.build` |
| `dlqq_rebuild_project` | `script.rebuild` |
| `dlqq_build_debug_project` | `script.build.debug` |
| `dlqq_rebuild_debug_project` | `script.rebuild.debug` |
| `dlqq_build_erase_download` | `script.build.erase.download` |
| `dlqq_rebuild_erase_download` | `script.rebuild.erase.download` |
| `dlqq_build_run_project` | `script.build.run` |
| `dlqq_rebuild_run_project` | `script.rebuild.run` |
| `dlqq_chip_download` | `script.chip.download` |
| `dlqq_chip_erase` | `script.chip.erase` |
| `dlqq_erase_download` | `script.erase.download` |
| `dlqq_run_project` | `script.run` |
| `dlqq_debug_project` | `script.debug` |

### Script path setters (13)

Each takes `script_path*` and writes the corresponding `script.*` property: `dlqq_set_build_script`, `dlqq_set_rebuild_script`, `dlqq_set_debug_script`, `dlqq_set_run_script`, `dlqq_set_chip_download_script`, `dlqq_set_chip_erase_script`, `dlqq_set_build_debug_script`, `dlqq_set_rebuild_debug_script`, `dlqq_set_build_erase_download_script`, `dlqq_set_rebuild_erase_download_script`, `dlqq_set_build_run_script`, `dlqq_set_rebuild_run_script`, `dlqq_set_erase_download_script`.

### Tool list (1)

| Tool | Parameters | Notes |
|------|------------|-------|
| `dlqq_get_tool_list` | `[filter]` | Names and descriptions of registered tools |

## Usage examples

Read a file:

```json
{ "name": "dlqq_read_file", "arguments": { "file_path": "src/App.java", "start_line": 1, "read_lines": 50 } }
```

Search text with regex:

```json
{ "name": "dlqq_search_text_in_dir", "arguments": { "search_path": "src", "pattern": "TODO|FIXME", "mode": "regex", "ignore_case": true } }
```

Switch the workspace at runtime:

```json
{ "name": "dlqq_workspace_set", "arguments": { "path": "E:\\work\\my-project" } }
```

Call a removed tool:

```json
{ "name": "dlqq_mcp_run_tools", "arguments": { "name": "dlqq_capture_screen", "arguments": { "format": "png" } } }
```

## Tech stack

- Java 25, Maven, shaded JAR
- MCP JSON-RPC 2.0 over stdio and Streamable HTTP (`com.sun.net.httpserver`)
- Apache PDFBox / POI, Batik, zip4j, commons-compress, usb4java, JNA, Gson, SLF4J
- Python 3.10+ (build time only) to generate `.runtime\python`; runtime uses the bundled `.runtime\python\`

## Project structure

```text
src/main/java/com/donglouqiaoque/dlqqmcpserver/
├── mcp/
│   ├── MCPServerMain.java        # entry point (logging/UTF-8 init, args, health check)
│   ├── protocol/                 # JsonRpcMessage, McpSchema
│   ├── server/McpServer.java     # dispatch + tool registry + dynamic tools
│   ├── transport/                # McpTransport, StdioTransport, HttpTransport (SSE)
│   └── tools/                    # AITools + all tool implementations
└── utils/                        # FileUtils, AIRulesUtils, CalculatorUtils, Workspace
src/main/resources/
├── py/ble_tools.py               # BLE GUI (Python + bleak)
├── image/logo.ico                # window icon (ICO)
├── image/logo.png                # window icon (PNG)
└── simplelogger.properties
```

Runtime directories (created on first start, git-ignored): `.dlqq/log/{logger,stderr}`, `.dlqq/cache`; settings live in `<workspace>/.dlqq/settings/settings.properties` (workspace defaults to the process CWD).

## AI rule files

| File | Purpose |
|------|---------|
| `.aiignore` | Files/directories the AI should ignore |
| `.aiwriterules` | Files/directories the AI must not modify |
| `.aireadrules` | Files/directories the AI must not read |

Gitignore-style syntax (`*.log`, `build/`, `/config.txt`, `!important.log`, `**/*.tmp`). Rules are resolved against the workspace; outside the workspace a JFrame asks for confirmation; single-file operations are not restricted.

## BOM tools in detail

- `dlqq_add_unicode_bom` — adds a UTF-8 BOM (`EF BB BF`) to files; skips files that already have one; creates the file if missing. In directory mode it recurses and filters by `file_extensions`.
- `dlqq_remove_unicode_bom` — removes the BOM when present; directory mode is recursive.
- Both respect `.aiignore/.aiwriterules/.aireadrules`.

## Known limitations

- **Full JRE required** for PDF/TLS/zipfs; trimmed `jlink` images can fail with `Could not initialize class org.apache.pdfbox.io.IOUtils`.
- **No PDF writing** – `dlqq_convert_office_file` does not target PDF; convert to DOCX and export externally.
- **Font cache** – PDFBox writes `~/.pdfbox.cache`; if unwritable it is rebuilt at each start (1–3 s). Override with `-Dpdfbox.fontcache=<writable dir>`.
- **Hardware tools** (serial/BLE/USB) need real hardware for end-to-end verification.
- **GUI tools** (serial, BLE, Wi-Fi, command confirmation) need a desktop session; unavailable headless.
- **Hot reload** – the JVM cannot reload classes; a JAR update needs a server restart, and tool changes also need a client restart (see “Updating the JAR”).

## Tool risk levels and visibility

### 1. Risk levels

**Device resources** (files, directories, processes, permissions)

| Level | Description |
|---|---|
| Safe | The tool does not affect any device resource. |
| Risky | The tool affects device resources, but the impact is controllable and easy to recover. |
| High-risk | The tool affects device resources, and the impact is unpredictable or hard to recover. |

**Device peripherals** (mouse, keyboard, screen, Bluetooth, Wi-Fi, USB)

| Level | Description |
|---|---|
| Safe | The tool does not affect any device peripheral. |
| Risky | The tool affects device peripherals, but the impact is controllable and easy to recover. |
| High-risk | The tool affects device peripherals, and the impact is unpredictable or hard to recover. |

### 2. AI permission levels

**Resources**

| AI permission level | Safe tools | Risky tools | High-risk tools |
|---|---|---|---|
| Chat only | Denied | Denied | Denied |
| Read-only | Allowed | Denied | Denied |
| Modify inside workspace | Allowed | Allowed inside workspace, denied outside | Denied |
| Approve outside workspace (default) | Allowed | Allowed inside, approval outside | Approval required inside and outside |
| Approve high-risk tools | Allowed | Allowed | Approval required inside and outside |
| Full access | Allowed | Allowed | Allowed |
| Custom | Per config file | Per config file | Per config file |

**Peripherals**

| AI permission level | Safe tools | Risky tools | High-risk tools |
|---|---|---|---|
| Chat only | Denied | Denied | Denied |
| Read-only | Allowed | Denied | Denied |
| Approval required (default) | Allowed | Approval required | Approval required |
| Approve high-risk tools | Allowed | Allowed | Approval required |
| Full access | Allowed | Allowed | Allowed |
| Custom | Per config file | Per config file | Per config file |

**Resource areas**

| Area | Path | Description | Access |
|---|---|---|---|
| Cache | `<workspace>/.dlqq/cache` | Temporary tool data | Read/write allowed |
| Data | `<workspace>/.dlqq/data` | Long-term tool data | Read/write allowed |
| External | Any directory | User-specified resource directory | Follows device resource permission |

### 3. Visibility

| State | Description |
|---|---|
| Full access | The AI agent can call the tool directly. |
| View only | The AI agent can read the description, but any call is rejected. |
| Hidden | The AI agent does not know the tool exists; it is not exposed by default. |
| Source-masked | The source code is commented out; only a code change can enable it. |

### 4. Tool inventory

#### 4.1 File operations (19)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_read_file | Safe | Safe | Full access |
| dlqq_read_bin_file | Safe | Safe | Full access |
| dlqq_create_file | Risky | Safe | Full access |
| dlqq_overwrite_file | High-risk | Safe | Full access |
| dlqq_append_file | Risky | Safe | Full access |
| dlqq_clear_file | High-risk | Safe | Full access |
| dlqq_insert_text_in_file | Risky | Safe | Hidden |
| dlqq_replace_text_in_file | Risky | Safe | Full access |
| dlqq_replace_text_in_dir | High-risk | Safe | Full access |
| dlqq_replace_lines_in_file | Risky | Safe | Hidden |
| dlqq_remove_lines_in_file | Risky | Safe | Hidden |
| dlqq_remove_text_in_file | Risky | Safe | Hidden |
| dlqq_copy_file | Risky | Safe | Full access |
| dlqq_move_file | High-risk | Safe | Full access |
| dlqq_rename_file | Risky | Safe | Full access |
| dlqq_delete_file | High-risk | Safe | Full access |
| dlqq_write_bin_file | Risky | Safe | Full access |
| dlqq_add_unicode_bom | Risky | Safe | Full access |
| dlqq_remove_unicode_bom | Risky | Safe | Full access |

#### 4.2 Directory operations (8)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_list_dir_files | Safe | Safe | Full access |
| dlqq_list_dir_tree | Safe | Safe | Full access |
| dlqq_mdir | Risky | Safe | Full access |
| dlqq_mdirs | Risky | Safe | Full access |
| dlqq_copy_dir | Risky | Safe | Full access |
| dlqq_move_dir | High-risk | Safe | Full access |
| dlqq_rename_dir | Risky | Safe | Full access |
| dlqq_delete_dir | High-risk | Safe | Full access |

#### 4.3 Search (4)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_search_file | Safe | Safe | Full access |
| dlqq_search_dir | Safe | Safe | Full access |
| dlqq_search_text_in_file | Safe | Safe | Full access |
| dlqq_search_text_in_dir | Safe | Safe | Full access |

#### 4.4 Archive (2)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_zip | High-risk | Safe | Full access |
| dlqq_unzip | High-risk | Safe | Full access |

#### 4.5 Commands and background programs (18)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_run_command | High-risk | Safe | Full access |
| dlqq_run_powershell | High-risk | Safe | Full access |
| dlqq_run_pwsh | High-risk | Safe | Full access |
| dlqq_get_command_list | Safe | Safe | Full access |
| dlqq_get_command_log | Safe | Safe | Full access |
| dlqq_kill_command | High-risk | Safe | Full access |
| dlqq_clear_command_list | Risky | Safe | Full access |
| dlqq_refresh_command_list | Safe | Safe | Full access |
| dlqq_get_powershell_list | Safe | Safe | Full access |
| dlqq_get_powershell_log | Safe | Safe | Full access |
| dlqq_kill_powershell | High-risk | Safe | Full access |
| dlqq_clear_powershell_list | Risky | Safe | Full access |
| dlqq_refresh_powershell_list | Safe | Safe | Full access |
| dlqq_get_pwsh_list | Safe | Safe | Full access |
| dlqq_get_pwsh_log | Safe | Safe | Full access |
| dlqq_kill_pwsh | High-risk | Safe | Full access |
| dlqq_clear_pwsh_list | Risky | Safe | Full access |
| dlqq_refresh_pwsh_list | Safe | Safe | Full access |

#### 4.6 Serial UART (17)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_run_uart | Safe | Risky | Full access |
| dlqq_rerun_uart | Safe | Risky | Full access |
| dlqq_exit_uart | Safe | Risky | Full access |
| dlqq_uart_open_com | Safe | Risky | Full access |
| dlqq_uart_close_com | Safe | Risky | Full access |
| dlqq_uart_reopen_com | Safe | Risky | Full access |
| dlqq_uart_refresh_com | Safe | Safe | Full access |
| dlqq_uart_get_com | Safe | Safe | Full access |
| dlqq_uart_send_text | Safe | Risky | Full access |
| dlqq_uart_send_hex | Safe | Risky | Full access |
| dlqq_uart_get_log | Safe | Safe | Full access |
| dlqq_uart_get_log_text | Safe | Safe | Full access |
| dlqq_uart_get_log_hex | Safe | Safe | Full access |
| dlqq_uart_switch_text_hex | Safe | Safe | Full access |
| dlqq_uart_clear_text | Safe | Safe | Full access |
| dlqq_uart_get_line_count | Safe | Safe | Full access |
| dlqq_uart_set_text_size | Safe | Safe | Full access |

#### 4.7 Bluetooth BLE (21)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_run_ble | Safe | Risky | Full access |
| dlqq_rerun_ble | Safe | Risky | Full access |
| dlqq_exit_ble | Safe | Risky | Full access |
| dlqq_ble_scan | Safe | Risky | Full access |
| dlqq_ble_get_devices | Safe | Safe | Full access |
| dlqq_ble_connect | Safe | Risky | Full access |
| dlqq_ble_disconnect | Safe | Risky | Full access |
| dlqq_ble_discover | Safe | Risky | Full access |
| dlqq_ble_read | Safe | Safe | Full access |
| dlqq_ble_write_text | Safe | Risky | Full access |
| dlqq_ble_write_hex | Safe | Risky | Full access |
| dlqq_ble_subscribe | Safe | Risky | Full access |
| dlqq_ble_get_log | Safe | Safe | Full access |
| dlqq_ble_get_log_text | Safe | Safe | Full access |
| dlqq_ble_get_log_hex | Safe | Safe | Full access |
| dlqq_ble_switch_text_hex | Safe | Safe | Full access |
| dlqq_ble_clear | Safe | Safe | Full access |
| dlqq_ble_get_line_count | Safe | Safe | Full access |
| dlqq_ble_set_buffer_size | Safe | Safe | Full access |
| dlqq_ble_release_script | Risky | Safe | Full access |
| dlqq_check_bluetooth_open | Safe | Safe | Full access |

#### 4.8 USB (15)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_run_usb | Safe | Risky | Full access |
| dlqq_rerun_usb | Safe | Risky | Full access |
| dlqq_exit_usb | Safe | Risky | Full access |
| dlqq_usb_scan | Safe | Risky | Full access |
| dlqq_usb_get_devices | Safe | Safe | Full access |
| dlqq_usb_open | Safe | Risky | Full access |
| dlqq_usb_close | Safe | Risky | Full access |
| dlqq_usb_send_text | Safe | Risky | Full access |
| dlqq_usb_send_hex | Safe | Risky | Full access |
| dlqq_usb_get_log | Safe | Safe | Full access |
| dlqq_usb_get_log_text | Safe | Safe | Full access |
| dlqq_usb_get_log_hex | Safe | Safe | Full access |
| dlqq_usb_switch_text_hex | Safe | Safe | Full access |
| dlqq_usb_clear | Safe | Safe | Full access |
| dlqq_usb_get_line_count | Safe | Safe | Full access |

#### 4.9 Wi-Fi (12)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_run_wifi | Safe | Risky | Full access |
| dlqq_rerun_wifi | Safe | Risky | Full access |
| dlqq_exit_wifi | Safe | Risky | Full access |
| dlqq_wifi_scan | Safe | Risky | Full access |
| dlqq_wifi_get_networks | Safe | Safe | Full access |
| dlqq_wifi_connect | Safe | Risky | Full access |
| dlqq_wifi_disconnect | Safe | Risky | Full access |
| dlqq_wifi_status | Safe | Safe | Full access |
| dlqq_wifi_profiles | Safe | Safe | Full access |
| dlqq_wifi_get_log | Safe | Safe | Full access |
| dlqq_wifi_clear_log | Safe | Safe | Full access |
| dlqq_wifi_get_line_count | Safe | Safe | Full access |

#### 4.10 Mouse / keyboard / screen (14)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_mouse_move | Safe | Risky | Full access |
| dlqq_mouse_click | Safe | Risky | Full access |
| dlqq_mouse_press | Safe | Risky | Full access |
| dlqq_mouse_release | Safe | Risky | Full access |
| dlqq_mouse_drag | Safe | Risky | Full access |
| dlqq_mouse_scroll | Safe | Risky | Full access |
| dlqq_mouse_get_position | Safe | Safe | Full access |
| dlqq_keyboard_type | Safe | Risky | Full access |
| dlqq_keyboard_tap | Safe | Risky | Full access |
| dlqq_keyboard_press | Safe | Risky | Full access |
| dlqq_keyboard_release | Safe | Risky | Full access |
| dlqq_keyboard_hotkey | Safe | Risky | Full access |
| dlqq_keyboard_key_list | Safe | Safe | Full access |
| dlqq_capture_screen | Risky | Risky | Full access |

#### 4.11 Dialogs (3)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_dialog_text | Safe | Safe | Full access |
| dlqq_dialog_check | Safe | Safe | Full access |
| dlqq_dialog_file_path | Safe | Safe | Full access |

#### 4.12 Image / Office conversion (3)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_convert_image | Risky | Safe | Full access |
| dlqq_read_office_file | Risky | Safe | Full access |
| dlqq_convert_office_file | Risky | Safe | Full access |

#### 4.13 Time and calculators (4)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_get_current_time | Safe | Safe | Full access |
| dlqq_calculator_math | Safe | Safe | Full access |
| dlqq_calculator_programmer | Safe | Safe | Full access |
| dlqq_calculator_function | Safe | Safe | Full access |

#### 4.14 MCP management and introspection (14)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_get_mcp_version | Safe | Safe | Full access |
| dlqq_get_mcp_properties | Safe | Safe | Full access |
| dlqq_set_mcp_properties | Risky | Safe | Full access |
| dlqq_reset_mcp_properties | Risky | Safe | Full access |
| dlqq_mcp_hide_tools | High-risk | Safe | Full access |
| dlqq_mcp_show_tools | High-risk | Safe | Full access |
| dlqq_mcp_refresh_tools | Safe | Safe | Full access |
| dlqq_mcp_reset_tools | High-risk | Safe | Full access |
| dlqq_mcp_run_tools | High-risk | Safe | Full access |
| dlqq_clear_mcp_cache | Risky | Safe | Full access |
| dlqq_clear_mcp_data | High-risk | Safe | Full access |
| dlqq_clear_mcp_log | Risky | Safe | Full access |
| dlqq_test_hot_reload | Safe | Safe | Source-masked |
| dlqq_get_tool_list | Safe | Safe | Full access |
| dlqq_get_tools_env | Safe | Safe | Full access |

#### 4.15 Workspace (5)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_workspace_set | High-risk | Safe | Full access |
| dlqq_workspace_get | Safe | Safe | Full access |
| dlqq_workspace_reset | High-risk | Safe | Full access |
| dlqq_workspace_permission_set | High-risk | Safe | Full access |
| dlqq_get_cwd_path | Safe | Safe | Full access |

#### 4.16 Project scripts (execution, 13)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_build_project | High-risk | Safe | Source-masked |
| dlqq_rebuild_project | High-risk | Safe | Source-masked |
| dlqq_run_project | High-risk | Safe | Source-masked |
| dlqq_debug_project | High-risk | Safe | Source-masked |
| dlqq_chip_erase | High-risk | Safe | Source-masked |
| dlqq_chip_download | High-risk | Safe | Source-masked |
| dlqq_erase_download | High-risk | Safe | Source-masked |
| dlqq_build_run_project | High-risk | Safe | Source-masked |
| dlqq_build_debug_project | High-risk | Safe | Source-masked |
| dlqq_build_erase_download | High-risk | Safe | Source-masked |
| dlqq_rebuild_run_project | High-risk | Safe | Source-masked |
| dlqq_rebuild_debug_project | High-risk | Safe | Source-masked |
| dlqq_rebuild_erase_download | High-risk | Safe | Source-masked |

#### 4.17 Project scripts (path settings, 13)

| Tool | Resource risk | Peripheral risk | Default visibility |
|---|---|---|---|
| dlqq_set_build_script | Risky | Safe | Source-masked |
| dlqq_set_rebuild_script | Risky | Safe | Source-masked |
| dlqq_set_run_script | Risky | Safe | Source-masked |
| dlqq_set_debug_script | Risky | Safe | Source-masked |
| dlqq_set_chip_erase_script | Risky | Safe | Source-masked |
| dlqq_set_chip_download_script | Risky | Safe | Source-masked |
| dlqq_set_erase_download_script | Risky | Safe | Source-masked |
| dlqq_set_build_run_script | Risky | Safe | Source-masked |
| dlqq_set_build_debug_script | Risky | Safe | Source-masked |
| dlqq_set_build_erase_download_script | Risky | Safe | Source-masked |
| dlqq_set_rebuild_run_script | Risky | Safe | Source-masked |
| dlqq_set_rebuild_debug_script | Risky | Safe | Source-masked |
| dlqq_set_rebuild_erase_download_script | Risky | Safe | Source-masked |

## License

MIT License. See [LICENSE](LICENSE).

