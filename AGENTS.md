# AGENTS.md — dlqq-mcp-server 工程约定

> 本文给 AI 助手（Qwen Code / DeepSeek Harness 等）阅读，包含硬性规则、构建发布方式、结构与工具速查。
> 完整工具手册见 [README.md](README.md)（英文版：[README.en.md](README.en.md)）。README 与 AGENTS 的中英文版本是**唯一**的说明文档。
> 英文版规则：[AGENTS.en.md](AGENTS.en.md)。

## 一、硬性规则（必须遵守）

1. **语言与构建**：Java 25 + Maven。
2. **工程定位**：面向各类 AI Agent 客户端（DeepSeek Harness、Qwen Code、Claude Code、Cursor、Cline 等）的 MCP 服务器（stdio 传输、JSON-RPC 2.0），入口 `com.donglouqiaoque.dlqqmcpserver.mcp.MCPServerMain`。
3. **脚本位置**：构建脚本放 `script\`（`build.bat`、`build_and_copy.bat`）；运行时与启动脚本放 `.runtime\`（`make_python_env.bat`、`make_python_env.py`、`start-http.bat`）；临时脚本放 `.ai_res\`（约定见 `AIRES.md`）。不要把 `.bat/.ps1/.sh` 放在这三处之外。
4. **.bat 必须使用英文**：`.bat` 文件**只能出现英文/ASCII 字符，不得出现任何中文**（注释、`echo`、提示语、变量说明一律用英文）。
   原因：cmd 默认代码页（GBK/936）与 UTF-8 混用会导致中文乱码，甚至影响批处理解析。
5. **产物命名**：JAR 固定名为 **`dlqq_mcp_server.jar`**。
6. **改完代码必须构建**：每次修改代码后都要重新打包，并按下面的规则替换发布目录里的 JAR。
7. **产物统一输出**：JAR 固定名为 `dlqq_mcp_server.jar`，构建后由 `build_and_copy.bat` 同步到 `.runtime\`。
8. **发行包手动管理**：`.release\` 仅用于用户手动存放压缩后的发布包，不写脚本处理。
9. **工具变更要同步文档**：增删或修改 MCP 工具后，必须同步更新 [README.md](README.md) 与本文的工具速查。
10. **诊断日志**：`StdioTransport`、`McpServer`、`MCPServerMain` 有详细连接诊断日志，统一输出到 `<jar 同级>/.dlqq/log/<stdio|http>/<启动时间>.log`（每次启动新建一个文件，文件名时间**精确到毫秒** `yyyy-MM-dd_HH-mm-ss-SSS`，logger 与 stderr 合并写入）。
11. **临时文件位置**：临时文件/中间产物**禁止放 C 盘系统临时目录**，统一放工程根目录下的本地临时工作目录（约定见 `AIRES.md`），按类型分子目录，用完自行清理。
12. **禁止用脚本批量修改代码**：不允许用 PowerShell / Python / sed / awk 等脚本对源码做批量查找替换或自动改写。所有代码修改必须逐个文件、逐个位置人工确认后用编辑工具完成；确需大范围调整时，先列出清单交由用户确认，再逐项修改。
13. **禁止使用任何 git 命令（写提交信息前的只读检查除外）**：包括但不限于 `git status`、`git diff`、`git log`、`git checkout/restore/reset`、`git add/commit/push/pull/stash` 等。版本管理由用户本人操作，AI 一律不得执行任何 git 命令（读取与回滚都不允许）。**唯一例外**：用户要求写 git 提交信息时，必须先执行一次只读 git 命令（`git status --short --untracked-files=all`、`git diff --stat`、`git diff --name-status` 等）读取当前差异后再撰写，见规则 29；该例外不允许任何写操作或回滚。
14. **禁止使用全限定类名**：代码中不得出现 `new java.xxx.XXX()`、`java.util.List` 这类全限定写法。**能 import 的一律在文件顶部 import，然后使用短类名**；只有无法 import 的情况（同名类冲突、需要显式区分同名的不同包类型、静态导入等）才允许写全限定名，并在该处注释说明原因。注释/文档中的类名描述不算代码，但也不建议加包名。
15. **README 与 AGENTS 中英文必须同步**：修改 [README.md](README.md)（中文）时必须同步修改 [README.en.md](README.en.md)（英文），反之亦然；修改 [AGENTS.md](AGENTS.md) 时必须同步修改 [AGENTS.en.md](AGENTS.en.md)。各语言版本的章节、工具数量、工具说明、配置示例必须保持一致，不允许只改其中一份。
16. **日志必须 UTF-8 且纯英文**：`.dlqq/log/` 下的日志文件必须使用 **UTF-8** 编码写入；日志内容**只能使用英文**，不得出现中文（包括 `logger.info/warn/error/debug` 的提示文本、异常附加说明）。新增或修改日志时必须写英文。
17. **配置文件必须 UTF-8 且注释纯英文**：`settings.properties` 等配置文件必须使用 **UTF-8** 编码读写；文件内的注释**一律使用英文**，不得写入中文注释；键值内容如含中文（如脚本路径）保持 UTF-8 原样，不做转义。
18. **禁止 AI 直接执行有副作用的脚本**：下载、安装、删除、解包、批量清理、烧写/下载固件等会造成文件修改、耗时较长或需要管理员权限的脚本，AI **一律不得直接执行**，必须把完整命令交给用户手动执行；AI 只允许做只读检查（测试网络连通性、判断路径/文件是否存在、读取文件内容），不得代用户点确认、不得绕过权限提示。
19. **测试脚本必须走真实入口**：验证 `.bat/.ps1` 能否正常运行时，必须像用户一样实际执行该 `.bat/.ps1`；**禁止直接运行它内部的 `.py`**（或其它被入口脚本调用的实现）来绕过入口，否则测不到参数解析、路径拼接和 `pause` 等入口逻辑。
20. **`.bat/.ps1` 必须 pause，AI 调用统一传 `-ai`**：所有批处理/脚本在正常结束和失败退出前都要 `pause`，方便用户双击运行时查看报错；AI/MCP 工具调用脚本时必须传入 **`-ai`** 参数（脚本至少识别 `ai`、`-ai`、`--ai`、`/ai`，不区分大小写），脚本收到该参数后按实际情况调整行为：跳过 `pause`、自动确认、使用热构建/热加载等，避免阻塞或破坏正在运行的服务。旧的 `nopause`、`autoyes`、`hot-build`、`hot-reload` 等参数一律废弃，不再使用。
21. **先回答问题，别动手**：用户只是提问、确认、咨询或讨论时，**只回答**——不改代码、不改文件、不执行命令、不启动进程、不跑测试；只有用户明确要求「改」「修」「执行」时才动手。拿不准时按「只是问问」处理。
22. **跟随用户语言**：用户用哪种语言提问，就用哪种语言思考和回复；没有明确语言时默认用简体中文。
23. **先看目录再创建**：新建任何文件或目录之前，先列一下当前目录（确认路径、命名、是否已存在），禁止凭空拼路径、造目录。
24. **禁止硬编码本机路径**：文档、注释与示例里只能写通用形式（如 `<JDK25>`、`<项目目录>`），不得写死本机盘符路径；确需举例时用占位符。
25. **写文件禁止用 shell 重定向**：创建或修改文本、代码、文档必须用规则 26 指定的 MCP 文件工具；禁止用 pwsh / cmd（`Set-Content`、`>` 重定向、`[IO.File]::WriteAllText`）改写文件。构建、渲染图片等由程序产出的文件除外。
26. **所有文件与命令操作必须走 MCP 通道**：读写文件、修改文本、执行命令、构建发布一律调用本项目的 MCP 工具——读文件用 `dlqq_read_file` / `dlqq_search_text_in_file`，写文件用 `dlqq_create_file` / `dlqq_overwrite_file` / `dlqq_append_file` / `dlqq_replace_text_in_file` / `dlqq_replace_lines_in_file` 等，执行命令用 `dlqq_run_command`，构建用 `dlqq_build_project` / `dlqq_rebuild_project`；不得改用原生 `read`/`write`/`edit` 工具或 pwsh 绕过。MCP 工作区必须指向当前工程目录（`dlqq_workspace_set`）。
27. **第三方库许可证审查**：新增依赖时优先选择 **MIT** 协议的库。若拟采用非 MIT 协议的库，必须**联网检索**该协议是否允许随本程序一起分发（bundling / redistribution）；确认允许方可引入。若检索结果表明不允许（或无法确认），**必须暂停并向用户说明**该库、版本、协议与限制，由用户决定是否采用，禁止自行添加。
28. **要改就一次性改完**：用户提出修改需求后，必须把该需求涉及的代码、配置、文档**一次性全部改完再汇报**；**禁止**改到一半以"尚未完成/后续步骤/下一轮继续"收尾，也禁止把本可一次完成的工作拆给用户追问。AI 大模型**无法获取当前上下文占用率**，因此**不存在"上下文空间已满"之类的说法**作为拒绝、缩减或中断修改的理由；只有遇到真实外部限制（权限被拒、工具不可用、必须由用户决策）时才可暂停，并须明确说明具体限制和剩余工作。
29. **写 git 提交信息前必须先读取 git 差异**：用户要求写提交信息时，禁止凭记忆、上一轮结论或旧提交信息直接编写；必须先执行一次只读 git 命令（建议组合 `git status --short --untracked-files=all`、`git diff --stat`、`git diff --name-status`，必要时只读 `git diff -- <文件>`）确认本次实际改动（新增/修改/删除的文件与关键差异），再按真实差异撰写。该只读检查是规则 13 的唯一例外；检查之外仍禁止任何 git 命令（包括 add/commit/push/checkout/restore/reset/stash 等）。

## 二、运行环境要求

### Java

- **Java 25 或更高版本**；**必须使用完整版 JRE**：精简 jlink 镜像缺 `jdk.crypto.ec`、`jdk.zipfs`、`java.net.http` 等模块，可能在 PDF、TLS、zipfs 场景报错。
- 生成完整 JRE（`ALL-MODULE-PATH` 包含 JDK 全部模块，约 130 MB）：
  ```cmd
  "<JDK25>\bin\jlink" --module-path "<JDK25>\jmods" --add-modules ALL-MODULE-PATH --output "<目标目录>\jre" --strip-debug --no-man-pages --no-header-files
  ```
- Java 运行时随包提供：`.runtime\java` 为 Microsoft Build of OpenJDK 25（GPLv2+CE），不再需要生成脚本；升级时直接替换该目录并保留 `legal\`。
- 生成时的 `WARNING: Using incubator modules: ...` 属正常现象（`ALL-MODULE-PATH` 含孵化器模块），退出码 0、镜像可用，忽略即可。

### Python（BLE 工具）

- **运行时不使用系统 Python**：BLE 工具固定使用随包分发的 `.runtime\python\`（解释器 + bleak）。BLE 的 UI 已由 Java Swing 实现，Python 只作为**无界面 bleak 后端**（`ble_service.py`）：由 Java 以**子进程**启动、通过 **stdin/stdout JSON** 通信，生命周期与 Java 进程一致。
- 部署时整个 `.runtime\` 目录一起分发；缺少 `python\python.exe` 时 BLE 工具直接报错，不会回退到 PATH 上的 Python；用户可运行 `make_python_env.bat` 重建。
- 构建期生成 `.runtime\python` 需要本机有一份完整 CPython（3.10+），仅构建使用。

## 三、MCP 客户端配置

本服务是标准 **MCP Server**，支持 **stdio**（默认）与 **Streamable HTTP** 两种传输，可接入任意 MCP 客户端；服务端在 `initialize` 响应里的名称是 **`dlqq-mcp-server`**，`tools/call` 的工具名前缀仍是 `dlqq_`。

### DeepSeek Harness (DSH)

在 `C:\Users\<用户名>\.dsh\profiles\desktop\cordis.patch.yml` 追加：

```yaml
- insert:
    - id: dlqq-mcp-server
      name: "@deepseek-ai/dsh-mcp-client"
      config:
        serverName: dlqq-mcp
        transport: stdio
        cwd: '<部署目录>\default_workspace'
        command: '<部署目录>\java\bin\java.exe'
        args:
          - "-Dfile.encoding=UTF-8"
          - "-jar"
          - '<部署目录>\dlqq_mcp_server.jar'
