# AI 旅行规划助理平台

Spring Boot 3 + Vue 3 单仓项目。文档：

- `travel-planning-prd.md` — 产品需求
- `travel-planning-api.md` — 接口契约（响应码、字段以此为准）

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17（`JAVA_HOME` 必须指向 JDK 17） |
| Maven | 3.9+ |
| Node.js | ^22.18 或 >=24.12 |
| PostgreSQL | 16 |
| Redis | 7 |

## 环境变量

| 变量 | 说明 | 必需 |
|---|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek API Key。未配置时对话模块自动降级为规则澄清桩，链路仍可跑通 | 否（不配则降级） |
| `DASHSCOPE_API_KEY` | 通义千问（DashScope）API Key，DeepSeek 失败后的兜底模型；两者都不配时攻略生成使用模板 | 否（不配则降级） |
| `QQ_MAIL_USERNAME` | QQ 邮箱地址，用于发送注册验证码 | 否（不配则 dev 回显） |
| `QQ_MAIL_AUTH_CODE` | QQ 邮箱 **SMTP 授权码**（非 QQ 密码）：QQ 邮箱 → 设置 → 账户 → 开启 SMTP 服务后生成 | 否（不配则 dev 回显） |
| `DB_PASSWORD` | 生产环境数据库口令（dev 使用 `application-dev.yml` 内置值 `1234`） | prod 是 |
| `REDIS_PASSWORD` | 生产环境 Redis 口令（dev 内置值 `123456`） | prod 是 |
| `CORS_ALLOWED_ORIGINS` | 生产环境允许的前端来源，逗号分隔 | prod 是 |
| `LINK_SIGNING_SECRET` | FR-8 转链 HMAC 密钥。prod 未配置时使用进程内随机密钥并告警，重启后旧转链失效 | prod 建议 |
| `OFFLINE_PACK_DIR` | FR-9 离线包文件目录，prod 默认 `/app/data/offline-packs`（compose 已挂命名卷） | 否 |
| `ADMIN_USER_IDS` | 运营接口白名单（逗号分隔用户 ID）；未配置时仅 `L3` 用户可访问 | 否 |
| `FLIGHT_PRICE_URL` / `HOTEL_PRICE_URL` | FR-6 机票/酒店价区间数据源（约定返回 `{min,max,currency,freshness}`）；未配置则对应数据块为 `unavailable` | 否 |
| `PRICE_SOURCE_TOKEN` | 上述价格源的 Bearer Token | 否 |

## 启动步骤

1. 起依赖（Postgres + Redis）：

   ```bash
   cd backend
   docker compose up -d postgres redis
   ```

2. 起后端（默认 `dev` profile，端口 8080）：

   ```bash
   # Windows CMD
   set JAVA_HOME=C:\path\to\jdk-17
   set DEEPSEEK_API_KEY=sk-xxxx
   mvn spring-boot:run

   # bash
   export JAVA_HOME=/path/to/jdk-17
   export DEEPSEEK_API_KEY=sk-xxxx
   mvn spring-boot:run
   ```

   启动日志应显示 Flyway 应用 `V1`~`V19` 迁移。

3. 起前端（端口 5173，已代理 `/api` → `localhost:8080`，含 WebSocket）：

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## 生产部署

服务器上 `git clone` 后**一条命令起全栈**（Postgres + Redis + 后端 + 前端 nginx）。首次构建全部在容器内完成（多阶段 Dockerfile），**服务器无需预装 JDK / Maven / Node，本地也无需先打包**：

```bash
cd backend
cp .env.example .env      # 可选：填入真实密钥（.env 不入库）
docker compose up -d --build
```

起 4 个容器：`travel-postgres` / `travel-redis` / `travel-api`（8080）/ `travel-web`（80）。访问 `http://<服务器IP>/` 即前端；前端**同源**反代 `/api` → `backend:8080`，对话 WebSocket 走 `/api/ws`（nginx 已配升级头，见 [frontend/nginx.conf](frontend/nginx.conf)）。

### 密钥与降级

prod 配置（[application-prod.yml](backend/src/main/resources/application-prod.yml)）**不含任何密钥**，全部由 `backend/.env` 注入（compose 自动加载同目录 `.env`，变量写成 `${VAR:-默认值}`，**不建 `.env` 也能起**，只是功能降级）：

| 变量 | 不配的后果 |
|---|---|
| `DEEPSEEK_API_KEY` | 对话意图分析降级为规则桩（能跑，但解析变笨） |
| `QQ_MAIL_USERNAME` / `QQ_MAIL_AUTH_CODE` | 验证码接口返回 `50300`，邮箱注册不可用 |
| `DB_PASSWORD` / `REDIS_PASSWORD` | 取默认值（`1234` / 空） |

