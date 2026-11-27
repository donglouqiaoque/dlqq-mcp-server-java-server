# AGENTS.en.md — dlqq-mcp-server Engineering Conventions

> This document is for AI assistants (Qwen Code / DeepSeek Harness, etc.). It contains hard rules, build and release instructions, code structure and a tool overview.
> The full tool manual is [README.en.md](README.en.md) (Chinese: [README.md](README.md)). These documents are the **only** documentation.
> Chinese version of the rules: [AGENTS.md](AGENTS.md).

## 1. Hard rules (mandatory)

1. **Language and build**: Java 25 + Maven.
2. **Project role**: an MCP server (stdio transport, JSON-RPC 2.0) for AI agent clients (DeepSeek Harness, Qwen Code, Claude Code, Cursor, Cline, ...). Entry point: `com.donglouqiaoque.dlqqmcpserver.mcp.MCPServerMain`.
3. **Script location**: build scripts live in `script\` (`build.bat`, `build_and_copy.bat`); runtime and launcher scripts live in `.runtime\` (`make_python_env.bat`, `make_python_env.py`, `start-http.bat`); temporary scripts go to `.ai_res\` (see `AIRES.md`). Do not put `.bat/.ps1/.sh` anywhere else.
4. **`.bat` files must be English-only**: a `.bat` file may contain **only English/ASCII characters** — no Chinese at all (comments, `echo` text, prompts and variable descriptions must all be English).
   Reason: the cmd default code page (GBK/936) mixed with UTF-8 garbles Chinese and can even break batch parsing.
5. **Artifact name**: the JAR is always **`dlqq_mcp_server.jar`**.
6. **Always build after code changes**: repackage after every code change and replace the JAR in the release directory as described below.
7. **Single artifact**: the JAR is always named `dlqq_mcp_server.jar`; `build_and_copy.bat` syncs it to `.runtime\`.
8. **Manual release storage**: `.release\` is only where the user stores zipped release packages; no script handles it.
9. **Tool changes must update the docs**: after adding, removing or changing an MCP tool, update [README.en.md](README.en.md) and the tool overview in this file.
10. **Diagnostic logs**: `StdioTransport`, `McpServer` and `MCPServerMain` write detailed connection diagnostics to `<jar dir>/.dlqq/log/<stdio|http>/<startup time>.log` (a new file per start; the timestamp in the file name is **millisecond-precise**, `yyyy-MM-dd_HH-mm-ss-SSS`; logger and stderr merged).
11. **Temporary files**: temporary files and intermediate output **must not go to the system temp directory on drive C**. Put them under the local temporary working directory inside the project root (see `AIRES.md`), grouped by type, and clean them up when done.
12. **No scripted bulk code edits**: PowerShell / Python / sed / awk and similar tools must not be used to bulk search-replace or rewrite source code. Every code change must be made file by file and location by location with the editing tool; if a large refactor is really needed, list the changes for user approval first, then apply them one by one.
13. **No git commands whatsoever (except the read-only check before writing a commit message)**: including but not limited to `git status`, `git diff`, `git log`, `git checkout/restore/reset`, `git add/commit/push/pull/stash`. Version control is handled by the user only; the AI must never run any git command (not even for reading or rollback). **The single exception**: when the user asks for a git commit message, the AI must first run one read-only git command (`git status --short --untracked-files=all`, `git diff --stat`, `git diff --name-status`, etc.) to read the current diff before writing the message — see rule 29. No write operation or rollback is allowed under this exception.
14. **No fully-qualified class names**: code must not contain `new java.xxx.XXX()` or `java.util.List` style references. **Anything that can be imported must be imported at the top of the file and used by its short name**; only when importing is impossible (name clash between same-named types from different packages, a static import, etc.) may a fully-qualified name be used, with a comment explaining why. Class names in comments and documentation are not code, but package prefixes are still discouraged.
15. **README and AGENTS must stay in sync across languages**: editing [README.md](README.md) (Chinese) requires the same edit in [README.en.md](README.en.md) (English) and vice versa; editing [AGENTS.md](AGENTS.md) requires the same edit in [AGENTS.en.md](AGENTS.en.md). Sections, tool counts, tool descriptions and configuration examples must match in every language; changing only one file is not allowed.
16. **Logs must be UTF-8 and English-only**: files under `.dlqq/log/` must be written in **UTF-8**, and their content must be **English only**. No Chinese anywhere, including `logger.info/warn/error/debug` messages and exception notes. New or modified log lines must be written in English.
17. **Config files must be UTF-8 with English-only comments**: `settings.properties` and similar files must be read and written as **UTF-8**, and comments inside them must be **English only** — no Chinese comments. Values that contain Chinese (such as script paths) stay as-is in UTF-8, without escaping.
18. **The AI must not execute scripts with side effects**: scripts that download, install, delete, unpack, bulk-clean, flash/download firmware, or that modify files, take a long time or require administrator rights **must never be run by the AI**. Hand the complete command to the user to run manually. The AI may only perform read-only checks (network connectivity, path existence, reading file contents), and must never confirm a dialog on the user's behalf or bypass a permission prompt.
19. **Test scripts through their real entry point**: to verify that a `.bat/.ps1` works, actually run that `.bat/.ps1` the way a user would. **Never run the `.py`** (or any other implementation it calls) directly to bypass the entry point; doing so misses argument parsing, path building and `pause` behaviour.
20. **`.bat/.ps1` must pause; AI calls pass `-ai`**: every batch/script must `pause` before exiting (both on success and on failure) so a user who double-clicks it can read the error output. AI/MCP tool calls must pass the **`-ai`** argument (scripts recognise at least `ai`, `-ai`, `--ai` and `/ai`, case-insensitively); on receiving it a script adjusts its behaviour as needed — skip `pause`, auto-confirm, use hot build/hot reload, etc. — so automation never blocks and never breaks a running server. The old `nopause`, `autoyes`, `hot-build` and `hot-reload` arguments are removed and must not be used.
21. **Answer first, do not act**: when the user is only asking, confirming, consulting or discussing, **answer only** — do not change code or files, do not run commands, do not start processes and do not run tests. Act only when the user explicitly asks to change, fix or execute something. If unsure, treat it as a question.
22. **Follow the user's language**: think and reply in the language the user writes in; default to Simplified Chinese when no language is specified.
23. **Look before you create**: list the current directory before creating any file or directory (confirm the path, the name and whether it already exists). Never invent paths or directories.
24. **No hard-coded local paths**: documentation, comments and examples must use generic forms such as `<JDK25>` or `<project dir>`; never embed a local drive-letter path. Use placeholders when an example is needed.
25. **Never write files through shell redirection**: create or modify text, code and docs with the MCP file tools required by rule 26. Never use pwsh / cmd (`Set-Content`, `>` redirection, `[IO.File]::WriteAllText`). Files produced by programs (build output, rendered images, ...) are exempt.
26. **All file and command operations must go through the MCP channel**: reading/writing files, editing text, running commands and building must always call this project's MCP tools — `dlqq_read_file` / `dlqq_search_text_in_file` to read, `dlqq_create_file` / `dlqq_overwrite_file` / `dlqq_append_file` / `dlqq_replace_text_in_file` / `dlqq_replace_lines_in_file` to write, `dlqq_run_command` to run commands, `dlqq_build_project` / `dlqq_rebuild_project` to build. Do not fall back to the native `read`/`write`/`edit` tools or pwsh. The MCP workspace must point at the current project directory (`dlqq_workspace_set`).
27. **Third-party license review**: prefer libraries under the **MIT** license. For any non-MIT library, you must **search the web** to confirm whether its license permits bundling/redistribution with this program; only then may it be added. If the license does not permit it (or cannot be confirmed), **stop and report** the library, version, license and restrictions to the user, and let the user decide — never add it unilaterally.
28. **Finish a change in one pass**: when the user requests a change, update every affected code, configuration and documentation file **in the same pass, then report**; never stop halfway with "not done yet / remaining steps / next round" and never split work that could be completed into follow-up questions. An AI model **cannot observe its own context usage**, so there is **no such thing as "context is full"** as a reason to refuse, shrink or interrupt a change. Only genuine external limits (denied permission, unavailable tool, a decision only the user can make) may pause work, and the exact limit plus the remaining work must be stated explicitly.
29. **Always read the git diff before writing a commit message**: when the user asks for a commit message, never write it from memory, an earlier conclusion or an old commit message. First run one read-only git command (recommended: `git status --short --untracked-files=all`, `git diff --stat`, `git diff --name-status`; read-only `git diff -- <file>` when needed) to confirm the actual changes (added/modified/deleted files and key differences), then write the message based on that diff. This read-only check is the only exception to rule 13; all other git commands (including add/commit/push/checkout/restore/reset/stash) remain forbidden.

## 2. Runtime requirements

### Java

- **Java 25 or newer**; **a full JRE is required**. Trimmed jlink images lack `jdk.crypto.ec`, `jdk.zipfs`, `java.net.http` and others, which can break PDF, TLS and zipfs scenarios.
- Generate a full JRE (`ALL-MODULE-PATH` bundles every JDK module, about 130 MB):
  ```cmd
  "<JDK25>\bin\jlink" --module-path "<JDK25>\jmods" --add-modules ALL-MODULE-PATH --output "<target>\jre" --strip-debug --no-man-pages --no-header-files
  ```
- The Java runtime ships with the package: `.runtime\java` is Microsoft Build of OpenJDK 25 (GPLv2+CE); no generator script is needed. To upgrade, replace the directory and keep `legal\`.
- The `WARNING: Using incubator modules: ...` message is normal (`ALL-MODULE-PATH` includes incubator modules). Exit code is 0 and the image works — ignore it.

### Python (BLE tool)

- **No system Python at runtime**: the BLE tool always uses the bundled `.runtime\python\` (interpreter + bleak). The BLE UI is implemented in Java Swing; Python is only a **headless bleak backend** (`ble_service.py`) started by Java as a **child process**, communicates via **stdin/stdout JSON**, and its lifetime matches the Java process.
- Ship the whole `.runtime\` directory. If `python\python.exe` is missing the BLE tool fails with an explicit error and never falls back to a Python on `PATH`; users can rebuild it with `make_python_env.bat`.

## 3. MCP client configuration

This is a standard **MCP Server** supporting **stdio** (default) and **Streamable HTTP**. It works with any MCP client. The server name in the `initialize` response is **`dlqq-mcp-server`**, and tool names keep the `dlqq_` prefix.

### DeepSeek Harness (DSH)

Append to `C:\Users\<user>\.dsh\profiles\desktop\cordis.patch.yml`:

```yaml
- insert:
    - id: dlqq-mcp-server
      name: "@deepseek-ai/dsh-mcp-client"
      config:
        serverName: dlqq-mcp
        transport: stdio
        cwd: '<deploy dir>\default_workspace'
        command: '<deploy dir>\java\bin\java.exe'
        args:
          - "-Dfile.encoding=UTF-8"
          - "-jar"
          - '<deploy dir>\dlqq_mcp_server.jar'