```

保存后重启 DSH。`serverName` 是客户端侧标识（同时决定工具名前缀，如 `mcp__dlqq-mcp__` 中的 `dlqq-mcp`），与服务端 `initialize` 返回的 `dlqq-mcp-server` 是两个值，可以不同；`command` 建议指向随包 JDK（`java\bin\java.exe`），改用 PATH 上的 `java` 也可以，但版本需 ≥ 25。

stdio 示例都把 `cwd` 固定为 `<部署目录>\default_workspace`，这样 MCP 进程的默认工作区即为该目录；服务端逻辑仍是“默认工作区 = 进程 CWD”，未改代码。HTTP 配置没有子进程，不需要 `cwd`。

### 其他客户端

统一是 `mcpServers` + `command/args/env` 结构（VS Code 例外，用 `servers` + `type`）：

```json
{
  "mcpServers": {
    "dlqq-mcp-server": {
      "command": "java",
      "cwd": "<部署目录>\\default_workspace",
      "args": ["-Dfile.encoding=UTF-8", "-jar", "E:\\Code\\AIProject\\dlqq-mcp-server\\.runtime\\dlqq_mcp_server.jar"],
      "env": { "JAVA_TOOL_OPTIONS": "-Dfile.encoding=UTF-8" },
      "timeout": 60000
    }
  }
}
```

各客户端配置文件位置（Qwen Code、Claude Code/Desktop、Cursor、VS Code、Cline/Roo Code、Windsurf）见 [README.md](README.md) 的“接入 AI Agent 客户端”一节。

### HTTP（Streamable HTTP）方式

先启动服务（一次启动可被多个客户端共用；服务端无鉴权，会话隔离按 `params._meta` 判定）：

```cmd
java -jar dlqq_mcp_server.jar --transport http --port 49129 --path /mcp
```

端点：`http://127.0.0.1:49129/mcp`。

