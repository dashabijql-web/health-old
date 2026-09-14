# AGENTS.md

本文件是 `health` monorepo 的项目协作约束和运行说明。根目录 `README.md` 仅提供项目概览与快速入口。代码、配置和实际运行结果是运行事实；若与本文不一致，应先确认预期行为，再修正实现或更新本文。

## 仓库边界与协作规则

- 根目录是唯一 Git 仓库，远端为 `https://github.com/dashabijql-web/health.git`。`HealthShow` 和 `HealthData` 均为子目录，所有 Git 操作在根目录执行。
- `/Users/jiangqianli/Documents/code/health` 固定连接新库 `health_new`；同级 `health-old` 固定连接老库 `health`。两个 checkout 独立修改、运行和验收，禁止跨目录混改或通过请求切换数据库。
- 未经用户明确要求，不提交、不推送、不修改 `origin`。
- 仅当跨模块约束、公共入口、认证、数据源、协议、运行方式或测试门禁发生变化时更新本文；普通局部改动以代码和测试为准。
- 除根目录 `README.md` 和本文件外，不新增 Markdown 文档；测试产生的临时 Markdown 文件须在交付前清理。

## 项目结构

```text
health/
├── HealthShow/                     Vue 3 前端
├── HealthData/                     Spring Boot 后端
├── firmware/esp32c3-wifi-watch/   ESP32-C3 手表原型
└── tools/                          macOS 启动、数据库和手表诊断工具
```

前端 `HealthShow` 使用 Vue 3、Vite、Vuex、Vue Router、Element Plus 和 ECharts。后端 `HealthData` 使用 Spring Boot、Java 21、MyBatis-Plus、Sa-Token、Redis、Netty、SQL Server 和 Actuator，默认构建为可执行 JAR，不使用 WAR 或外部 Tomcat。IDEA 本地调试直接运行 `HealthApplication.main()`；交付产物通过 `mvn package` 构建并用 `java -jar target/health-*.jar` 启动。依赖版本以 `package.json`、锁文件和 `pom.xml` 为准，未经任务要求不升级依赖。

## 端口与地址

| 服务 | 默认地址 | 作用 |
| --- | --- | --- |
| 前端 | `http://localhost:9528/` | Vite 开发服务器 |
| 后端 HTTP | `http://localhost:8080/health` | Spring Boot API |
| 后端健康 | `http://localhost:8080/health/actuator/health` | 顶层 `status=UP` 才算可用 |
| 后端指标 | `http://localhost:8080/health/actuator/metrics` | Micrometer 指标 |
| 手表 TCP | `127.0.0.1:9000` | Netty 手表协议 |
| 手表 SCTP | `9001` | 默认关闭，仅 Linux 生产按需启用 |
| Redis | `127.0.0.1:6379` | 手表数据缓冲和 Sa-Token 存储 |
| SQL Server | `127.0.0.1:1433` | 业务数据库 |

前端 `/dev-api/*` 由 Vite 代理到后端 `/health/*`，前端业务代码不应绕过 `HealthShow/src/utils/request.js` 直接创建请求客户端。

## 登录与权限

- 本地默认可登录账号是 `admin / admin123`；登录页也预填该账号。实际部署应通过数据库和环境变量管理密码，不要把生产密码写进仓库。
- `POST /health/auth/login` 校验账号密码并由 Sa-Token 生成 token。前端用 Cookie `User-Token` 保存 token，后续请求从 Cookie 读取并放入 `satoken` 请求头；token 不以 localStorage 作为主存储。
- `/auth/login`、`/auth/logout` 和 `/error` 是认证白名单；其他业务请求必须通过 Sa-Token 登录校验。
- 当前项目禁止通过请求头或 Cookie 改变数据库；遗留的数据源路由类型仅用于兼容现有代码，不能恢复前端切库入口或请求级数据库覆盖。
- CORS 默认允许 `http://localhost:9528` 和 `http://127.0.0.1:9528`，允许凭证和 `satoken` 请求头；生产环境应收紧来源。

## 前端路由