> 本地开发的 key 放在 `application-dev-local.yml`（已 gitignore），**不随仓库上传**；prod 只认 `backend/.env`。

### 端口与网络

- `web` 的 80 端口对外；`backend` 的 8080 对外（保留 Swagger 直连：`http://<IP>:8080/swagger-ui/index.html`）。
- Postgres / Redis 仅绑定 `127.0.0.1`，不对公网暴露（容器间通过服务名互访）。
- 同源反代下浏览器不触发跨域，`CORS_ALLOWED_ORIGINS` 可忽略；仅当前端与接口分处不同域名才需配置。
- 需 HTTPS 时，在最外层再加一层反向代理（Caddy / Traefik / 宿主 nginx）终结 TLS，转发到 `web:80`。

## 数据库迁移

`backend/src/main/resources/db/migration/`：

- `V1__init_user_profile.sql` — `t_user` / `t_user_profile` / `t_traveler`
- `V2__init_chat.sql` — `t_chat_session` / `t_chat_message`
- `V3__init_guide_post.sql` — `t_guide_post`（推荐攻略流内容表）
- `V4__seed_guide_posts.sql` — 推荐攻略流**演示种子数据**（15 条），与业务无关，可整表删除
- `V5__extend_guide_post.sql` — `t_guide_post` 补详情字段：`source_url` / `images` / `plan_sections`
- `V6__add_profile_trip_plan_fields.sql` — `t_user_profile` 补 `trip_days` / `budget_amount`（不参与完整性校验）
- `V7__seed_guide_post_details.sql` — 为 `V4` 那 15 条灌详情（图集 / 外链 / 四模块规划），**演示数据**，可整表清空
- `V8__init_guidebook.sql` — `t_guidebook`（用户生成的攻略）及活跃会话部分唯一索引 `uq_t_guidebook_active_session`
- `V9__init_knowledge_doc.sql` — `t_knowledge_doc` + `pg_trgm` 索引（目的地知识库）
- `V10__seed_knowledge_docs.sql` — 知识库**演示种子数据**
- `V11__add_user_generate_quota_date.sql` — `t_user.generate_quota_date`（每日生成配额跨天重置）
- `V12__guidebook_version.sql` — `t_guidebook_version`（只追加的版本快照），并重建活跃索引把 `editing` 纳入
- `V13__offline_pack.sql` — `t_offline_pack`（FR-9 离线包异步任务与文件元数据）
- `V14__realtime_data.sql` — `travel_data` 两列（攻略 + 版本快照）、`t_data_source_config`、`t_visa_policy`（FR-6）
- `V15__resource_slot.sql` — `t_resource_slot` 资源目录 + **演示种子数据**（成都/东京，不代表真实库存与价格，FR-8）
- `V16__knowledge_review.sql` — `t_knowledge_doc` 审核/来源字段、`t_guide_post.review_status`（FR-7）
- `V17__guidebook_comparison.sql` — `t_guidebook_comparison`，`t_guidebook` 增加 `comparison_id` / `variant`（FR-4）
- `V18__guidebook_node_status.sql` — `t_guidebook_node_status`（FR-10 出行期节点执行状态，不改攻略版本）
- `V19__trip_suggestion.sql` — `t_trip_suggestion`（FR-10 调整建议与去重键唯一索引）
- `V20__guidebook_review.sql` — `t_guidebook_review`（FR-11 复盘评价与统计快照，同一攻略 + 用户唯一）
- `V21__guidebook_share.sql` — `t_guidebook_share`（FR-12 分享快照与短码）+ `t_guidebook.source_share_id`

JSONB 写入依赖 JDBC 连接串上的 `?stringtype=unspecified`，请勿移除。

## 响应约定

- 成功：HTTP 200，`{"code":0,"message":"ok","requestId":...,"data":...}`
- 失败：HTTP 状态按错误码映射（如 `40100`→401、`40301`→403、`42200`→422、`42300`→423）
- 分页：`data: { list, page, pageSize, total }`
- 鉴权头：`Authorization: Bearer <token>`（Sa-Token 签发）

## 鉴权与登录安全

- 登录失败连续 3 次锁账号 30 分钟（Redis 自动解锁），计数含账号不存在的尝试以防账号枚举。
- 验证码：邮箱经 QQ SMTP 发送；未配置 SMTP 时 dev 回显、生产返回 `50300`。同一账号同场景 60 秒节流，每日上限 10 次。
- Sa-Token 会话由 `sa-token-redis-jackson` 持久化到 Redis（复用 `spring.data.redis` 连接），服务重启登录态不丢。