**DSH**（`~/.dsh/profiles/desktop/cordis.patch.yml`，`transport: streamable-http` + `url`）：

```yaml
- insert:
    - id: dlqq-mcp-server-http
      name: "@deepseek-ai/dsh-mcp-client"
      config:
        serverName: dlqq-mcp-http
        transport: streamable-http
        url: http://127.0.0.1:49129/mcp
```

**其他客户端**只需把 stdio 的 `command/args` 换成 URL：

| 客户端 | HTTP 关键字段 |
|--------|----------------|
| Qwen Code | `"httpUrl": "http://127.0.0.1:49129/mcp"`（旧版 SSE 用 `url`） |
| Claude Code | `claude mcp add --transport http <name> <url>`；JSON 用 `"type": "http"` + `"url"` |
| Cursor | `"url": "http://127.0.0.1:49129/mcp"` |
| VS Code (Copilot) | `"servers"` 里 `"type": "http"` + `"url"` |
| Cline / Roo Code | `"type": "streamableHttp"` + `"url"` |
| Windsurf | `"serverUrl": "http://127.0.0.1:49129/mcp"` |

完整示例见 [README.md](README.md) 的“⑧ 用 HTTP（Streamable HTTP）方式接入”。

## 四、构建与发布

### 版本

- 版本号由 `pom.xml` 的 `<revision>`（当前 `255.0.0`）控制，不再区分 debug / release，也没有 `config.properties`。
- 构建时 Maven 会把版本与构建时间写入 JAR 清单。