```

Restart DSH after saving. `serverName` is the client-side label (it also prefixes tool names, e.g. `dlqq-mcp` in `mcp__dlqq-mcp__`) and is separate from the `dlqq-mcp-server` name returned by `initialize`; `command` should point at the bundled JDK (`java\bin\java.exe`), or at `java` from PATH with version ≥ 25.

Every stdio example sets `cwd` to `<deploy dir>\default_workspace` so the MCP process starts with that directory as its default workspace; the server logic still defines the default workspace as the process CWD and the code is unchanged. HTTP configs have no child process and need no `cwd`.

### Other clients

They all use `mcpServers` + `command/args/env` (VS Code is the exception: `servers` + `type`):

```json
{
  "mcpServers": {
    "dlqq-mcp-server": {
      "command": "java",
      "cwd": "<deploy dir>\\default_workspace",
      "args": ["-Dfile.encoding=UTF-8", "-jar", "E:\\Code\\AIProject\\dlqq-mcp-server\\.runtime\\dlqq_mcp_server.jar"],
      "env": { "JAVA_TOOL_OPTIONS": "-Dfile.encoding=UTF-8" },
      "timeout": 60000
    }
  }
}
```

Config file locations for each client (Qwen Code, Claude Code/Desktop, Cursor, VS Code, Cline/Roo Code, Windsurf) are listed in the "Connect AI agent clients" section of [README.en.md](README.en.md).

### HTTP (Streamable HTTP)

Start the server first (one instance can serve several clients; there is no authentication and no session state):

```cmd
java -jar dlqq_mcp_server.jar --transport http --port 49129 --path /mcp
```

Endpoint: `http://127.0.0.1:49129/mcp`.