## 接口调试（Swagger UI）

- 地址：`http://localhost:8080/swagger-ui/index.html`
- 用法：先 `POST /api/auth/login` 拿 token → 点右上 **Authorize** 粘贴 token（无需 `Bearer ` 前缀）→ 直接 Try it out。
- 详见 PRD §7.10。

## WebSocket 对话

- 端点：`ws://<host>/api/ws/chat/{sessionId}?token=<token>`
- 发送：`{"content":"用户消息"}`
- 接收事件：
  - `{"event":"message_delta","messageId":...,"delta":"...","done":false}`
  - `{"event":"message_delta","messageId":...,"delta":"","done":true}`
  - `{"event":"message_done","messageId":...,"sessionStatus":...,"pendingQuestions":[...]}`
  - `{"event":"error","message":"..."}`

> 注：本期 `message_delta` 为服务端分块推送（先整段生成再按片下发），非模型 token 级流式。

## 攻略生成（FR-3）

- 触发：`POST /api/guidebooks/generate`（传 `sessionId` 或 `intent`），或确认会话 `POST /api/chat/sessions/{id}/confirm`（两者幂等，同一会话返回同一本攻略）。
- 查询：`GET /api/guidebooks/{id}`；失败重试 `POST /api/guidebooks/{id}/retry`（最多 3 次）；配额默认 3 次/天。
- 进度 WS：`ws://<host>/api/ws/guidebook/{guidebookId}?token=<token>`，事件为 `guidebook_progress` / `guidebook_done` / `error`；前端断线时每 2 秒轮询兜底。
- 降级链：DeepSeek → 通义千问（`DASHSCOPE_API_KEY`）→ 模板。**不配任何 key 也能产出 draft**，此时 `degradedMode=true`、来源标注“模板生成”。
- 知识库：PostgreSQL + `pg_trgm`（`t_knowledge_doc`，V9 建表 / V10 演示数据），不依赖 Milvus。
- 实时数据：Open-Meteo 天气 + open.er-api.com 汇率（均无需密钥）等，详见“实时数据接入（FR-6）”；失败只记录 `*_unavailable`，不阻断生成。
- 异步与恢复：进程内线程池 + DB 状态机 + Redis 进度；重启时超过 10 分钟仍为 `generating` 的任务自动置 `failed`，可重试。

## 攻略编辑（FR-5）

- 节点操作：改 / 增 / 删（`PUT|POST|DELETE /api/guidebooks/{id}/days/{day}/nodes[/{nodeId}]`）与跨天移动（`POST /api/guidebooks/{id}/nodes/{nodeId}/move`）。
- 状态机：`draft → editing`（首次编辑 / 调整 / 回溯）→ `confirmed`；`generating / failed / confirmed / executing / completed` 拒绝编辑并返回 `40900`。
- 冲突检测：`POST /api/guidebooks/{id}/validate` 返回 `time_overlap` / `unreachable` / `budget_exceeded` / `closed_time`。全部由确定性代码计算，不依赖模型；`closed_time` 依赖 FR-6 富化的节点营业时间，无法解析时不误报。
- 对话式调整：`POST /api/guidebooks/{id}/adjust`，模型必须返回完整攻略 JSON；解析或调用失败时不落库、版本不变（`50300` / `42200`）。Redis 编辑锁 `travel:edit:lock:{guidebookId}`（TTL 180 秒）防并发，重复请求返回 `42900`；不消耗每日生成配额。
- 版本历史：`GET /api/guidebooks/{id}/versions`、`POST /api/guidebooks/{id}/versions/{version}/restore`。快照只追加，回溯不是回滚——复制旧快照生成 `restore` 类型的新版本，版本号继续递增。
- 确认攻略：`POST /api/guidebooks/{id}/confirm` 先做服务端冲突复检；存在冲突返回 `42200` 且状态保持不变，确认成功不再写快照。
- 前端：`/guidebook/:guidebookId` 的 `draft / editing` 态支持节点编辑、同日拖拽、跨天移动、冲突面板、版本抽屉与调整输入；会话页仅在绑定 `draft / editing` 攻略时显示「调整当前攻略」，调整结果只做本地展示，不写入会话消息表。

## 实时数据接入（FR-6）

