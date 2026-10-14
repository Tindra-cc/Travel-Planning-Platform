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
| `QQ_MAIL_USERNAME` | QQ 邮箱地址，用于发送注册验证码 | 否（不配则 dev 回显） |
| `QQ_MAIL_AUTH_CODE` | QQ 邮箱 **SMTP 授权码**（非 QQ 密码）：QQ 邮箱 → 设置 → 账户 → 开启 SMTP 服务后生成 | 否（不配则 dev 回显） |
| `DB_PASSWORD` | 生产环境数据库口令（dev 使用 `application-dev.yml` 内置值 `1234`） | prod 是 |
| `REDIS_PASSWORD` | 生产环境 Redis 口令（dev 内置值 `123456`） | prod 是 |
| `CORS_ALLOWED_ORIGINS` | 生产环境允许的前端来源，逗号分隔 | prod 是 |

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

   启动日志应显示 Flyway 应用 `V1`~`V4` 迁移。

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

> 注：本期 `message_delta` 为服务端分块推送（先整段生成再按片下发），非模型 token 级流式；FR-3 生成编排落地后再切换为真流式。

## 当前进度（Sprint 1）

已完成：鉴权（§2）、用户画像 FR-1（§3）、对话会话 FR-2（§4，含 WS 流式）、推荐攻略流（§5.7，推荐页 + `GET /api/guides/recommend`）。

推荐页 `/discover` 是登录后的落地页，内容为上述 `V4` 演示种子数据（封面图走 picsum.photos 外链，图片内容与目的地无关）。

`POST /api/chat/sessions/{id}/confirm` 目前仅将会话置为 `generating`，`guidebookId` 返回 `null`——攻略生成（FR-3）在 Sprint 2 接入。

## 常用命令

```bash
# 后端编译 / 测试
cd backend && mvn -DskipTests compile && mvn test

# 前端类型检查 / 单测 / 构建
cd frontend && npm run type-check && npm run test:unit -- --run && npm run build
```