**DSH** (`~/.dsh/profiles/desktop/cordis.patch.yml`, `transport: streamable-http` + `url`):

```yaml
- insert:
    - id: dlqq-mcp-server-http
      name: "@deepseek-ai/dsh-mcp-client"
      config:
        serverName: dlqq-mcp-http
        transport: streamable-http
        url: http://127.0.0.1:49129/mcp
```

**Other clients** only need a URL instead of `command/args`:

| Client | HTTP fields |
|--------|-------------|
| Qwen Code | `"httpUrl": "http://127.0.0.1:49129/mcp"` (older SSE used `url`) |
| Claude Code | `claude mcp add --transport http <name> <url>`; JSON uses `"type": "http"` + `"url"` |
| Cursor | `"url": "http://127.0.0.1:49129/mcp"` |
| VS Code (Copilot) | under `"servers"`: `"type": "http"` + `"url"` |
| Cline / Roo Code | `"type": "streamableHttp"` + `"url"` |
| Windsurf | `"serverUrl": "http://127.0.0.1:49129/mcp"` |

Full examples are in the "8) Connect over HTTP (Streamable HTTP)" section of [README.en.md](README.en.md).

## 4. Build and release

### Version

- The version comes from `pom.xml` `<revision>` (currently `255.0.0`); there is no debug/release split and no `config.properties`.
- Maven writes the version and build time into the JAR manifest.