- 数据源：Open-Meteo 天气、open.er-api 汇率、GDELT 当地动态、`t_visa_policy` 签证信息；机票/酒店价区间走可配置的 HTTP 适配器（`FLIGHT_PRICE_URL` / `HOTEL_PRICE_URL` + `PRICE_SOURCE_TOKEN`）。
- 快照结构：`travelData = { fetchedAt, blocks[] }`，每个块含 `source` / `status`（`ok` / `unavailable`）/ `summary` / `data` / `fetchedAt`。快照与 `travelDataAt` 随攻略与版本快照一起落库，详情接口直接返回。
- 降级：每个源独立超时（默认 4 秒），失败只产生 `*_unavailable` 并写入 `degradationReasons`，生成继续；**未配置价格源时不会估算价格**，只会显示“时效未知”。
- 缓存：Redis key `travel:data:{source}:{destination}:{dates}`，TTL 由 `t_data_source_config` 配置；运营可通过 `GET/PUT /api/admin/data-sources` 调整启用、优先级与 TTL。
- 营业时间：生成阶段在 `itinerary_compose` 与 `output_validation` 之后执行 `OpeningHoursEnrichmentStep`（Nominatim + Overpass），把 `openingHours` / `openingHoursSource` 写到节点；`ConflictDetector` 据此保守识别 `closed_time` 冲突（无法解析则不误报）。
- 前端：攻略页展示「数据时效」面板（逐源状态与采集时间）、节点营业时间；不可用源明确标注，不展示编造数值。

## 预订转链（FR-8）

- 资源目录：`t_resource_slot`（`flight` / `hotel` / `ticket` / `car` / `dining`），含价格区间、可用性、退改政策、时效与联盟参数。`V15` 种子为**演示数据**，不代表真实库存/价格，以合作方页面为准。
- 搜索：`GET /api/resources?type=hotel&destination=东京&...`，分页返回资源；`bookingLink` 由服务端签名生成，前端不得自行拼接。
- 绑定：`POST /api/guidebooks/{id}/nodes/{nodeId}/resource` 复用编辑保存路径（版本 +1、写 `manual` 快照），传 `null` 可解绑；详情接口按节点批量补充 `resource`，避免 N+1。
- 转链：`GET /api/link/click?rid=&sig=` 校验 HMAC 与启用状态后 302 跳转，并追加联盟参数与 `utm_source`；签名错误返回 `40301`，资源不存在 `40400`，已下架 `40900`。
- 校验：`POST /api/link/validate` 批量检查可用性与时效（超过 48 小时视为失效），失效时返回原因与同类目的地的 `fallbackSupplier`。
- 前端：节点卡片展示供应商、价格、可用性、退改与时效；编辑态可搜索并绑定/解绑资源；点击「预订」先确认「即将离开本站，交易由合作方完成」。

## 知识库与溯源（FR-7）

- 检索抽象：`KnowledgeRetriever` 接口隔离检索实现，本期为 `KeywordKnowledgeRetriever`（PostgreSQL `pg_trgm`）；Milvus/向量检索未接入，后续可新增适配器而不改业务层。
- 准入规则：检索只返回 `status = approved` 的知识条目；UGC 攻略需经运营审核（`POST /api/admin/guidebooks/{publishedId}/review`）通过后才写入知识库。
- 结构化引用：节点新增 `citations[]`（`code` / `type` / `title` / `sourceName` / `sourceUrl` / `publishedAt` / `summary`）；`TraceabilityStep` 解析模型输出中的 `[K1]` 编号映射为引用，未知编号丢弃，无资料时补一条 `general` 引用并标注“知识库无相关资料”。
- 引用随节点 JSON 落库并复制进版本快照，历史版本保留生成时的引用，不会被新检索覆盖；前端、导出与离线包统一展示。
- 运营接口：`GET/POST /api/admin/knowledge`、`POST /api/admin/guidebooks/{publishedId}/review`，权限由 `AdminGuard`（`L3` 或 `ADMIN_USER_IDS` 白名单）控制。

## 离线攻略包（FR-9）

- 生成：`POST /api/guidebooks/{id}/offline-pack`（`format=pdf|html`，`includeMaps`）；仅所有者，`generating/failed` 返回 `40900`；同攻略 + 版本 + 格式的未过期任务直接复用。
- 版本绑定：按 `(guidebookId, version)` 读取 `t_guidebook_version` 快照组装内容，下载的包与请求时的版本一致；旧包不因后续编辑失效。
- 异步任务：`t_offline_pack` 状态机 `processing → completed/failed`，独立线程池；进程重启时超过 10 分钟仍为 `processing` 的任务置为 `failed`，用户可重试；72 小时后懒过期并删除文件。
- 渲染：HTML 自包含（内联 CSS、图片 base64、无外部资源请求）；PDF 使用 PDFBox + 内置 Noto Sans SC 子集字体，自动换行与分页，中文不出现方框。
- 地图：按每日坐标质心请求静态图，失败时降级为坐标列表 + 地图链接，**不影响整包生成**。
- 下载：`GET /api/offline-packs/{taskId}/download` 返回文件流，需要 `Authorization`；前端使用带 token 的 blob 请求后创建临时对象 URL，文件名同时提供 ASCII 与 RFC 5987 中文形式。