业务路由主要定义在 `HealthShow/src/router/app-routes.mjs`、`health-monitor.mjs` 和 `alert-management.mjs`，由 `router/index.js` 统一注册，并复用懒加载的 Layout。登录和 `/auth/info` 返回的 `routes` 权限码用于前端菜单过滤；路由本身是静态注册的，实际接口访问权限仍由认证守卫和后端权限共同保证。历史入口只做隐藏重定向，不新增第二套页面实现。安全指挥中心和统一管控通过 `VITE_SAFETY_COMMAND_V2`、`VITE_UNIFIED_CONTROL_V2` 切换，保持公共路径不变；懒加载导航失败会通过路由错误回调记录到浏览器控制台。

## 后端数据流

手表数据流为：TCP 字节流 -> `WatchProtocolDecoder` -> `WatchDataHandler`/协议处理器 -> 设备与人员绑定解析 -> `DataProcessService` 及 `service/watch/*`。绑定数据先进入 Redis 缓冲并异步批量写入 SQL Server 月分表；体征阈值判断和设备行为预警在业务处理链路中独立执行，不以本批数据先完成落库为前提。

- `DataProcessService` 负责外部协议入口；设备上下文、落库、健康预警和原始报文职责位于 `service/watch/*`。
- Redis List、实时快照、在线集合、失败重试和 dead-letter key 按逻辑数据源带 `old`/`new` 后缀隔离；当前项目正常流量使用 `old` 后缀，兼容逻辑仍可清理和刷写两类 key。实时查询优先读取在线窗口内的 Redis 快照，兼容旧快照，Redis 不可用时回退 SQL；快照保留 48 小时并定时清理，反序列化失败进入 dead-letter。
- 协议处理使用有界 `taskExecutor`（线程名前缀 `watch-data-`、队列满时 `CallerRunsPolicy`）回压，缓冲刷写默认 `@Scheduled(fixedDelay = 5000)`；不得在 Netty 线程中同步执行大批量数据库写入。
- 健康流水表按月命名：`health_record_YYYYMM`、`warning_record_YYYYMM`；月表调度器提前建表并执行 `sp_update_monthly_views`。30 天大盘优先读取 `health_user_daily_summary`、`warning_daily_summary` 和 `warning_user_daily_summary`，以 `dashboard_daily_refresh_state` 判断日期覆盖是否完整，不完整时回退分月原表；结构和刷新过程由 `dashboard_daily_summary.sql` 显式迁移，调度器每 5 分钟刷新当天。新增健康字段时必须同步月表、视图、存储过程、实体、Mapper INSERT、分月直查 SQL、Service 返回值和前端绑定。
- 动态表名只能来自 `TableNameUtil` 或已校验的月表白名单，禁止将用户输入直接拼接 SQL。

## 项目与数据库边界

当前 `health-old` checkout 固定连接老库 `health`；old/new 兼容路由键均映射到老库，禁止通过请求头、Cookie 或前端选择器切换到新库。新库项目位于同级 `health`。

数据库边界按项目划分，不再按 HTTP 请求动态切换：

| 项目目录 | 固定数据库 | 用途 |
| --- | --- | --- |
| `/Users/jiangqianli/Documents/code/health` | `health_new` | 当前新库项目、真实手表接入；业务数据稀疏或为空是允许状态 |
| `/Users/jiangqianli/Documents/code/health-old` | `health` | 老库项目、模拟器、演示数据和非空数据回归 |

配置默认值以 `HealthData/src/main/resources/application.yml` 为准。数据库密码通过 `DB_PASSWORD_OLD` 或 `DB_PASSWORD` 环境变量提供，不在代码、文档或日志中记录真实凭证。

固定数据库规则：

1. 当前 `health-old` checkout 的 HTTP、真实手表和模拟器均固定写入 `health`；`old/new` 两个兼容路由键都映射到同一个老库物理连接。
2. `HEALTH_ALLOW_REQUEST_SOURCE_OVERRIDE=false`，客户端不得通过 `X-Health-Data-Source`、Cookie 或前端选择器改变数据库。
3. 需要新库数据、真实手表接入或新库空态验收时，进入同级 `health` 项目并启动它自己的前后端；不要修改当前项目的环境变量临时连接新库。
4. 两个项目不能同时争用相同的 HTTP、前端、Redis 或 Netty 端口；并行运行时必须为其中一个项目显式分配独立端口和 Redis key 空间。
5. 老库用于模拟器、演示数据和非空数据回归；涉及数据密度的验收默认要求组织、人员、设备和业务流水存在，不能把新库允许为空的语义套用到本项目。
6. 老库迁移必须显式执行 SQL 脚本，不能在业务请求中自动建表。安全指挥中心事件表缺失应返回 `503` 并提示部署迁移；Druid SQL 防火墙会拒绝条件 DDL。需要同时变更新库时，应在同级 `health` 项目中独立执行和验收。