### Scripts

| Script | Purpose | Output |
|--------|---------|--------|
| `script\build.bat` | Build only: `mvn clean package -DskipTests` | `target\dlqq_mcp_server.jar` (thin jar) and `target\libs\` |
| `script\build_and_copy.bat` | Build and sync to `.runtime\` | `.runtime\dlqq_mcp_server.jar`, `.runtime\libs\` |
| `.runtime\build.bat` | Development build: `[-ai]`; without `-ai` it deletes old `.runtime\libs` jars first (stop the server) and pauses at the end; with `-ai` it is non-blocking + hot-build and can run while the server is running | `.runtime\dlqq_mcp_server.jar`, `.runtime\libs\` |
| `.runtime\make_python_env.bat` | One-click rebuild of the project Python env with bleak / ttkbootstrap / pillow | `.runtime\python\` (about 81 MB) |
| `.runtime\start-http.bat` | Double-click HTTP launcher (uses `java\`, `libs\` and the sibling JAR; asks for the workspace first) | long-running HTTP service |
| `.runtime\install_dlqq_mcp_dsh_client.bat` | Auto-install the DSH client plugin `dlqq-mcp-client`: download from Gitee first, fall back to GitHub; install dependencies and register it into the DSH profile (`link:` dependency + bundle + enabled patch entry) | `%USERPROFILE%\.dsh\plugins\dlqq-mcp-client`, updated DSH profile config |

### HTTP deployment layout

```text
.runtime\                          # the whole directory is the deployment package
├── start-http.bat
├── dlqq_mcp_server.jar            # thin jar, project code only
├── install_dlqq_mcp_dsh_client.bat / .ps1  # install the DSH client plugin automatically
├── make_python_env.bat / .py      # rebuild the Python runtime
├── java\                          # Microsoft OpenJDK 25 (GPLv2+CE)
├── python\                        # CPython 3.14.9 with all dependencies
└── libs\                          # all Maven dependencies
```

- When packaging a release, copy `LICENSE` into the zip root and copy `THIRD-PARTY-NOTICES.txt` into `.runtime` (next to the JAR; the release layout has no parent directory).
- Default port is **49129**; if it is in use the console asks for another port, **Enter** retries the default port.
- If `java\`, `libs\` or `python\` is missing the launcher fails with an explicit "incomplete package" error and never falls back to the system environment.
- `java\` missing does not fall back to a `java` on `PATH`; `python\` missing disables the BLE tools.
- `.runtime\install_dlqq_mcp_dsh_client.bat`: **close DSH first**, then run it; it downloads `dlqq-mcp-server-dsh-client` from Gitee (falling back to GitHub), installs it to `%USERPROFILE%\.dsh\plugins\dlqq-mcp-client` and registers it into the DSH profile (requires Node.js 20+ with `npm` on PATH).

Usage:

```cmd
script\build.bat
script\build_and_copy.bat
```

To change the version, edit `<revision>` in `pom.xml`.

## 5. Code structure

```
src/main/java/com/donglouqiaoque/dlqqmcpserver/
├── mcp/
│   ├── MCPServerMain.java          # entry: log dir / UTF-8 init, startup, health check thread
│   ├── AboutTabPanel.java          # Credits tab (author/version/third-party licenses)
│   ├── ConfigTabPanel.java         # Config tab (manage a session workspace's settings.properties)
│   ├── ToolCatalog.java            # Source-masked tool catalog for the Tool Management tab
│   ├── ToolManagementTabPanel.java # Tool Management tab (risk levels / visibility states)
│   ├── server/McpServer.java       # request dispatch + tool registry + getToolsInfo()
│   ├── transport/                  # McpTransport, StdioTransport (stdio), HttpTransport (Streamable HTTP)
│   └── tools/                      # AITools (registry), DeviceTools (peripheral tools) and tool implementations
├── device/                        # UartDevices/UsbDevices/WifiDevices/BleDevices + console tab panels
├── env/                           # EnvTabPanel (Environment tab: Java/Python detection, deps, rebuild)
└── utils/                          # FileUtils, AIRulesUtils, CalculatorUtils
src/main/resources/
├── py/ble_service.py               # headless BLE backend (bleak + stdin/stdout JSON, Java child process)
├── image/logo.png                  # window icon (PNG, used by LogoLoader)
└── simplelogger.properties
```

Startup arguments: `--transport http|stdio`, `--port N` (default 49129), `--path /mcp`, `--workspace <dir>`,
`--session-meta-key <key>` (default `dsh/sessionId`), `--session-agent-meta-key <key>` (default `dsh/agentId`),
`--session-workspace-meta-key <key>` (default `dsh/workspace`), `--session-idle-minutes <n>` (default 30,
`0` disables); stdio is the default.

Multi-session in one process: requests carry a session/agent identity in `params._meta`; the
workspace and settings are isolated per session. Clients without `_meta` share the default
session (legacy compatibility). HTTP `Mcp-Session-Id` serves the protocol and SSE only.
On a session's first request the server may initialize that session's workspace from
`_meta`'s session workspace (default key `dsh/workspace`, an absolute directory); an invalid,
missing or `.dlqq`-nested path silently falls back to the default workspace.

Permissions and timeouts: `permission.resource.level` (resource, default **chat, conversation
mode**) and `permission.operation.level` (operation, default **chat**). A new workspace starts as chat
and must be escalated through the permission tool first; all sessions on the same workspace share one
settings file. Permissions can only be changed via `dlqq_workspace_permission_set` (downgrade executes
directly, escalation shows a countdown approval; `dlqq_set_mcp_properties` / `dlqq_reset_mcp_properties`
reject protected keys (`permission.*`, `script.*`, `tool.policy.*`, `mcp.tools.*`) before approval).
Workspace switching always
requires approval, a failed set keeps the current workspace, and the same workspace returns
immediately. The tool total timeout = approval + execution, default 50 s, overridable with
`_meta["dlqq/toolTimeoutSeconds"]`; on timeout the approval window closes and execution is
aborted; `no_wait` background calls are unbounded.
`dlqq_overwrite_file` is special: a missing target fails before approval with a `dlqq_create_file` hint;
when the target is at most 1 MiB / 20,000 lines and line-level similarity is >= 90% (changed lines
<= `max(3, old lines x 10%)`), the tool is downgraded to risky; otherwise it stays high-risk.
Oversized, undecodable or diff-failed cases always remain high-risk.

Runtime directories (single global location, created on first start): under
`<jar dir>/.dlqq/` — `workspace_config.json` (path -> hash), `cache/<workspace hash>` (cache; workspace level only, no per-session split), `data/` (shared BLE scripts/icons), `log/{stdio,http}/<startup time>.log` (one file per
start, millisecond-precise timestamp) and `settings/<workspace hash>/settings.properties`; plus the fallback workspace
`<jar dir>/default_workspace/`. If `.dlqq` or `default_workspace` cannot be created or written,
startup fails (dialog + stderr + exit code) and no `.dlqq` is written into workspaces. AI can never
read or write `.dlqq` at any permission level except the content of the current workspace cache
directory; a workspace must never be located under `.dlqq`. The `.dlqq` directory is marked hidden
(dos:hidden) on Windows after it is created.
client falls back to `initialize`); **JAR replacement watcher (poor-man's hot reload)**: every **2 seconds** the
running process compares the in-memory `BuildInfo.BUILD_TIME` with the JAR manifest `Build-Time`; on mismatch (or file
mtime/size change) it logs an English error, shows a topmost notice dialog and **rejects new tool calls**; foreground
tool calls are awaited for at most **5 s** (forced exit on timeout), **background (`no_wait`) calls are not awaited and
end with the process**, and the notice dialog stays visible for **at least 2 s**. The process then exits and the DSH
client auto-reconnects and starts a fresh process with the new JAR. Stopping the server before rebuilding is still
recommended.

Server console: on startup (with a graphical environment) it **starts hidden in the system tray** with no
window; **single-click** the tray icon or use the tray menu "Show console" to open it. The menu bar shows the Chinese
app name "鸫楼桥鹊MCP服务器控制台" (dropdown: hide/exit) with "联系作者：https://www.donglouqiaoque.com" in its
centre (clickable, opens the default browser) and a Refresh button on its right (small right margin); the
**tabs `Sessions` / `Config` / `Tool Management` / `BLE` / `UART` / `USB` / `WiFi` / `Environment` / `Credits` sit above the table**; peripherals no longer open separate
windows — every device is shown and managed inside its tab as a device list + panel (UART by COM port, USB by
`VID:PID:bus:address`, BLE by MAC, WiFi by interface/SSID).
The `Config` tab selects a session or workspace (active sessions plus workspaces registered in `workspace_config.json`
without an active session; sessions sharing one workspace share the same `settings.properties`) and lists every
configuration property with its current and default value; double-click the "Current value" cell to save, and use
"Restore default" for the selected row; the "Restore all" button to its right is always available and restores every configurable
property of the current workspace to its default (with confirmation); switching a session or workspace reloads that workspace's configuration file.
The `Tool Management` tab uses the same session/workspace selection and shows **all tools, including source-masked tools**,
with resource risk, peripheral risk and current visibility. Defaults come from `resources/tool_policy.txt`; changes are
written only to `tool_policy.txt` in that workspace's settings directory (next to `settings.properties`). Registered tools
support the four documented states (full access / view-only / hidden) and source-masked tools stay at "source-masked";
risk levels are editable for every tool. The "Restore all" button to the right of "Restore default" is always available and
clears all tool-policy overrides for the current workspace (with confirmation). Hiding/restoring tools notifies clients with `notifications/tools/list_changed`.
The `Environment` tab shows Java (version/vendor/install dir/JAR/heap) and Python (interpreter/version/pip/bleak)
and provides buttons to open directories, install/update BLE dependencies, and rebuild the Python environment
(via `.runtime\make_python_env.bat`).
The `Credits` tab shows the author/contact, version and the build time **in the system time zone**; the license body is
**read dynamically** from `THIRD-PARTY-NOTICES.txt` (**the JAR directory, i.e. `.runtime`**; the actual path is shown);
when the file is missing the text area shows the expected path and the open button shows an error dialog; the tab also
offers buttons to open the deploy directory and the author website.
The **Refresh button refreshes the currently selected tab**: Sessions refreshes the table; Config reloads the property list;
Tool Management reloads the tool policy; BLE refreshes backend status/device lists; UART and USB re-enumerate devices;
WiFi refreshes interfaces and connection status; Environment re-detects Java/Python; Credits reloads the license file.
Individual tabs no longer provide their own refresh buttons.
native title bar text is cleared. The table is sorted by last activity (descending) and shows active sessions plus
workspaces recorded in `workspace_config.json` (columns: session key/workspace/source/resource permission/operation
permission/last activity `yyyy-MM-dd HH:mm:ss`); the resource-permission, operation-permission and last-activity columns
(headers included) are centre-aligned. **The workspace (session rows only) plus the two permission columns are edited
directly by double-clicking a table cell** (the workspace cell has a directory picker button; permissions are dropdown
choices) with immediate effect; only an **actual change** of a permission value or the workspace path counts as an
operation, while opening the dropdown/picker and closing it with the original value writes nothing and updates no
timestamps; clicking an empty table area clears the selection. The bottom area shows deploy dir,
runtime dir, log file, session count and row count (long paths scroll horizontally). Closing the window hides it again;
only "Exit server" terminates the MCP server process.

## 6. Tool overview

**118** tools in total. Implementations live in `mcp/tools/`; parameters are documented in [README.en.md](README.en.md).
Of the 13 project build/chip/run/debug tools, `dlqq_build_project` is restored (resource RISK, no per-call approval); the
other 12 stay commented out. Of the 13 script-path setters, `dlqq_set_build_script` is restored (resource HIGH, needs
approval); the other 12 stay commented out. Of the 5 tool-management tools only `dlqq_mcp_refresh_tools` remains;
`hide/show/reset/run` are temporarily source-masked. `dlqq_test_hot_reload` is still shielded; the background-program tools
use the new names (`dlqq_kill_command`, ...).

| Group | Count | Notes |
|-------|-------|-------|
| File operations | 21 | read/write/create/overwrite/append, line edits, text replace, copy/move/rename, BOM, binary, zip |
| Directory operations | 8 | mdir, mdirs, tree listing, recursive copy, ... |
| Search | 4 | files, directories, text in a directory, text in one file |
| Dialogs | 3 | text input, confirmation, file picker (Swing) |
| UART | 5 | console tab; addressed by COM name, multiple ports at once, send/receive/log |
| BLE | 11 | console tab (Java Swing); includes `dlqq_check_bluetooth_open`; headless Python bleak backend, addressed by MAC |
| USB | 5 | console tab; usb4java, addressed by `VID:PID:bus:address` |
| WiFi | 5 | console tab; netsh / nmcli, network interface / SSID based |
| Mouse / keyboard / screen | 14 | `java.awt.Robot` + `dlqq_capture_screen` |
| Image / Office | 3 | image conversion, Office read and conversion |
| Time / calculators | 4 | time 1 + calculators 3 |
| Command execution & background | 18 | 3 command tools + 5 background managers per shell (new `dlqq_kill_*` names) |
| MCP data/properties | 13 | version/property read+write, workspace, cleanup; only `dlqq_mcp_refresh_tools` remains (hide/show/reset/run source-masked) |
| Project build / script setting | 2 | `dlqq_build_project` (resource RISK, runs directly once configured); `dlqq_set_build_script` (resource HIGH, setting the script needs approval) |
| Tool list / environment | 2 | `dlqq_get_tool_list`, `dlqq_get_tools_env` |

**Dynamic tool management (refresh only for now)**: `dlqq_mcp_refresh_tools` stays fully accessible and only pushes `notifications/tools/list_changed`; `dlqq_mcp_hide_tools`, `dlqq_mcp_show_tools`, `dlqq_mcp_reset_tools` and `dlqq_mcp_run_tools` are **source-masked**. `mcp.tools.excluded` is still read by the server, but no tool can modify it right now; in HTTP mode a refresh needs an open SSE connection.

**Workspace**: `dlqq_workspace_set` / `dlqq_workspace_get` / `dlqq_workspace_reset` / `dlqq_workspace_permission_set` / `dlqq_get_cwd_path`, plus the `--workspace <dir>` startup argument. The default workspace is the process CWD; when the CWD is unusable or a fallback is needed the server uses `<jar dir>/default_workspace` (the CWD is never a fallback target). **The workspace path is not persisted**. Permissions use two settings: `permission.resource.level` (resource, default chat for a new workspace) and `permission.operation.level` (operation, default chat). All other settings (`script.*`, `response.max.size`, `mcp.tools.excluded`, ...) are persisted in `<jar dir>/.dlqq/settings/<workspace hash>/settings.properties`, and switching the workspace reloads them. Relative paths, script working directories and AI rule bases are resolved against the workspace; cache, data and settings are all workspace-scoped (no per-session split) — cache lives at `<jar dir>/.dlqq/cache/<workspace hash>/` and AI may only read/write content inside that workspace cache directory.

**Script tool rules**: `dlqq_build_project` / `dlqq_rebuild_project` / `dlqq_chip_*` / `dlqq_*erase_download` / `dlqq_run_project` / `dlqq_debug_project` and friends have **no built-in default script paths**. When unconfigured they fail with "configure the tool path first" and point at the matching `dlqq_set_*_script`. Settings are written under `script.*` in `<jar dir>/.dlqq/settings/<workspace hash>/settings.properties`.
A script path can only be set through `dlqq_set_build_script` (**resource HIGH, needs user approval**); `dlqq_set_mcp_properties` / `dlqq_reset_mcp_properties` **reject protected keys before approval** (`script.*`, `permission.*`, `tool.policy.*`, `mcp.tools.*`). Running the configured `dlqq_build_project` is a **resource RISK operation and runs directly inside the workspace, without per-call approval**.

## 7. AI rule files

| File | Purpose |
|------|---------|
| `.aiignore` | files/directories the AI ignores |
| `.aiwriterules` | paths the AI must not modify (write protection) |
| `.aireadrules` | paths the AI must not read (read protection) |

Syntax is Gitignore-style (`*.log`, `build/`, `/config.txt`, `!important.log`, `**/*.tmp`).
Scope: strictly enforced inside the project; outside the project a JFrame asks the user; single-file operations are not restricted.

## 8. Hot reload and dynamic tools (important)

### Summary

- The JVM cannot reload already-loaded classes: **replacing the JAR requires restarting the server process**.
- Restarting the server drops the SSE connection; the HTTP client (DSH, protocol 2025-11-25) does **not** reconnect SSE and does not re-run `initialize`/`tools/list`, so its tool list stays stale and added/removed tools remain invisible. **The client must be restarted.**
- `notifications/tools/list_changed` only works while SSE is alive: without a server restart, when the tool set changes the client re-fetches `tools/list` about 2 ms after the push (measured).

### Two diagnostic tools

| Tool | Notes |
|------|-------|
| `dlqq_get_mcp_version` | `Build time (in code, real value)` comes from `BuildInfo.BUILD_TIME` (injected at compile time by templating-maven-plugin); it does not change when the JAR is replaced without a restart. `JAR manifest build time (on disk)` reads the manifest, so it does change. A mismatch means the JAR was replaced but the process still runs old code. |
| `dlqq_test_hot_reload` | Returns a hard-coded `HOT_RELOAD_TEST_MARK` (currently v4); change it and rebuild to verify that new code is loaded. |

### SSE and restarts (mechanism)

- HTTP mode: the client opens SSE with `GET /mcp` + `Accept: text/event-stream`; `HttpTransport.sseClients` keeps the connections and `notifyToolsListChanged()` pushes to each.
- Restart = old process exits and a new one listens again: the kernel closes the old TCP connections and the list is cleared; the new process cannot inherit them (TCP connections belong to the old process socket).
- Measured with DSH: after a server restart it does not reconnect SSE or `initialize`; it only POSTs `tools/call` when needed, so there is no push channel (`notifyToolsListChanged()` returns false).

### Verification procedure

1. Build the new JAR and record `BuildInfo.BUILD_TIME`;
2. Stop the HTTP service and confirm the port is released;
3. Overwrite the JAR in the deployment directory;
4. Restart the service;
5. Call `dlqq_get_mcp_version`: in-code time == on-disk time == the new value means loaded; an old in-code value means not loaded;
6. If tools were added/removed/renamed or schemas changed, restart the client and check the tool list.

### Dynamic tool management (refresh only for now)

- Only `dlqq_mcp_refresh_tools` is registered: it pushes `notifications/tools/list_changed` without changing the tool set; in HTTP mode the client must hold an open SSE connection.
- `dlqq_mcp_hide_tools` / `dlqq_mcp_show_tools` / `dlqq_mcp_reset_tools` / `dlqq_mcp_run_tools` are **source-masked**; hiding/restoring tools and calling tools by name are temporarily unavailable.
- `mcp.tools.excluded` is still read by the server, but no tool can modify it right now.

## 9. Known limitations

- **PDF/TLS/zipfs etc.**: require a **full JRE**. Trimmed jlink images fail with missing modules (the historical `Could not initialize class org.apache.pdfbox.io.IOUtils` was one of these). See "2. Runtime requirements".
- **Hardware tools** (serial/BLE/USB) without the actual hardware can only be verified at the window and management level; end-to-end traffic needs real devices.
- **PDF output**: not supported by the tools (`dlqq_convert_office_file` has no PDF target); convert to DOCX first and export with an external tool.

## 10. References

### MCP protocol

- MCP documentation: <https://modelcontextprotocol.io/>
- MCP specification (including Streamable HTTP): <https://modelcontextprotocol.io/specification/2026-07-28>

### Client integration docs

| Client | Official docs | Config file in this project |
|--------|---------------|-----------------------------|
| **DeepSeek Harness (DSH)** | see "3. MCP client configuration" above (plugin `@deepseek-ai/dsh-mcp-client`) | `C:\Users\<user>\.dsh\profiles\desktop\cordis.patch.yml` |
| Qwen Code | <https://qwenlm.github.io/qwen-code-docs/zh/users/features/mcp/> | `settings.json` |
| Claude Code | <https://code.claude.com/docs/en/mcp> | `.mcp.json` / `claude mcp add` |
| Cline | <https://docs.cline.bot/mcp/mcp-overview> | `cline_mcp_settings.json` |
| Cursor | <https://cursor.com/docs/mcp> | `.cursor\mcp.json` |
| VS Code (GitHub Copilot) | <https://code.visualstudio.com/docs/agent-customization/mcp-servers> | `.vscode\mcp.json` (`servers` + `type`) |

> All links were reachable as of 2026-10. If a client is redesigned and a link breaks, use that client's official documentation site.