### 脚本

| 脚本 | 作用 | 输出 |
|------|------|------|
| `script\build.bat` | 仅构建：`mvn clean package -DskipTests` | `target\dlqq_mcp_server.jar`（thin jar）与 `target\libs\` |
| `script\build_and_copy.bat` | 构建并同步到 `.runtime\` | `.runtime\dlqq_mcp_server.jar`、`.runtime\libs\` |
| `.runtime\build.bat` | 开发期构建：`[-ai]`；不带 `-ai` 时先删除 `.runtime\libs` 旧 jar（需停服）并在结束时 pause，带 `-ai` 时自动无阻塞 + 热构建，可在服务运行中构建 | `.runtime\dlqq_mcp_server.jar`、`.runtime\libs\` |
| `.runtime\make_python_env.bat` | 一键重建项目 Python 环境并安装 bleak / ttkbootstrap / pillow | `.runtime\python\`（约 81 MB） |
| `.runtime\start-http.bat` | 双击启动 HTTP 服务（用 `java\`、`libs\` 与同目录 jar，启动前询问工作区） | 常驻 HTTP 服务 |
| `.runtime\install_dlqq_mcp_dsh_client.bat` | 自动安装 DSH 客户端插件 `dlqq-mcp-client`：优先从 Gitee 下载，失败回退 GitHub；安装依赖、注册到 DSH profile（`link:` 依赖 + bundle + 启用补丁） | `%USERPROFILE%\.dsh\plugins\dlqq-mcp-client`，DSH profile 配置更新 |

### HTTP 部署结构

```text
.runtime\                          # 整个目录就是部署包
├── start-http.bat
├── dlqq_mcp_server.jar            # thin jar，仅项目代码
├── install_dlqq_mcp_dsh_client.bat / .ps1  # 自动安装 DSH 客户端插件
├── make_python_env.bat / .py      # 重建 Python 环境用
├── java\                          # Microsoft OpenJDK 25（GPLv2+CE）
├── python\                        # CPython 3.14.9 + 全部依赖
└── libs\                          # 全部 Maven 依赖
```

- 发行打包时把 `LICENSE` 复制进 zip 根目录，把 `THIRD-PARTY-NOTICES.txt` 复制进 `.runtime`（jar 同级；实际发行版只有这一层目录）。
- 默认端口 **49129**；被占用时控制台提示输入新端口，**直接回车 = 重试默认端口**。
- 缺少 `java\`、`libs\`、`python\` 任一目录时，脚本直接报错"安装不完整"，不回退到系统环境。
- `java\` 缺失不会回退 PATH 上的 java；`python\` 缺失则 BLE 工具不可用。
- `.runtime\install_dlqq_mcp_dsh_client.bat`：**先关闭 DSH** 再运行；优先从 Gitee 下载 `dlqq-mcp-server-dsh-client`，失败回退 GitHub，安装到 `%USERPROFILE%\.dsh\plugins\dlqq-mcp-client` 并注册到 DSH profile（需 Node.js 20+，`npm` 在 PATH）。

用法：

```cmd
script\build.bat
script\build_and_copy.bat
```

改版本号只需编辑 `pom.xml` 的 `<revision>`。

## 五、代码结构速查

```
src/main/java/com/donglouqiaoque/dlqqmcpserver/
├── mcp/
│   ├── AboutTabPanel.java          # 版权鸣谢选项卡（作者/版本/第三方许可）
│   ├── ConfigTabPanel.java         # 配置选项卡（按会话管理工作区 settings.properties）
│   ├── ToolCatalog.java            # 源码屏蔽工具目录（工具管理页展示用）
│   ├── ToolManagementTabPanel.java # 工具管理选项卡（风险等级/可见程度）
│   ├── MCPServerMain.java          # 入口：日志目录/UTF-8 初始化、启动、健康检查线程
│   ├── protocol/                   # JsonRpcMessage、McpSchema
│   ├── server/McpServer.java       # 请求分发 + 工具注册表 + getToolsInfo()
│   ├── transport/                  # McpTransport 接口、StdioTransport（stdio）、HttpTransport（Streamable HTTP）
│   └── tools/                      # AITools（注册中心）、DeviceTools（外设注册）与各工具实现
├── device/                        # UartDevices/UsbDevices/WifiDevices/BleDevices + 控制台各 Tab 面板
├── env/                           # EnvTabPanel（环境选项卡：Java/Python 检测、依赖安装、重建）
└── utils/                          # FileUtils、AIRulesUtils、CalculatorUtils
src/main/resources/
├── py/ble_service.py               # BLE 无界面后端（bleak + stdin/stdout JSON，Java 子进程）
├── image/logo.png                  # 窗口图标（PNG，LogoLoader 使用）
└── simplelogger.properties
```

启动参数：`--transport http|stdio`、`--port N`（默认 49129）、`--path /mcp`、`--workspace <dir>`、
`--session-meta-key <key>`（默认 `dsh/sessionId`）、`--session-agent-meta-key <key>`（默认 `dsh/agentId`）、
`--session-workspace-meta-key <key>`（默认 `dsh/workspace`）、`--session-idle-minutes <n>`（默认 30，`0` 不回收）；
默认 stdio。

单进程多会话：请求通过 `params._meta` 携带会话/Agent 标识，工作区与配置按会话隔离；
没有 `_meta` 的客户端统一使用默认会话（兼容旧客户端）。HTTP 的 `Mcp-Session-Id` 只用于协议与 SSE。
会话首次收到请求时，可用 `_meta` 中的会话工作区（默认键 `dsh/workspace`，值为绝对目录）初始化
该会话的工作区；路径非法/不存在/位于 `.dlqq` 下时静默回退到默认工作区。

权限与超时：`permission.resource.level`（资源权限，默认 **chat 对话模式**）与
`permission.operation.level`（操作权限，默认 **chat**）两套配置。
新工作区首次使用即写入 chat，需先用权限工具提权；同一工作区多会话共用一份 settings。
权限只能通过 `dlqq_workspace_permission_set` 修改（降权直行、提权弹带倒计时审批；`dlqq_set_mcp_properties` / `dlqq_reset_mcp_properties` 对 `permission.*`、`script.*`、`tool.policy.*`、`mcp.tools.*` 等保护属性直接拒绝且不进入审批）。
工作区切换始终需要审批，设置失败保持当前工作区；同工作区直接返回。工具总超时=审批+运行，默认 50 秒，
可由 `_meta["dlqq/toolTimeoutSeconds"]` 指定；超时立即关闭审批窗口并终止执行；`no_wait` 后台调用不受限。
`dlqq_overwrite_file` 例外：文件不存在时审批前直接报错（提示改用 `dlqq_create_file`）；目标不超过
1 MiB / 20000 行且新旧内容行级相似度 ≥ 90%（改动行数 ≤ `max(3, 旧行数×10%)`）时降级为风险工具，
否则保持高危；文件过大、无法读取/解码或 diff 失败时一律保持高危。
运行期目录（全局唯一，首次启动自动创建）：`<jar 同级>/.dlqq/` 下的 `workspace_config.json`（路径→hash）、
`cache/<工作区hash>`（缓存，仅按工作区隔离、不区分会话）、`data/`（BLE 脚本/图标等共享数据）、`log/{stdio,http}/<启动时间>.log`
（每次启动一个文件，文件名时间精确到毫秒）、`settings/<工作区hash>/settings.properties`；另有兜底工作区 `<jar 同级>/default_workspace/`。
`.dlqq` 与 `default_workspace` 创建/写入失败时直接启动失败（弹窗 + stderr + 退出码）；不再往工作区写 `.dlqq`。
`.dlqq` 创建后自动设置为 Windows 隐藏属性（dos:hidden）。
`.dlqq` 内除当前工作区缓存目录内容外，AI 任何权限下都禁止读写；工作区不能位于 `.dlqq` 下。
支持 MCP 2026-07-28+ 客户端的 `server/discover` 探测（返回兼容的 2025 系协议版本，客户端回落 initialize）；
**JAR 替换监视（变相热加载）**：运行中每 **2 秒**比对内存 `BuildInfo.BUILD_TIME` 与 JAR 清单 `Build-Time`，
不一致（或 JAR 修改时间/大小变化）即记录英文日志、弹出置顶提示窗，**拒绝新工具调用**；
前台工具调用最多等待 **5 秒**（超时强制退出），**后台（no_wait）调用不等待、直接随进程结束**；
提示窗**至少保留 2 秒**；随后进程退出，客户端（DSH）自动重连并拉起新进程加载新 JAR。
构建/替换 JAR 前仍建议先停止服务。

服务端控制台：启动时（有图形环境）**默认隐藏到系统托盘**、不弹出窗口；**单击**托盘图标或托盘菜单
"显示控制台"打开窗口。菜单栏显示中文名「鸫楼桥鹊MCP服务器控制台」（下拉含隐藏/退出），**中间显示「联系作者：
https://www.donglouqiaoque.com」并可点击打开默认浏览器**，右侧为刷新按钮（留右边距）；
**表格上方为选项卡：`会话管理` / `配置` / `工具管理` / `BLE` / `UART` / `USB` / `WiFi` / `环境` / `版权鸣谢`**；外设无需单独窗口，所有设备都在对应选项卡
内以设备列表 + 面板展示与管理（UART 按 COM、USB 按 `VID:PID:bus:address`、BLE 按 MAC、WiFi 按网卡/SSID）。
`配置` 选项卡按会话或工作区选择（活跃会话 + `workspace_config.json` 中登记过但当前无活跃会话的工作区；同一工作区的会话共用一份 `settings.properties`），表格展示全部配置属性、当前值与默认值；
双击「当前值」单元格保存，选中行后可「恢复默认」；「恢复默认」右侧的「恢复全部」始终可用，可将当前工作区所有配置属性恢复默认值（执行前会确认）；切换会话或工作区会重新读取对应工作区的配置文件。
`工具管理` 选项卡同样按会话或工作区选择，展示全部工具（含源码屏蔽工具）的资源风险、外设风险与当前可见程度；
默认值来自 `resources/tool_policy.txt`，修改写入该工作区 settings 目录下的 `tool_policy.txt`（与 `settings.properties` 同级），
可修改风险等级和已注册工具的可见程度（完全访问 / 仅可查看 / 已被隐藏）；源码屏蔽工具可见程度固定为“源码屏蔽”，只能改风险等级；
「恢复默认」右侧的「恢复全部」始终可用，可清除当前工作区全部工具策略覆盖、恢复内置默认值（执行前会确认）；
隐藏/恢复工具后推送 `notifications/tools/list_changed`。
`环境` 选项卡展示 Java（版本/供应商/安装目录/JAR/堆内存）与 Python（解释器/版本/pip/bleak）信息，
并提供打开目录、安装/更新 BLE 依赖、重建 Python 环境（调用 `.runtime\make_python_env.bat`）的入口。
`版权鸣谢` 选项卡展示作者/联系作者、版本与**按系统时区显示**的构建时间；许可正文**动态读取**
`THIRD-PARTY-NOTICES.txt`（**jar 同级目录，即 `.runtime`**；界面显示实际读取路径）；文件不存在时文本框显示
"未找到"及期望路径，「打开第三方许可文件」会弹出错误；另提供打开部署目录/作者网站按钮。
菜单栏右侧的**刷新按钮刷新"当前选中选项卡"的内容**：会话管理刷新表格；配置重新读取属性列表；工具管理重新读取工具策略；BLE 刷新后端状态/设备列表；UART、USB 重新枚举
设备；WiFi 刷新网卡与连接状态；环境重新检测 Java/Python 信息；版权鸣谢重新读取许可文件。各选项卡内部不再单独提供"刷新"按钮。
系统标题栏标题文字置空；表格按最后活跃时间倒序展示"活跃会话 + workspace_config.json 中出现过的工作区"两类行
（列：会话键/工作区/来源/资源权限/操作权限/最后活跃 `yyyy-MM-dd HH:mm:ss`），资源权限/操作权限/最后活跃三列（含表头）居中；
**工作区（仅会话行）与资源/操作权限可直接双击表格单元格编辑**（工作区单元格带目录选择按钮，权限为下拉选择），
提交后立即生效；仅**实际切换/修改**权限值或工作区路径才算一次操作，打开下拉框/目录选择框后以原值关闭不写配置、不更新时间；
点击表格空白处取消选中；底部显示部署目录/运行时目录/日志文件/会话数与表格行数，长路径可横向滚动；
点击关闭按钮重新隐藏到托盘，只有"退出服务器"才会终止 MCP 服务器进程。

## 六、工具速查

共 **118** 个工具，实现见 `mcp/tools/`，逐个参数说明见 [README.md](README.md)。
项目构建/芯片/运行/调试类 13 个工具中已恢复 `dlqq_build_project`（资源风险，无需每次审批），其余 12 个仍源码屏蔽；
脚本路径设置 13 个工具中已恢复 `dlqq_set_build_script`（资源高危，需要审批），其余 12 个仍源码屏蔽；
工具管理 5 个工具中仅保留 `dlqq_mcp_refresh_tools`，`hide/show/reset/run` 暂时源码屏蔽；
`dlqq_test_hot_reload` 同样屏蔽；后台程序管理工具按新命名（`dlqq_kill_command` 等）。

| 分组 | 数量 | 说明 |
|------|------|------|
| 文件操作 | 21 | 读写/创建/覆盖/追加/行级编辑/文本替换/复制移动重命名/BOM/二进制/zip |
| 目录操作 | 8 | mdir、mdirs、树形列出、递归复制等 |
| 搜索 | 4 | 文件/目录/目录内文本/单文件文本 |
| 对话框 | 3 | 文本输入、确认、文件选择（Swing） |
| UART | 5 | 控制台选项卡；按 COM 名寻址，多串口并发、收发/日志 |
| BLE | 11 | 控制台选项卡（Java Swing）；含 `dlqq_check_bluetooth_open`；Python 无界面 bleak 后端，按 MAC 寻址 |
| USB | 5 | 控制台选项卡；usb4java，按 `VID:PID:bus:address` 寻址 |
| WiFi | 5 | 控制台选项卡；netsh / nmcli，按网卡/SSID 操作 |
| 鼠标 / 键盘 / 屏幕 | 14 | `java.awt.Robot` 模拟 + `dlqq_capture_screen` |
| 图片 / Office | 3 | 图片转换、Office 读取与转换 |
| 时间 / 计算器 | 4 | 时间 1 + 计算器 3 |
| 命令执行与后台程序 | 18 | 命令 3 + cmd/powershell/pwsh 各 5 个后台管理（`dlqq_kill_*` 等新命名） |
| MCP 数据/属性 | 13 | 版本/属性读写、工作区、清理数据/缓存/日志；工具管理仅保留 `dlqq_mcp_refresh_tools`（hide/show/reset/run 已源码屏蔽） |
| 项目构建 / 脚本设置 | 2 | `dlqq_build_project`（资源风险 R，配置后直接执行）；`dlqq_set_build_script`（资源高危 H，设置脚本需审批） |
| 工具列表 / 环境 | 2 | `dlqq_get_tool_list`、`dlqq_get_tools_env` |

**动态工具管理（暂时只保留刷新）**：`dlqq_mcp_refresh_tools` 保持完全访问，仅推送 `notifications/tools/list_changed`；`dlqq_mcp_hide_tools`、`dlqq_mcp_show_tools`、`dlqq_mcp_reset_tools`、`dlqq_mcp_run_tools` **源码屏蔽**。`mcp.tools.excluded` 配置仍存在，但当前没有工具可修改它；HTTP 模式下刷新需要客户端已打开 SSE 长连接。

**工作区**：`dlqq_workspace_set` / `dlqq_workspace_get` / `dlqq_workspace_reset` / `dlqq_workspace_permission_set` / `dlqq_get_cwd_path`；启动参数 `--workspace <dir>`。默认工作区 = 进程 CWD，CWD 不可用或需要回退时使用 `<jar 同级>/default_workspace`（CWD 不承担回退职责）。**工作区路径不持久化**。权限分两套配置：`permission.resource.level`（资源权限，新工作区默认 chat）与 `permission.operation.level`（操作权限，默认 chat）；其它配置（`script.*`、`response.max.size`、`mcp.tools.excluded` 等）持久化在 `<jar 同级>/.dlqq/settings/<工作区hash>/settings.properties`，切换工作区自动重载。所有工具的相对路径、脚本执行目录、AI 规则基准都基于工作区解析；缓存、数据、设置的最小单位都是工作区（不再区分会话），缓存位于 `<jar 同级>/.dlqq/cache/<工作区hash>/`，AI 只能读写该工作区缓存目录内的内容。

**脚本工具规则**：`dlqq_build_project` / `dlqq_rebuild_project` / `dlqq_chip_*` / `dlqq_*erase_download` / `dlqq_run_project` / `dlqq_debug_project` 等**没有任何内置默认脚本路径**；未配置时直接报错“需要先配置工具路径”，并提示对应的 `dlqq_set_*_script`。配置写入 `<jar 同级>/.dlqq/settings/<工作区hash>/settings.properties` 的 `script.*` 项。
设置脚本路径只能使用 `dlqq_set_build_script`（**资源高危，需要用户审批**）；`dlqq_set_mcp_properties` / `dlqq_reset_mcp_properties` 对 `script.*`、`permission.*`、`tool.policy.*`、`mcp.tools.*` 等保护属性**直接拒绝，不进入审批**。执行已配置的 `dlqq_build_project` **属于资源风险操作**，工作区内可直接执行，无需每次审批。

## 七、AI 规则文件

| 文件 | 作用 |
|------|------|
| `.aiignore` | AI 忽略的文件/目录 |
| `.aiwriterules` | 禁止 AI 修改（写保护） |
| `.aireadrules` | 禁止 AI 读取（读保护） |

语法为 Gitignore 风格（`*.log`、`build/`、`/config.txt`、`!important.log`、`**/*.tmp`）。
生效范围：工程内严格遵守；工程外弹 JFrame 由用户确认；单文件操作不受规则约束。

## 八、热加载与动态工具逻辑（重要）

### 结论

- JVM 不支持运行中重载已加载的类：**换 JAR 必须重启服务端进程**。
- 重启服务端会断开 SSE 长连接；HTTP 客户端（DSH，协议 2025-11-25）断开后**不会**自动重连 SSE、也不会重新 `initialize`/`tools/list`，所以客户端工具列表仍是旧的，新增/删除的工具对客户端不可见，**必须重启客户端**。
- `notifications/tools/list_changed` 只在 SSE 存活时有效：服务端不重启、工具集变化时，推送后客户端约 2ms 重新 `tools/list`（已实测）。

### 两个判断工具

| 工具 | 说明 |
|------|------|
| `dlqq_get_mcp_version` | `构建时间（代码内，真实值）` 来自 `BuildInfo.BUILD_TIME`（templating-maven-plugin 编译期注入），替换 JAR 不重启不会变；`JAR清单构建时间（磁盘）` 读磁盘清单，替换后会变。两者不一致 = JAR 已换、进程仍跑旧代码 |
| `dlqq_test_hot_reload` | 返回写死的 `HOT_RELOAD_TEST_MARK`（当前 v4），改标记重新构建可验证代码更新 |

### SSE 与重启（机制）

- HTTP 模式：客户端 `GET /mcp` + `Accept: text/event-stream` 建立 SSE；`HttpTransport.sseClients` 保存连接，`notifyToolsListChanged()` 遍历推送。
- 重启 = 旧进程退出 + 新进程重新 listen：旧 TCP 连接被内核关闭、连接列表清空；新进程无法继承旧连接（TCP 连接属于旧进程的 socket）。
- DSH 实测：服务端重启后不重连 SSE、不 `initialize`，只在调用时发 POST `tools/call` → 无推送通道（`notifyToolsListChanged()` 返回 false）。

### 验证流程

1. 构建新 JAR，记录 `BuildInfo.BUILD_TIME`；
2. 停 HTTP 服务，确认端口释放；
3. 覆盖部署目录 JAR；
4. 重启服务；
5. 调 `dlqq_get_mcp_version`：代码内时间 == 磁盘清单时间 == 新值 → 已加载；代码内仍为旧值 → 未生效；
6. 如果涉及工具增删/改名/schema 变化，重启客户端后再确认工具列表。

### 动态工具管理（暂时只保留刷新）

- 当前仅注册 `dlqq_mcp_refresh_tools`：向客户端推送 `notifications/tools/list_changed`，不改变工具集；HTTP 模式下需要客户端已打开 SSE。
- `dlqq_mcp_hide_tools` / `dlqq_mcp_show_tools` / `dlqq_mcp_reset_tools` / `dlqq_mcp_run_tools` **源码屏蔽**，暂时不能隐藏/恢复/按名称调用工具。
- `mcp.tools.excluded` 配置仍会被服务端读取，但当前没有工具可修改它。

## 九、已知限制

- **PDF/TLS/zipfs 等**：依赖**完整版 JRE**；精简 jlink 镜像缺模块时会报错（历史 `Could not initialize class org.apache.pdfbox.io.IOUtils` 即此类）。完整 JRE 生成方式见“二、运行环境要求”。
- **硬件相关工具**（串口/BLE/USB）在没有对应硬件时只能验证窗口与管理类接口，收发的端到端链路需实机验证。
- **PDF 写入**：工具侧不支持（`dlqq_convert_office_file` 目标格式不含 PDF），需先转 DOCX 再用外部工具导出。

## 十、参考资料

### MCP 协议

- MCP 官方文档：<https://modelcontextprotocol.io/>
- MCP 规范（含 Streamable HTTP 传输定义）：<https://modelcontextprotocol.io/specification/2026-07-28>

### 各 Agent 客户端接入文档

| 客户端 | 官方文档 | 本项目配置文件 |
|--------|----------|----------------|
| **DeepSeek Harness (DSH)** | 见本文“三、MCP 客户端配置”（插件 `@deepseek-ai/dsh-mcp-client`） | `C:\Users\<用户名>\.dsh\profiles\desktop\cordis.patch.yml` |
| Qwen Code | <https://qwenlm.github.io/qwen-code-docs/zh/users/features/mcp/> | `settings.json` |
| Claude Code | <https://code.claude.com/docs/en/mcp> | `.mcp.json` / `claude mcp add` |
| Cline | <https://docs.cline.bot/mcp/mcp-overview> | `cline_mcp_settings.json` |
| Cursor | <https://cursor.com/docs/mcp> | `.cursor\mcp.json` |
| VS Code (GitHub Copilot) | <https://code.visualstudio.com/docs/agent-customization/mcp-servers> | `.vscode\mcp.json`（`servers` + `type`） |

> 以上链接均为 2026-10 实测可访问；若后续客户端改版导致失效，以各客户端官网文档导航为准。