## 指挥中心与事件处置

- 安全指挥中心和统一管控共用 `/command-center/incidents` 事件模型，事件定位键必须是 `warningId + occurredAt`；分月预警表场景禁止只用裸 `warningId`。
- 查询、详情、确认、分派、处理、误报、时间线和外部动作共用服务端事件状态。迁移脚本须在两个项目中分别执行，并明确作用于各自数据库。
- `IncidentCommandDrawer` 是两页共享的处置入口，跨页保留事件、人员、区域和项目上下文；成功后重新读取服务端状态，不能只修改前端数组。
- 呼叫、广播、撤离只能在具体事件详情中发起。外部系统未接入时记录 `NOT_CONFIGURED` 审计，界面不得显示“已下发”。班前复检、责任人、SLA、设备中断等后端事实未接入时显示“未接入/未分派”，不得用 `0` 或虚构人员填充。
- `GET /command-center/dashboard-summary` 是指挥摘要权威来源。`period=day|week|month` 返回对应周期的 `periodNew`；预警总数、高危待办、待办总数、未分派和超时必须以后端聚合为准，不能从前端已加载事件条数推算。
- 安全指挥中心可以展示真实设备覆盖、去重后的重点风险人员、事件范围和趋势，但不得恢复模拟体征趋势、均摊处理率、样本冒充全量、重复事件列表或无具体事件上下文的批量呼叫。未经用户确认，不做大幅信息删减或恢复。

### 风险事件分类与处置不变量

目标是将不同触发机制结构化区分，同时复用统一处置生命周期：体征越界属于 `HEALTH_THRESHOLD`，手表主动上报属于 `DEVICE_ALARM`，趋势预测属于 `TREND_WARNING`。`SOS` 只是 `DEVICE_ALARM` 下的一种事件代码，任何高危体征记录都不得被页面或接口冒充为 SOS。

不可破坏的口径：

- “已读”“已确认”“已处理”“已关闭”是不同动作；没有独立通知投递模型前，页面不得提供虚假的已读状态。
- 事件严重程度与事件来源正交：高危不等于 SOS，设备报警也不一定都是高危。
- 设备报警不配置数值阈值，但可以配置启用状态、严重级别、SLA 和通知升级策略；尚未接入的策略必须显示未接入，不能假装已经执行。
- 历史无结构化分类字段的数据可以在查询层兼容推断；所有新增记录必须直接写入结构化来源和代码。

人员快速处置：

- `GET /employee/command-search` 按姓名、工号、手机号或 IMEI 搜索当前项目数据库中的人员、绑定设备和 Netty 在线状态。
- `PersonDetailDrawer` 是指挥中心、统一管控和职工健康画像共用的人员入口。已绑定手表才启用文字消息和单人语音；未绑定设备必须禁用下发。
- SOS 只能表示手表主动上报的求救事件，管理端不得伪造“发送 SOS”。人员抽屉的应急处置只能关联该人员已有未处理预警，并以 `warningId + occurredAt` 打开事件抽屉。

## 实时监控与健康画像口径