## 多方案对比与混搭（FR-4）

- 生成：`POST /api/guidebooks/compare`（2–3 个变体：`budget` / `balanced` / `luxury`），以 base 攻略的意图与画像快照为基础；一次对比只消耗 **1 次**每日配额。
- 隔离：三套方案是独立攻略（各自 `version=1`、独立编辑/确认/删除，`session_id=null`），顺序执行避免挤占普通生成线程池；单套失败不影响其余，汇总为 `partial`。
- 并发：Redis 锁 `travel:compare:lock:{userId}`，重复请求返回 `42900`。
- 详情：`GET /api/guidebooks/compare/{comparisonId}` 返回汇总状态与每套的 `guidebookId` / `variant` / `status` / `progress` / `title` / `totalBudget` / `dayCount` / `highlights`，前端每 2 秒轮询。
- 混搭：`POST /api/guidebooks/compare/{comparisonId}/mix` 复制所选节点并重新生成 ID，调用模型重平衡；模型失败时保留所选并标记 `degradedMode`（不使用模板替换）。复检冲突后，有冲突返回 `status=blocked` 且**不落库**；无冲突新建 `variant=mixed` 的 draft 并追加 v1 快照。
- 前端：`/guidebook/compare/:comparisonId` 三列对比、跨方案勾选节点、底部「混搭所选」；冲突时展示面板并保留选择。

## 行程伴侣（FR-10）

- 状态投影：执行状态只写 `t_guidebook_node_status`，不修改攻略 `version`；`confirmed → executing`（进入行程首日）→ `completed`（结束日期后或全部节点 executed/skipped）。
- 阶段 A：`GET /api/trip-companion/{guidebookId}` 返回今日节点、执行进度与建议；`POST /api/trip-companion/{guidebookId}/nodes/{nodeId}/status` 标记 `executed` / `skipped` / `rescheduled`。
- 阶段 B：`POST /api/trip-companion/{guidebookId}/refresh` 手动触发扫描；定时任务默认每 15 分钟扫描 `confirmed / executing` 且日期覆盖今天的攻略。
- 阶段 C：`POST .../suggestions/{suggestionId}/accept|reject` 处理建议。接受带操作的建议时深拷贝 `days` → 应用 `move_node` / `swap_node` → 冲突复检 → 乐观锁写回 → 追加 `companion`（行程调整）快照，`version` +1；提示类建议（`operations` 为空）只标记 `accepted`，`applied=false` 且版本不变。冲突返回 42200 且不落库；重复处理 40900；只有 `executing` 可接受。
- 前端：`/trip/:guidebookId` 行程伴侣页展示今日节点、执行进度与建议；节点可就地标记状态，进入页面刷新一次建议，之后每 30 秒轮询（页面隐藏时暂停）。
- 规则引擎：天气改期（逐日天气码判定，选择未来 3 天内天气最好的日期）、营业时间越界（复用 `OpeningHours`，24/7 与无法解析的表达式不生成建议）、当地动态提示（只产出无操作的提示）。所有规则是确定性纯函数，不调用 AI。
- 降级与并发：任一数据源不可用时只跳过对应建议，绝不猜测；Redis 锁 `travel:companion:job:{guidebookId}:{date}`（TTL 5 分钟）+ 去重键唯一索引保证多实例、重复扫描不产生重复建议；建议在触发日 23:59:59 过期，读取时惰性置为 `expired`。
- 开关：`app.trip-companion.scheduling-enabled`（默认 `true`）与 `scan-cron`（默认 `0 */15 * * * *`）。

## 复盘闭环（FR-11）

