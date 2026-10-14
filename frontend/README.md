# frontend — AI 旅行规划助理（用户端）

Vue 3 + TypeScript + Element Plus + Pinia + Vue Router 的单页应用。后端接口契约见仓库根目录 [`travel-planning-api.md`](../travel-planning-api.md)。

## 快速开始

```bash
npm install
npm run dev          # 5173，已代理 /api → localhost:8080（含 WebSocket）
```

后端需先启动（见根目录 README）。开发环境登录测试账号：`13800000001` / `pass1234`。

## 常用命令

| 命令 | 作用 |
|---|---|
| `npm run dev` | 启动开发服务器（HMR） |
| `npm run type-check` | `vue-tsc --build` 类型检查 |
| `npm run build` | type-check + 生产构建 |
| `npm run test:unit` | Vitest 单元测试（`-- --run` 跑一次退出） |
| `npm run lint` / `npm run format` | oxlint + ESLint / Prettier |

## 目录结构

```
src/
├─ api/                 接口层，与 api.md 的章节一一对应
│  ├─ request.ts        axios 实例 + 拦截器 + ApiError
│  ├─ auth.ts           §2  鉴权
│  ├─ profile.ts        §3  用户画像
│  ├─ chat.ts           §4  对话会话
│  └─ guide.ts          §5.7 推荐攻略流
├─ stores/auth.ts       Pinia：token（localStorage）+ userInfo
├─ router/index.ts      路由表 + 登录守卫
├─ types/api.ts         与 api.md 对齐的类型定义（含 PageResult<T>）
├─ utils/ws.ts          对话 WebSocket 封装
├─ styles/
│  ├─ tokens.css        设计 token（CSS 变量）
│  └─ element-overrides.css  Element Plus 主题覆盖
└─ views/               页面组件
   ├─ DiscoverView.vue  推荐（登录后落地页）
   ├─ LoginView.vue     登录 / 注册
   ├─ ProfileView.vue   画像
   ├─ ChatView.vue      对话
   └─ GuidebookView.vue 攻略（FR-3，Sprint 2 接入）
```

## 设计体系（清新旅行风）

全站颜色、圆角、阴影、间距一律走 CSS 变量，**组件里不硬编码颜色**，改主题只动 [tokens.css](src/styles/tokens.css)。

- 主色青绿 `--brand-500: #12b3a8`，品牌渐变 `--grad-brand`（青绿 → 天蓝），强调色橙 `--accent-500`。
- 圆角 `--radius-lg/md/sm`、阴影 `--shadow-soft` / `--shadow-float`、间距 `--space-1..12`（4px 网格）。
- 字体走系统栈（`--font-sans`），**不引外链字体**（国内网络环境，避免首屏阻塞）。

[element-overrides.css](src/styles/element-overrides.css) 把 Element Plus 的主题变量（`--el-color-primary` 及其 light/dark 梯度、圆角、字体）映射到上述 token，因此 `el-button` / `el-card` / `el-input` 等组件自动跟随品牌色，无需逐处传参。二者在 [main.ts](src/main.ts) 中于 `element-plus/dist/index.css` **之后**引入。

## 路由与鉴权

- 路由表见 [router/index.ts](src/router/index.ts)。`/` 与未匹配路径都重定向到 `/discover`（登录后落地页）；末位有 `/:pathMatch(.*)*` 兜底，**任何路径都不会让 `router-view` 空渲染**（历史白屏问题即源于缺此兜底）。
- 全局守卫：非 `meta.public` 的路由需登录，否则跳 `/login?redirect=<原路径>`；已登录访问 `/login` 直接回 `/discover`。
- 登录成功后的回跳由 [LoginView.vue](src/views/LoginView.vue) 的 `safeRedirect()` 处理：只接受能 `router.resolve()` 出匹配项的站内路径，否则回落 `/discover`——防止 `?redirect=` 被塞进非法路径导致白屏。

## API 层约定

- axios 实例 `baseURL: '/api'`，请求拦截器自动附 `Authorization: Bearer <token>`（token 存 localStorage，key 见 `TOKEN_KEY`）。
- **响应拦截器剥掉 `Result` 外壳**：业务码 `0` 时直接返回 `data`，所以 `request<T>()` 的返回类型就是 `T`（如 `PageResult<GuidePostItem>`），页面无需再解一层。
- 业务失败抛 `ApiError(code, message)` 并统一 `ElMessage.error` 提示；`40100/40101` 会清 token 并跳登录页。页面通常只需 `try/catch` 兜住，或在需要按码分支时判断 `err.code`（例：账号锁定 `42300` 启动倒计时）。
- 分页统一 `{list, page, pageSize, total}`（`PageResult<T>`）；后端 Long 序列化为字符串，故所有 ID 字段在 TS 里是 `string`。

## WebSocket

对话消息走 `ws://<host>/api/ws/chat/{sessionId}?token=<token>`，封装见 [utils/ws.ts](src/utils/ws.ts)；事件格式（`message_delta` / `message_done` / `error`）见根目录 README。

## 测试

Vitest + `@vue/test-utils`（jsdom 环境），用例在 `src/__tests__/`。提交前建议跑 `npm run type-check && npm run test:unit -- --run && npm run build` 三件套。