- `/health-monitor/real-time` 在线窗口默认 15 分钟，由 `HEALTH_REALTIME_ONLINE_WINDOW_MINUTES` 配置；体征新鲜度默认 5 分钟，由 `HEALTH_REALTIME_FRESHNESS_MINUTES` 配置。
- 心率分析的部门异常图直接展示各部门偏低、偏高心率记录数，不在前端换算百分比；数值轴使用“条”，悬浮提示展示两类记录数及异常合计。
- 实时状态使用 `normal`、`warning`、`stale`、`no_data`。刷新失败保留上次数据并显示失败/缓存状态；不能用当前请求时间掩盖设备采集时间，也不能丢掉后端 `stale=true`。
- 快照按人员在窗口内为每个指标取最新非空值；筛选、总数、摘要和分页由后端完成，不能用当前已加载数组长度冒充总人数或异常数。
- 阈值统一来自 `alert_config`；后端返回 `warningReasons` 和 `indicatorStates`，前端只呈现。实时读数队列不是预警生命周期，确认、分派、处理和误报必须走预警/事件处置链路。
- `GET /realtime/health-snapshot` 的异常人数按人去重，主值写成“异常/覆盖”，并显示极值、覆盖人数、窗口、新鲜度和 `NORMAL/PARTIAL/STALE/NO_DATA`；禁止用群体均值判断个人异常或用事件列表估算实时异常人数。
- 统一管控的体征异常卡进入专项分析页时必须携带下钻上下文；心率异常使用 `period=day&focus=current-anomaly`，进入后定位实时异常人员。名单通过 `GET /realtime/online-users?status=warning&indicator=heartRate` 服务端分页，必须与健康快照共用在线窗口、新鲜度、`alert_config` 和 `indicatorStates` 口径，不能用近 2 小时专项列表解释 15 分钟快照。
- 职工健康画像展示身份、处置、当前体征及逐指标采集时间、数据新鲜度、7 日趋势、今日活动和权威预警轨迹。不得使用装饰人体热区、前端虚构医学百分比或把动画示意称为真实 ECG。
- 历史趋势走 `GET /api/health/record/history/trend`，按员工和日期范围直查相关月表；明细走 `/api/health/record/page` 服务端分页。7 日内返回原始记录点，超过 7 日按日聚合，并返回完整样本数，不能截取前 200 条或当前页自行计算。历史区提供“查询”按钮；结束日期为今天时随画像刷新同步，纯历史日期不轮询。
- 画像自动刷新默认 30 秒，手动刷新、切换员工和自动刷新都要重置倒计时，且不允许并发重复请求。画像页联系处置复用 `PersonDetailDrawer` 的 `contact` 模式，不重复铺设体征、趋势和完整画像入口。

## 本地启动

### macOS

仓库提供：

```bash
tools/run-redis-mac.sh
tools/run-backend-mac.sh
tools/run-frontend-mac.sh
tools/sqlcmd-docker.sh
```

Mac 原生运行前需准备 Java 21、Maven、Node/npm、Python 和 Redis；SQL Server 使用名为 `local-mssqlserver2022` 的 Docker 容器映射到 `1433`。后端脚本从容器读取 SQL 密码并固定使用老库 `health`，前端脚本默认 `VITE_TARGET=http://localhost:8080`。启动后逐项检查 `6379/8080/9000/9528/1433`，停止后再次检查监听端口，不能只凭进程信息判断已停止。

Windows 不提供仓库级启动脚本。手表原型和协议探针可在 PlatformIO/串口环境中单独运行。

## 模拟器与手表协议

- 模拟器文件是 `tools/watch_tcp_simulator_1000.py`，默认连接 `127.0.0.1:9000`，默认 1000 个手表；启动前必须确认没有其他模拟器实例。
- 当前项目中的模拟器数据固定写入老库 `health`，用于演示数据和非空回归；真实手表验收应在同级新库项目中进行，并确保模拟器已停止。
- 手表登录后，后端下发 `BP33` 工作模式，并用 `BP86/BP87` 关闭设备内部周期，避免设备和服务端两套调度重叠。后端每 60 秒只下发一项测量，按 `BPXL -> BPXY -> BPXZ -> BPXT` 轮换，不并发启动传感器，也不把定位 `BP16` 混入健康周期；每项指标约每 4 分钟触发一次。
- `BPXL/BPXY/BPXZ/BPXT` 也可用于人工立即测量。对应的 `APXL/APXY/APXZ/APXT` 只是命令确认，实际数值仍以随后到达的 `AP49/AP50/APHT/APHP` 为准；未佩戴时数值可能为 `0`，部分真实手表的 `APHP` 还会固定回传 `0,0,0,95,0.0,0.0`，其中 `95` 是未佩戴占位值，不能当作有效血氧。
- 厂家协议中 `AP03` 状态串和 `AP50` 温度包第二参数都标注为电量；但当前真实手表实测 `AP03` 电量与设备状态一致，而 `AP50` 可能上报错误的 `0`。因此电量以 `AP03` 三位字段为当前设备权威来源，`AP50` 仍用于温度，且其 `0` 电量不得覆盖已有有效值或触发低电告警。
- ESP32-C3 原型位于 `firmware/esp32c3-wifi-watch/`，通过 PlatformIO 构建。`src/main.cpp` 默认 TCP `9000`，Wi-Fi、服务器地址和 IMEI 通过编译宏配置，不能提交真实 Wi-Fi 密码。
- 原型协议包含 `IW*AP00*<IMEI>#` 登录和 `IW*APHP*...#` 健康数据；`firmware/esp32c3-wifi-watch/tools/protocol_probe.py` 可向后端发送探针。默认占位地址是 `192.168.1.100`，现场按实际局域网修改。