- 阶段 A：`GET /api/reviews/{guidebookId}/summary` 只读聚合当前 `days` 与 `t_guidebook_node_status`，返回 `executed / skipped / rescheduled / untouched`、`skipRate` 与确定性模板摘要（`summarySource=template`）；`POST /api/reviews` 提交评价，仅 `completed` 可调用，同一用户同一攻略 upsert。
- 统计口径：只计入当前行程中仍存在的节点，已删除节点的孤儿状态行忽略；未标记节点单独计数，不并入跳过；`skipRate` 为 0–1 数值（四位小数）。
- 评价快照：`t_guidebook_review` 保存提交那一刻的分项计数，后续节点标记变化不回写；重复提交只更新用户输入与计数，不重置画像迭代字段。
- 阶段 B：摘要优先复用评价行上缓存的 AI 文本（`summarySource=ai`），否则在专用线程池 `reviewAiExecutor` 中调用模型，最多等 5 秒；任何异常、超时或空回复都回落到确定性模板（`summarySource=template`），接口始终 200。提示词把用户评语放在 `<<< >>>` 中并要求不执行其中指令。
- 画像建议是纯规则：跳过率 ≥ 30% 且日均节点 ≥ 5 时建议 `pace=relaxed`；上一本评价也偏紧时置信度 0.85，否则 0.65；画像已是 `relaxed` 或没有足够天数/节点依据时返回 `null`。建议在提交评价时计算一次并落库，已决策的评价不会被后续提交覆盖。
- 阶段 B 决策：`POST /api/reviews/{guidebookId}/profile-iteration`，`accept=true` 先条件声明 `pending → accepted`，再通过 `ProfileService.applySuggestedFields`（唯一画像写入口，白名单字段 + 版本条件）写入并 `version +1`；`accept=false` 只改状态。重复决策或画像版本冲突返回 40900，接受失败时事务回滚，评价状态保持 `pending`。
- 阶段 C：攻略详情页仅在 `completed` 状态挂载 `ReviewPanel`（`v-if`，非完成态不发任何复盘请求），展示统计、摘要来源标签、评价表单与画像建议卡片；提交/接受/拒绝后重新拉取摘要，版本号以服务端 `profileVersion` 为准；加载失败保留失败态与「重新加载」，评价表单仅在数据加载成功后可用。图片上传入口本期隐藏（§15.3 未实现）。

## 攻略分享（FR-12）

- 阶段 A：`POST /api/guidebooks/{guidebookId}/share` 仅允许 `confirmed / executing / completed` 的攻略所有者创建分享；`channel` 支持 `link` / `qrcode`（`market` 本期返回 `42200`）。
- 分享是**不可变快照**：创建时深拷贝 `days / budgetBreakdown / 贴士 / 免责声明 / 时效` 到 `t_guidebook_share.snapshot`，不包含画像快照、用户 ID、会话、对比方案等内部字段；之后编辑原攻略不影响已创建分享。
- 短码是能力凭证：16 位 Base62（约 95 bit，`SecureRandom`），唯一索引兜底，插入冲突时重新生成并重试。
- 幂等与版本：同一攻略 + 渠道 + 版本重复创建返回已有分享；版本变化先条件撤回旧分享（`status=revoked`，快照保留），再创建新快照。
- 公开访问：`GET /api/share/{shareCode}` 无需登录（`/api/share/**` 放行），服务层不读取 `UserContext`；短码不存在 `40400`，已撤回/已过期 `40900`。
- 撤回：`DELETE /api/guidebooks/{guidebookId}/share/{shareCode}` 只改状态，重复撤回幂等；非所有者 `40301`。
- 配置：`app.share.base-url`（分享页地址）、`expire-days`（默认 30，0 表示不过期）、可选 `qrcode-url-template`（`{url}` 占位，未配置时 `qrcodeUrl=null`）。
- 阶段 B：`POST /api/share/{shareCode}/regenerate` 位于公开前缀下但在服务层显式 `StpUtil.checkLogin()`；使用 `StpUtil.getLoginIdAsLong()` 而非 `UserContext`。新攻略归属接收者、绑定其 `ready_to_generate` 会话并记录 `source_share_id`，复用配额、`travel:gen:lock` 与生成池容量校验。
- 参考骨架只保留每天的时间段、类型与地点名（最多 8 天 × 每天 8 个节点），不含备注、来源、坐标、资源绑定；提示词明确禁止照抄与引用参考来源，`citations` 只来自本次检索。
- `dataFreshnessDiff` 基于分享快照判断：缺失、无法解析或超过 48 小时的节点返回 `new=pending_refresh`，仅提示不阻断生成。
- 阶段 C：攻略页在 `confirmed / executing / completed` 显示「分享」入口（不可分享状态不渲染该动作）；`ShareDialog` 支持链接/二维码两种渠道、复制链接、展示二维码与撤回，关闭即重置，重复创建依赖后端同版本幂等返回同一短码。
- 公开页 `/share/:shareCode` 带 `meta.public`，未登录可访问，只调用 `GET /api/share/{shareCode}` 并只读渲染逐日节点、预算与贴士，不渲染编辑、版本、收藏等入口；加载错误区分 `40400`（分享不存在）与 `40900`（已撤回/已过期）。
- 「为我再生成」在点击时判断登录：未登录跳转 `/login?redirect=<encodeURIComponent(fullPath)>`，登录后回到同一分享；已登录只列出 `ready_to_generate` 会话，提交成功后展示 `dataFreshnessDiff`（`new` 显示为「待刷新」）并可跳转新攻略生成页。失败保留已选会话以便重试。