## 测试与验收

前端测试门禁集中在 `HealthShow/scripts/health-test-runner.mjs`；后端测试使用 Maven：

```bash
cd HealthShow
npm run test:fast
npm run test:frontend
npm run test:integration -- --source old
npm run test:full -- --source old
npm run audit:structure
npm run audit:visual
npm run build
```

```bash
cd HealthData
mvn test
```

当前项目只验收 `old`，数据密度检查默认要求业务数据非空；登录和配置写入仍必须执行。新库空态验收在同级 `health` 项目内独立运行。`audit:visual` 用 Playwright 覆盖桌面与移动视口，布局问题以截图和布局摘要为证据。会争用服务或测试数据的认证、端到端和流水线测试不得并行运行；需要浏览器时先完成 preflight。完整 profile 和命令以 `package.json` 及测试 runner 为准。

最小运行验收：

1. 使用 `curl` 检查前端 `9528`、后端 `/health/actuator/health` 和响应状态，Actuator 顶层 `status` 必须为 `UP`。
2. 登录后确认 `satoken` 认证和 Cookie 正常，不发送数据库切换请求头。
3. 确认页面摘要、分页 `total` 和状态符合老库非空数据回归语义，不把缺失业务数据误判为正常空态。
4. 需要设备链路时确认 TCP `9000`、模拟器单实例、Redis 队列和数据库月表写入。
5. 停止服务后确认监听端口和项目进程已释放。

## 观测、性能与安全

- Actuator 指标入口是 `/health/actuator/metrics`。排障重点关注数据库路由、手表链路、Redis 缓冲、预警、AI 调用和 SQL 耗时。
- 慢查询阈值由 `HEALTH_SLOW_QUERY_THRESHOLD_MS` 控制，默认 `500ms`。排障先看 Actuator、后端日志、Redis 队列和 SQL，再看 controller 日志。
- 前端性能问题分别判断 JavaScript/Node、浏览器渲染与 ECharts、网络/视频解码，不把“资源占用”当成单一指标。保留产品需要的视觉信息，不为降低负载擅自删业务内容。
- AI 聊天支持受控 Text2SQL 和报告能力。SQL 动态列结果必须经过白名单、参数化和结果集封装；禁止把用户输入直接拼 SQL，禁止在提示词或日志中泄露 token、密码、个人敏感健康信息。AI 规则提示不是医学诊断，AI 报告是用户主动触发的二级能力。
- 外部呼叫、广播、撤离和其他设备控制必须保留审计和明确的未配置状态，不能用前端成功提示冒充外部系统已执行。

## 变更检查清单

- 新增接口：同步 controller、service、mapper/DTO、权限、固定数据库边界、响应状态和前端 API；补充集成测试。
- 新增健康字段：按“月表/视图/存储过程 -> entity -> mapper insert/select -> service -> API -> 前端”逐层核对。
- 修改实时页：确认在线窗口、新鲜度、逐指标最新值、服务端分页和 `normal/warning/stale/no_data`。
- 修改数据库连接或兼容路由：当前项目只能连接老库 `health`，并在当前项目验证老库非空场景；新库变化在同级 `health` 项目独立处理。检查 Redis key、手表/模拟器路由和权限，不得恢复请求级切库。
- 修改指挥中心或人员处置：保留复合事件键、项目上下文、服务端状态刷新和未接入/未配置语义。
- 修改页面布局：至少运行对应结构门禁和五档视觉审计，不以单一可见浏览器视口口头判断。
- 修改后端异步链路：确认使用有界 `taskExecutor`，不会回退到无限创建线程的 `SimpleAsyncTaskExecutor`。
- 任务结束：在根目录检查 `git status` 和 `git diff --check`；保留任务开始前已有改动，不引入与本次任务无关的新改动，并清理本次测试生成的临时 Markdown 文件。