## 与 PRD 的偏差

| 项 | 偏差 | 原因 / 后续 |
|---|---|---|
| FR-4 权限 | 未强制 `L2`，所有登录用户可用 | 用户等级体系未落地；`UserLevelPolicy.allowMultiPlan` 是唯一开关，接入等级时只改这一处 |
| FR-7 检索 | 仅关键词（`pg_trgm`），未接 Milvus / embedding | 接口已抽象为 `KnowledgeRetriever`，新增向量实现即可切换；`README` 与接口文档同步说明 |
| FR-8 资源 | 使用本地资源目录与**演示种子数据** | 无真实供应商接口；转链、签名、可用性、时效均为真实实现，替换数据来源即可 |
| FR-9 地图 | 静态地图为尽力而为，失败降级为坐标 + 链接 | 免费静态图服务可能限流或下线；地图缺失不影响攻略包其余内容 |
| FR-12 分享渠道 | `market` 渠道未开放，返回 `42200` | 达人发布流程（FR-13/L3）尚未落地；链接与二维码分享已完整实现 |

## 当前进度

已完成：鉴权（§2）、用户画像 FR-1（§3）、对话会话 FR-2（§4，含 WS 流式）、推荐攻略流（§5.7，推荐页 + `GET /api/guides/recommend`）、推荐攻略详情（§5.8，详情页 `/discover/:postId`：多图 + 住宿/吃饭/景点/交通四模块 + 原文外链）、攻略生成 FR-3、攻略编辑 FR-5（阶段 A–I）、实时数据 FR-6、预订转链 FR-8、知识库溯源 FR-7、离线攻略包 FR-9、多方案对比与混搭 FR-4、行程伴侣 FR-10（阶段 A–C）、复盘闭环 FR-11（阶段 A–C）、攻略分享 FR-12（阶段 A–C）。

推荐页 `/discover` 是登录后的落地页，内容为上述 `V4` 演示种子数据（封面图走 picsum.photos 外链，图片内容与目的地无关）；详情页内容由 `V7` 补充。画像页基础画像含「人均预算」与「行程天数」两个具体数值（`V6`，不影响完整性校验）。

FR-3 攻略生成已交付：对话确认 → 异步流水线 → 进度推送 → `/guidebook/:guidebookId` 三态页面（生成中 / 失败重试 / 草稿），支持日程、预算、贴士与溯源展示，以及 Markdown/HTML 导出。

FR-5 攻略编辑已交付：`draft / editing` 状态下支持节点编辑、新增、删除、跨天移动与同日拖拽排序；支持对话式 AI 调整（失败不落库、Redis 编辑锁防并发）；服务端确定性冲突检测（时段重叠 / 地理不可达 / 预算超限）；版本历史只追加、回溯生成新版本；确认前复检冲突，存在冲突时返回 `42200`。会话页在绑定 `draft / editing` 攻略时可进入显式调整模式，调整指令与结果只做本地展示，不写入会话消息表。

FR-6/8/7/9/4 已交付：实时数据以“快照 + 逐源降级”接入，营业时间参与 `closed_time` 冲突检测；预订资源可搜索、绑定（版本递增）并经 HMAC 签名转链；知识库只召回审核通过的条目，节点输出结构化引用；离线包按版本快照生成自包含 HTML / 中文 PDF；多方案对比一次扣 1 次配额、顺序生成，支持跨方案混搭与冲突阻断。全部外部依赖均有 `unavailable` 降级，单测不访问网络。

## 测试

| 层 | 外部依赖 | 命令 |
|---|---|---|
| 后端单元测试（服务层 / 工具类） | 无 | `cd backend && mvn -B test` |
| 后端 MVC 契约测试（standalone MockMvc） | 无 | 同上 |
| 后端集成测试（`@Tag("integration")`） | PostgreSQL + Redis | `cd backend && docker compose up -d postgres redis && mvn -B -Pintegration test` |
| 前端单测（Vitest + jsdom） | 无 | `cd frontend && npm run test:unit -- --run` |

默认 `mvn test` 通过 Surefire `excludedGroups=integration` 排除上下文启动类，**无需任何外部服务即可全绿**；只有显式指定 `-Pintegration` 才会运行 `TravelApiApplicationTests`、`GuidebookGenerationE2ETest` 以及 `integration` 包下的 FR-4/7/8/9 全流程用例（外部 HTTP 源指向不可达地址，不需要公网）。

验收标准映射（US-01–US-05 + FR-4–FR-10）：

| 用户故事 | 后端 | 前端 |
|---|---|---|
| US-01 账号访问 | `AuthServiceTest`、`LoginAttemptGuardTest`、`AuthControllerTest` | `LoginView.spec.ts` |
| US-02 旅行画像 | `ProfileServiceTest`、`ProfileControllerTest` | — |
| US-03 对话意图 | `IntentServiceTest`、`ChatServiceTest`、`ChatControllerTest` | `ChatView.spec.ts` |
| US-04 推荐攻略 | — | `DiscoverView.spec.ts` |
| US-05 攻略生成 | `GuidebookServiceTest`、`GuidebookControllerTest`、`GenerationPipelineTest`、`GuidebookGenerationRunnerTest`、`GenerationRecoveryListenerTest`、`TravelDataServiceTest`、`KnowledgeServiceTest`、`GuidebookGenerationE2ETest`（integration） | `GuidebookView.spec.ts`、`guidebookWs.spec.ts` |
| FR-5 攻略编辑 | `GuidebookEditServiceTest`、`GuidebookAdjustServiceTest`、`GuidebookHistoryServiceTest`、`GuidebookConfirmServiceTest`、`GuidebookConflictServiceTest`、`ConflictDetectorTest`、`GuidebookEditControllerTest`、`GuidebookVersionControllerTest`、`ChatServiceTest`、`ChatControllerTest` | `GuidebookView.spec.ts`、`ChatView.spec.ts` |
| FR-6 实时数据 | `TravelDataServiceTest`、`VisaPolicyProviderTest`、`HttpPriceRangeClientTest`、`OpeningHoursTest`、`OpeningHoursEnrichmentStepTest`、`AdminDataControllerTest` | `GuidebookView.spec.ts` |
| FR-7 知识库溯源 | `KnowledgeServiceTest`、`AdminKnowledgeServiceTest`、`AdminKnowledgeControllerTest`、`TraceabilityStepTest` | `GuidebookView.spec.ts` |
| FR-8 预订转链 | `ResourceServiceTest`、`LinkSignServiceTest`、`ResourceControllerTest`、`LinkControllerTest` | `GuidebookView.spec.ts` |
| FR-9 离线攻略包 | `OfflinePackServiceTest`、`OfflinePackRunnerTest`、`OfflinePackRecoveryListenerTest`、`HtmlPackRendererTest`、`PdfPackRendererTest`、`OfflinePackControllerTest` | `GuidebookView.spec.ts` |
| FR-4 多方案对比 | `ComparisonServiceTest`、`ComparisonRunnerTest`、`MixServiceTest`、`ComparisonControllerTest` | `CompareView.spec.ts`、`GuidebookView.spec.ts` |
| FR-10 行程伴侣 | `TripCompanionServiceTest`、`SuggestionRuleEngineTest`、`TripCompanionScanServiceTest`、`TripCompanionSchedulerTest`、`CompanionNodeEditorTest`、`SuggestionDecisionServiceTest`、`TripCompanionControllerTest` | `TripCompanionView.spec.ts` |
| FR-11 复盘闭环 | `ReviewStatsServiceTest`、`ReviewServiceTest`、`ReviewControllerTest`、`ReviewSummaryGeneratorTest`、`ProfileSuggestionServiceTest`、`ProfileIterationServiceTest` | `ReviewPanel.spec.ts`、`GuidebookView.spec.ts`（完成态门控） |
| FR-12 攻略分享 | `ShareSnapshotBuilderTest`、`ShareServiceTest`、`ShareControllerTest`、`ShareCodeTest`、`ShareReferenceBuilderTest`、`ShareFreshnessServiceTest`、`ShareRegenerationServiceTest`、`PromptAssemblyStepTest` | `ShareDialog.spec.ts`、`ShareView.spec.ts`、`GuidebookView.spec.ts`（分享入口门控） |

## 常用命令

```bash
# 后端编译
cd backend && mvn -DskipTests compile

# 前端类型检查 / 构建
cd frontend && npm run type-check && npm run build
```
