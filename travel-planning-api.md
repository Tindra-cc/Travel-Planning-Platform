# AI 旅行助理平台 接口文档（API Reference）

| 项 | 内容 |
|---|---|
| 文档名称 | AI 旅行助理平台 API 接口文档 |
| 版本 | v1.0 |
| 日期 | 2026-10-12 |
| 基础路径 | `/api`（不带版本号） |
| 协议 | HTTPS |
| 数据格式 | `application/json; charset=utf-8` |
| 关联文档 | [AI 旅行助理平台 PRD v2.0](./travel-planning-prd.md) |

> 本接口文档按 PRD 功能模块 FR-1 ~ FR-13 组织，接口路径统一以 `/api` 开头（不带 `/v1`）。

---

## 1. 通用约定

### 1.1 基础路径

所有接口以 `/api` 开头，例如：

```
GET  /api/profile
POST /api/chat/sessions
POST /api/guidebooks/generate
```

### 1.2 请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | 是（除公开接口） | `Bearer <token>`，Sa-Token 签发 |
| `Content-Type` | 是（有 body 时） | `application/json; charset=utf-8` |
| `X-Request-Id` | 否 | 客户端链路追踪 ID，用于 SkyWalking 关联 |
| `Accept-Language` | 否 | 语言偏好，如 `zh-CN` |

### 1.3 统一响应结构

```json
{
  "code": 0,
  "message": "ok",
  "requestId": "a1b2c3d4",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | integer | `0` 成功；非 0 为业务错误码 |
| message | string | 提示信息 |
| requestId | string | 服务端生成的链路 ID |
| data | object/array/null | 业务数据 |

### 1.4 分页结构

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [],
    "page": 1,
    "pageSize": 20,
    "total": 0
  }
}
```

### 1.5 分页请求参数

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| page | integer | 1 | 页码，从 1 开始 |
| pageSize | integer | 20 | 每页条数，最大 100 |

### 1.6 错误码

| code | HTTP | 说明 |
|---|---|---|
| 0 | 200 | 成功 |
| 40001 | 400 | 参数校验失败 |
| 40100 | 401 | 未登录或 token 失效 |
| 40101 | 401 | token 过期 |
| 40300 | 403 | 无权限（含用户层不足） |
| 40301 | 403 | 非资源所有者 |
| 40400 | 404 | 资源不存在 |
| 40900 | 409 | 状态冲突（如攻略已确认不可直接删除） |
| 42200 | 422 | 业务规则不满足（如节点冲突、画像不完整） |
| 42300 | 423 | 账号锁定（连续登录失败次数超限） |
| 42900 | 429 | 触发限流（AI 生成配额超限、验证码发送过频） |
| 50000 | 500 | 服务端错误 |
| 50300 | 503 | 依赖服务不可用（AI/数据源降级失败） |

### 1.7 用户层标识

接口按 PRD §3.1 三层用户控制权限：`L1`（通用）、`L2`（进阶）、`L3`（达人）。接口说明中标注最低所需层。

---

## 2. 用户与鉴权

### 2.1 注册

**POST** `/api/auth/register`

请求：

```json
{
  "account": "13800000000",
  "accountType": "mobile",
  "verifyCode": "123456",
  "password": "******"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| account | string | 是 | 手机号或邮箱 |
| accountType | string | 是 | `mobile` / `email` |
| verifyCode | string | 是 | 验证码 |
| password | string | 是 | 密码，长度 ≥ 8 |

响应 `data`：

```json
{
  "userId": "10001",
  "token": "xxxxx",
  "userLevel": "L1"
}
```

### 2.2 登录

**POST** `/api/auth/login`

请求：

```json
{
  "account": "13800000000",
  "password": "******"
}
```

响应 `data`：

```json
{
  "userId": "10001",
  "token": "xxxxx",
  "userLevel": "L1",
  "profileCompleted": false
}
```

**失败锁定**：同一账号（不论是否存在）连续 3 次密码错误即锁定 **30 分钟**，期间即使密码正确也返回 `42300`；成功登录清零计数。

- 计数与锁定按账号字符串进行，账号不存在时同样计数，且比对做等时处理——避免通过是否锁定或响应耗时探测账号是否已注册。
- 失败、锁定、限流均写审计日志（脱敏账号 + 客户端 IP）。
- 验证码校验错误（注册）不计入登录失败次数。

### 2.3 发送验证码

**POST** `/api/auth/verify-code`

请求：

```json
{
  "account": "13800000000",
  "accountType": "mobile",
  "scene": "register"
}
```

`scene` 枚举：`register` / `login` / `reset-password`。

响应 `data`：

```json
{ "sent": true, "code": null }
```

**发送渠道**：

- `accountType = email` 且已配置 SMTP → 通过 QQ 邮箱发送，`code` 恒为 `null`。
- 未配置 SMTP 的 **dev** 环境 → 回退为响应体内回显 `code`（便于联调）。
- 未配置 SMTP 的 **非 dev** 环境 → 返回 `50300`（生产不回显验证码）。
- `accountType = mobile`：短信通道未接入，仅 dev 回显。

**限流**：同一账号同一 `scene` 60 秒内不可重复发送；每账号每日上限 10 次（按 `account` 计，与 `scene` 无关）。超限返回 `42900`。

### 2.4 退出登录

**POST** `/api/auth/logout`

### 2.5 获取当前用户

**GET** `/api/auth/me`

响应 `data`：

```json
{
  "userId": "10001",
  "nickname": "旅行者",
  "userLevel": "L1",
  "profileCompleted": true,
  "dailyGenerateQuota": 3,
  "dailyGenerateUsed": 1,
  "expertEligible": false
}
```

`expertEligible` 由服务端 `L3AccessPolicy`（L3 或 `app.admin.user-ids` 白名单）计算，仅用于前端显示达人入口；接口门禁仍由 `ExpertInterceptor` 独立校验。

---

## 3. 用户画像（FR-1）

### 3.1 获取画像

**GET** `/api/profile`

响应 `data`：

```json
{
  "profileId": "p20001",
  "userId": "10001",
  "basic": {
    "budgetLevel": "balanced",
    "budgetAmount": 4000,
    "tripDays": 5,
    "pace": "relaxed",
    "interestTags": ["nature", "food"],
    "dietaryRestrictions": "无",
    "partyStructure": {
      "adults": 2,
      "children": 0,
      "elderly": 0,
      "pets": 0
    }
  },
  "advanced": {
    "specialPreferences": ["photography"],
    "acceptRedEye": false,
    "transportPreference": "rail",
    "accommodationPreference": "boutique"
  },
  "version": 3,
  "updatedAt": "2026-10-12T10:00:00Z"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| basic.budgetLevel | string | `budget`/`economy`/`balanced`/`comfort`/`luxury` |
| basic.budgetAmount | number | 人均预算（元）；可空，不参与完整性校验 |
| basic.tripDays | number | 常规行程天数；可空，不参与完整性校验 |
| basic.pace | string | `tight`/`balanced`/`relaxed` |
| basic.interestTags | string[] | 兴趣标签，至少 1 项 |
| basic.partyStructure | object | 出行人结构 |
| advanced | object | 深度画像，L2 解锁 |

### 3.2 更新画像

**PUT** `/api/profile`

请求：同 §3.1 响应中的 `basic` / `advanced` 结构（部分更新）。

响应 `data`：返回更新后的完整画像（`version` +1）。

### 3.3 画像完整性检查

**POST** `/api/profile/check`

用途：生成攻略前校验关键字段是否齐备。

响应 `data`：

```json
{
  "completed": false,
  "missingFields": ["budgetLevel", "partyStructure"]
}
```

### 3.4 出行人档案列表

**GET** `/api/profile/travelers`

### 3.5 新增出行人档案

**POST** `/api/profile/travelers`

请求：

```json
{
  "name": "张三",
  "idType": "id_card",
  "idNumber": "110101********1234",
  "birthDate": "1990-01-01",
  "specialNeeds": ""
}
```

响应 `data`：`{ "travelerId": "t30001" }`

### 3.6 更新出行人档案

**PUT** `/api/profile/travelers/{travelerId}`

### 3.7 删除出行人档案

**DELETE** `/api/profile/travelers/{travelerId}`

---

## 4. 对话式意图采集（FR-2）

### 4.1 创建对话会话

**POST** `/api/chat/sessions`

请求：

```json
{
  "firstMessage": "下个月想去日本，7 天，两个人，预算 1.5 万，想泡温泉看樱花"
}
```

响应 `data`：

```json
{
  "sessionId": "s40001",
  "status": "collecting",
  "messages": [
    {
      "messageId": "m1",
      "role": "user",
      "content": "下个月想去日本，7 天，两个人，预算 1.5 万，想泡温泉看樱花",
      "createdAt": "2026-10-12T10:00:00Z"
    }
  ],
  "pendingQuestions": []
}
```

### 4.2 获取会话详情

**GET** `/api/chat/sessions/{sessionId}`

响应 `data`：

```json
{
  "sessionId": "s40001",
  "status": "collecting",
  "intent": {
    "destination": "日本",
    "dateWindow": { "start": "2026-11-01", "end": "2026-11-08" },
    "party": { "adults": 2 },
    "budget": { "amount": 15000, "currency": "CNY" },
    "preferences": ["温泉", "樱花"]
  },
  "pendingQuestions": [
    { "questionId": "q1", "text": "具体城市偏好？东京/大阪/北海道？" }
  ],
  "messages": []
}
```

### 4.3 发送消息（对话）

**POST** `/api/chat/sessions/{sessionId}/messages`

请求：

```json
{
  "content": "首选东京和大阪，不接受红眼航班"
}
```

响应 `data`：

```json
{
  "messageId": "m2",
  "role": "assistant",
  "content": "明白了。我理解为：11 月初日本 7 天，2 人，预算 1.5 万，东京+大阪，温泉+樱花，不接受红眼航班。确认生成攻略吗？",
  "pendingQuestions": [],
  "sessionStatus": "ready_to_generate"
}
```

### 4.4 确认意图并触发生成

**POST** `/api/chat/sessions/{sessionId}/confirm`

用途：会话状态 `ready_to_generate` → 触发生成工作流。重复确认幂等返回同一 `guidebookId`；每用户同时只允许一个生成任务（并发返回 `42900`），每日配额默认 3 次。

响应 `data`：

```json
{
  "sessionId": "s40001",
  "status": "generating",
  "guidebookId": "g50001"
}
```

### 4.5 会话列表

**GET** `/api/chat/sessions?page=1&pageSize=20`

### 4.6 删除会话

**DELETE** `/api/chat/sessions/{sessionId}`

---

## 5. 攻略生成（FR-3）

### 5.1 一键生成攻略

**POST** `/api/guidebooks/generate`

请求：

```json
{
  "sessionId": "s40001",
  "intent": {
    "destination": "日本",
    "dateWindow": { "start": "2026-11-01", "end": "2026-11-08" },
    "party": { "adults": 2 },
    "budget": { "amount": 15000, "currency": "CNY" },
    "preferences": ["温泉", "樱花"]
  },
  "useProfile": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| sessionId | string | 否 | 从会话生成 |
| intent | object | 是（无 sessionId 时） | 直接传入意图 |
| useProfile | boolean | 否 | 是否结合用户画像，默认 `true` |

响应 `data`：

```json
{
  "guidebookId": "g50001",
  "status": "generating",
  "estimatedSeconds": 30
}
```

> 生成过程异步，通过 §5.2 轮询或 WebSocket 推送获取结果。

### 5.2 查询生成状态 / 攻略详情

**GET** `/api/guidebooks/{guidebookId}`

响应 `data`（生成完成）：

```json
{
  "guidebookId": "g50001",
  "status": "draft",
  "version": 1,
  "intent": {},
  "days": [
    {
      "day": 1,
      "nodes": [
        {
          "nodeId": "n1",
          "timeSlot": { "start": "09:00", "end": "11:30" },
          "type": "activity",
          "place": { "name": "浅草寺", "lat": 35.7148, "lng": 139.7967 },
          "resourceSlotId": null,
          "resource": null,
          "source": "知识库：近半年游记 3 篇；建议清晨前往避开人流 [K1]",
          "dataFreshness": "2026-10-12T09:00:00Z",
          "openingHours": "Mo-Su 09:00-17:00",
          "openingHoursSource": "OSM",
          "citations": [
            {
              "code": "K1",
              "type": "guide",
              "title": "浅草寺参拜指南",
              "sourceName": "达人攻略",
              "sourceUrl": "https://example.com/post/1",
              "publishedAt": "2026-09-01",
              "summary": "清晨人少，建议 8 点前到达"
            }
          ],
          "note": ""
        }
      ]
    }
  ],
  "budgetBreakdown": {
    "currency": "CNY",
    "items": [
      { "category": "transport", "min": 4000, "max": 5000 },
      { "category": "lodging", "min": 3000, "max": 4000 }
    ],
    "total": { "min": 14000, "max": 16000 }
  },
  "packingList": [],
  "culturalTips": [],
  "pitfallTips": [],
  "travelData": {
    "fetchedAt": "2026-10-12T09:00:00",
    "blocks": [
      { "source": "weather", "status": "ok", "summary": "11-01 晴 12~20℃", "data": {}, "fetchedAt": "2026-10-12T09:00:00" },
      { "source": "flight_price", "status": "unavailable", "fetchedAt": "2026-10-12T09:00:00" }
    ]
  },
  "travelDataAt": "2026-10-12T09:00:00",
  "disclaimer": "AI 生成，请核实",
  "degradedMode": false,
  "degradationReasons": []
}
```

`status` 枚举：`generating` / `failed` / `draft` / `editing` / `confirmed` / `executing` / `completed`。

扩展字段说明（FR-6 / FR-7 / FR-8）：

| 字段 | 说明 |
|---|---|
| `travelData` | 实时数据快照，`blocks[].status` 为 `ok` / `unavailable`；`unavailable` 时前端显示“时效未知”，不得补默认值 |
| `openingHours` / `openingHoursSource` | OSM 营业时间原始字符串与来源；未查到为 `null` |
| `citations` | 结构化溯源引用；`code` 对应生成时的 `[K1]` 编号，`general` 类型表示通用知识 |
| `resource` | 按 `resourceSlotId` 解析出的资源对象（含签名 `bookingLink`）；未绑定为 `null` |

### 5.3 重试生成

**POST** `/api/guidebooks/{guidebookId}/retry`

用途：生成失败后重试（会话状态回退，保留上下文）。

### 5.4 攻略列表

**GET** `/api/guidebooks?status=draft&page=1&pageSize=20`

### 5.5 删除攻略

**DELETE** `/api/guidebooks/{guidebookId}`

> 已确认/执行中的攻略需先解锁再删除。

### 5.6 导出攻略

**POST** `/api/guidebooks/{guidebookId}/export`

请求：

```json
{ "format": "markdown" }
```

`format` 枚举：`markdown` / `pdf` / `html`。

### 5.7 推荐攻略流

**GET** `/api/guides/recommend?page=1&pageSize=12`

用途：推荐页的信息流，返回**已发布到市场**的攻略列表（卡位：封面 / 标题 / 目的地 / 作者 / 收藏数），支持「加载更多」翻页。

> 本接口是 §13.5「发布攻略到市场」的**消费侧**，与 §5.1-5.6 中「用户自己生成的攻略」（FR-3，`/api/guidebooks/*`）是两回事，不要混用。
> 本期为新增能力：原契约只有发布侧，没有浏览侧。

请求参数：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码，从 1 开始 |
| `pageSize` | int | 否 | 12 | 每页条数，服务端夹取到 `[1,50]` |

需要登录（不带 token 返回 `40100`）。响应为 §1.4 分页结构：

```json
{
  "code": 0,
  "message": "ok",
  "requestId": "1757bbac-56c0-49df-b0a0-c90fc59ff33b",
  "data": {
    "list": [
      {
        "postId": "1",
        "title": "成都四天三晚｜把火锅和熊猫一次吃够",
        "destination": "成都",
        "coverUrl": "https://picsum.photos/seed/chengdu-panda/640/420",
        "summary": "落地先睡，第二天熊猫基地赶早、下午宽窄巷子，第三天都江堰，收尾春熙路扫街。",
        "authorName": "阿茶",
        "tags": ["美食", "亲子", "四天三晚"],
        "likeCount": 1284,
        "viewCount": 20311,
        "durationDays": 4,
        "budgetLevel": "balanced"
      }
    ],
    "page": 1,
    "pageSize": 12,
    "total": 15
  }
}
```

`GuidePostItem` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `postId` | string | 攻略 ID（Long 序列化为字符串） |
| `title` | string | 标题 |
| `destination` | string | 目的地 |
| `coverUrl` | string \| null | 封面图外链；前端需做加载失败兜底 |
| `summary` | string \| null | 一句话摘要 |
| `authorName` | string \| null | 作者昵称（无头像字段，前端用首字渐变圆代替） |
| `tags` | string[] \| null | 标签 |
| `likeCount` | int | 收藏/点赞数（当前为静态值，无点赞接口） |
| `viewCount` | int | 浏览数 |
| `durationDays` | int \| null | 建议天数 |
| `budgetLevel` | string \| null | 预算档位，复用画像枚举：`budget`/`economy`/`balanced`/`comfort`/`luxury` |

### 5.8 攻略详情

**GET** `/api/guides/{postId}`

用途：推荐流卡片的**详情页**数据源。返回多图图集 + 按模块（住宿 / 吃饭 / 景点 / 交通）拆分的规划 + 原文外链。列表接口（§5.7）字段保持轻量，重内容只在本接口返回。

需要登录（不带 token 返回 `40100`）；`postId` 不存在或已下架返回 `40400`。本接口**不做浏览数自增**，保持 GET 幂等。

路径参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `postId` | long | 攻略 ID |

响应 `data`（`GuidePostDetail`）：

```json
{
  "postId": "1",
  "title": "成都四天三晚｜把火锅和熊猫一次吃够",
  "destination": "成都",
  "coverUrl": "https://picsum.photos/seed/chengdu-panda/640/420",
  "summary": "落地先睡，第二天熊猫基地赶早、下午宽窄巷子，第三天都江堰，收尾春熙路扫街。",
  "authorName": "阿茶",
  "tags": ["美食", "亲子", "四天三晚"],
  "likeCount": 1284,
  "viewCount": 20311,
  "durationDays": 4,
  "budgetLevel": "balanced",
  "images": [
    "https://picsum.photos/seed/chengdu-panda/960/640",
    "https://picsum.photos/seed/chengdu-panda-2/960/640",
    "https://picsum.photos/seed/chengdu-panda-3/960/640",
    "https://picsum.photos/seed/chengdu-panda-4/960/640"
  ],
  "sourceUrl": "https://zh.wikipedia.org/wiki/成都市",
  "planSections": [
    {
      "type": "stay",
      "title": "住宿",
      "items": [
        { "name": "宽窄巷子·隐庐", "note": "步行到地铁，出门就是小吃街", "price": "¥420/晚" }
      ]
    },
    {
      "type": "food",
      "title": "吃饭",
      "items": [
        { "name": "马旺子川小馆", "note": "本地菜，别点网红菜", "price": "人均 ¥80" }
      ]
    }
  ]
}
```

`GuidePostDetail` 在 `GuidePostItem`（§5.7）全部字段基础上追加：

| 字段 | 类型 | 说明 |
|---|---|---|
| `images` | string[] \| null | 图集，首元素与 `coverUrl` 同图 |
| `sourceUrl` | string \| null | 原文外链；为空则前端不渲染「查看原文」按钮 |
| `planSections` | GuidePlanSection[] \| null | 模块化规划，按数组顺序渲染 |

`GuidePlanSection`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `stay`/`food`/`spot`/`transport`（前端按此映射图标，未知值有默认图标） |
| `title` | string | 模块标题，如「住宿」 |
| `items` | GuidePlanItem[] | 条目列表 |

`GuidePlanItem`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string \| null | 条目名 |
| `note` | string \| null | 一句话点评 |
| `price` | string \| null | 价格文案，如 `¥420/晚`、`人均 ¥80` |

---

## 6. 多方案对比与混搭（FR-4，L2）

> 本期偏差：不强制 `L2`，所有登录用户可用；服务层保留 `UserLevelPolicy` 作为唯一开关。

### 6.1 生成对比方案

**POST** `/api/guidebooks/compare`

请求：

```json
{
  "baseGuidebookId": "g50001",
  "variants": ["budget", "balanced", "luxury"]
}
```

响应 `data`：

```json
{
  "comparisonId": "c60001",
  "guidebooks": [
    { "guidebookId": "g50002", "variant": "budget", "totalBudget": null },
    { "guidebookId": "g50003", "variant": "balanced", "totalBudget": null },
    { "guidebookId": "g50004", "variant": "luxury", "totalBudget": null }
  ]
}
```

- `variants` 可选 `budget` / `balanced` / `luxury`，去重后 2–3 套；为空默认三套。
- 一次对比只扣 1 次每日生成配额；重复提交由 Redis 锁拦截（42900）。
- 方案异步顺序生成，`totalBudget` 在生成完成后才有值。

### 6.2 获取对比详情

**GET** `/api/guidebooks/compare/{comparisonId}`

响应 `data`：

```json
{
  "comparisonId": "c60001",
  "baseGuidebookId": "g50001",
  "status": "completed",
  "errorMessage": null,
  "variants": [
    {
      "guidebookId": "g50002",
      "variant": "budget",
      "status": "draft",
      "progress": 100,
      "title": "省钱东京",
      "totalBudget": { "min": 10000, "max": 12000 },
      "dayCount": 7,
      "highlights": ["浅草寺", "上野公园"]
    }
  ]
}
```

`status` 枚举：`processing` / `completed` / `partial` / `failed`。前端每 2 秒轮询，不用逐套猜测状态。

### 6.3 混搭生成

**POST** `/api/guidebooks/compare/{comparisonId}/mix`

请求：

```json
{
  "picks": [
    { "guidebookId": "g50002", "nodeId": "n11" },
    { "guidebookId": "g50003", "nodeId": "n21" }
  ]
}
```

响应 `data`：

```json
{
  "guidebookId": "g50005",
  "conflicts": [],
  "status": "draft"
}
```

- `picks` 去重后最多 30 个；所选节点必须属于该对比任务。
- 生成时复制所选节点并重新生成 `nodeId`，再调用模型重平衡；模型失败时保留所选节点并返回 `degradedMode=true`（详见 §5.2），不使用模板替换。
- 若存在时段/地理冲突，`conflicts` 返回冲突详情，`status` 为 `blocked`，**不落库**，需用户调整后重新提交。
- 成功时新建独立攻略：`variant=mixed`、`sessionId=null`、`comparisonId` 保留，后续编辑/版本/确认走 FR-5 链路。

---

## 7. 攻略编辑与重平衡（FR-5）

### 7.1 编辑节点（手动）

**PUT** `/api/guidebooks/{guidebookId}/days/{day}/nodes/{nodeId}`

请求：

```json
{
  "timeSlot": { "start": "10:00", "end": "12:00" },
  "place": { "name": "明治神宫", "lat": 35.6764, "lng": 139.6993 },
  "note": "改到这里"
}
```

响应 `data`：

```json
{
  "guidebookId": "g50001",
  "version": 2,
  "changeSummary": "将 Day1 上午节点替换为明治神宫",
  "conflicts": []
}
```

### 7.2 新增节点

**POST** `/api/guidebooks/{guidebookId}/days/{day}/nodes`

### 7.3 删除节点

**DELETE** `/api/guidebooks/{guidebookId}/days/{day}/nodes/{nodeId}`

### 7.4 移动节点

**POST** `/api/guidebooks/{guidebookId}/nodes/{nodeId}/move`

请求：

```json
{ "targetDay": 2, "targetIndex": 3 }
```

### 7.5 对话式调整（AI 重平衡）

**POST** `/api/guidebooks/{guidebookId}/adjust`

请求：

```json
{ "instruction": "Day3 太赶了，帮我调整" }
```

响应 `data`：

```json
{
  "guidebookId": "g50001",
  "version": 3,
  "changeSummary": "将 Day3 拆为两天，因用户反馈节奏过紧",
  "changes": [
    { "type": "split_day", "day": 3, "into": [3, 4] }
  ],
  "conflicts": []
}
```

### 7.6 节点级冲突检测

**POST** `/api/guidebooks/{guidebookId}/validate`

响应 `data`：

```json
{
  "valid": false,
  "conflicts": [
    {
      "type": "time_overlap",
      "day": 2,
      "nodeIds": ["n5", "n6"],
      "message": "两个节点时段重叠"
    }
  ]
}
```

冲突类型 `type` 枚举：`time_overlap` / `unreachable` / `budget_exceeded` / `closed_time`。

### 7.7 确认攻略

**POST** `/api/guidebooks/{guidebookId}/confirm`

响应 `data`：`{ "guidebookId": "g50001", "status": "confirmed" }`

### 7.8 攻略版本列表（L2）

**GET** `/api/guidebooks/{guidebookId}/versions`

### 7.9 回溯版本（L2）

**POST** `/api/guidebooks/{guidebookId}/versions/{version}/restore`

---

## 8. 资源搜索与转链（FR-8）

### 8.1 搜索资源

**GET** `/api/resources?type=hotel&destination=东京&startDate=2026-11-01&endDate=2026-11-08&page=1&pageSize=20`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| type | string | 是 | `flight`/`hotel`/`ticket`/`car`/`dining` |
| destination | string | 是 | 目的地 |
| startDate | string | 否 | 起始日期 |
| endDate | string | 否 | 结束日期 |
| budgetMin | number | 否 | 预算下限 |
| budgetMax | number | 否 | 预算上限 |

响应 `data.list[]`：

```json
{
  "resourceSlotId": "r70001",
  "type": "hotel",
  "supplier": "Booking",
  "priceRange": { "min": 800, "max": 1200, "currency": "CNY" },
  "availability": "available",
  "refundPolicy": "入住前 7 天可免费取消",
  "bookingLink": "/api/link/click?rid=r70001&sig=xxxxx",
  "freshness": "2026-10-12T09:00:00Z"
}
```

`availability` 枚举：`available` / `tight` / `sold_out` / `link_error`。

### 8.2 绑定资源到攻略节点

**POST** `/api/guidebooks/{guidebookId}/nodes/{nodeId}/resource`

请求：

```json
{ "resourceSlotId": "r70001" }
```

响应 `data`：`{ guidebookId, version, changeSummary, conflicts }`。

- 走与手动编辑相同的保存路径：版本 +1、追加 `manual` 快照、状态 `draft → editing`。
- `resourceSlotId` 为 `null` 表示解绑（契约扩展）。
- 资源不存在/已下架：40400；`sold_out` / `link_error` 不可绑定：40900；非所有者：40301。

### 8.3 获取转链（点击跳转）

**GET** `/api/link/click?rid={resourceSlotId}&sig={signature}`

说明：302 跳转到合作方页面，带联盟追踪参数。跳转前展示中转页提示「即将离开本站」（合规）。

响应：`302 Found`，`Location: <合作方 URL>`（已追加 `affiliate_params` 的联盟参数）

- 签名错误：40301
- 资源不存在或已下架：40400
- 当前不可跳转（售罄 / 失效 / URL 非法）：40900
- 只允许跳转到资源目录中的 `target_url`，不接受任何用户传入的 URL。

### 8.4 校验转链有效性

**POST** `/api/link/validate`

请求：

```json
{ "resourceSlotIds": ["r70001", "r70002"] }
```

响应 `data`：

```json
{
  "results": [
    { "resourceSlotId": "r70001", "valid": true },
    { "resourceSlotId": "r70002", "valid": false, "reason": "link_error", "fallbackSupplier": "携程" }
  ]
}
```

---

## 9. 离线攻略包（FR-9）

### 9.1 生成离线包

**POST** `/api/guidebooks/{guidebookId}/offline-pack`

请求：

```json
{ "format": "pdf", "includeMaps": true }
```

响应 `data`：

```json
{
  "taskId": 80001,
  "status": "processing"
}
```

- `format` 为 `pdf` / `html`，缺省 `pdf`；`includeMaps` 缺省 `true`。
- 仅所有者可创建；攻略状态允许 `draft` / `editing` / `confirmed` / `executing`，`generating` / `failed` 返回 40900。
- 同攻略 + 同版本 + 同格式的未过期 `processing` / `completed` 任务直接复用，返回原 `taskId`。

### 9.2 查询离线包状态

**GET** `/api/offline-packs/{taskId}`

响应 `data`：

```json
{
  "taskId": 80001,
  "status": "completed",
  "downloadUrl": "/api/offline-packs/80001/download",
  "expiresAt": "2026-11-01T00:00:00",
  "errorMessage": null
}
```

`status` 枚举：`processing` / `completed` / `failed`。
`errorMessage` 为扩展字段：`failed` 时说明原因（超时、过期、文件丢失等）。
`processing` 时 `downloadUrl` 为 null；任务 72 小时后过期，过期后状态变为 `failed` 且文件被删除。

### 9.3 下载离线包

**GET** `/api/offline-packs/{taskId}/download`

响应：文件流（`application/pdf` 或 `text/html`），
`Content-Disposition` 同时给出 ASCII 文件名与 RFC 5987 中文名（`filename*`）。

- 未完成或已过期：40900
- 非所有者：40301
- 任务不存在：40400

下载接口需要 `Authorization`，前端必须用带 token 的 blob 请求，不能使用裸 `<a href>`。

---

## 10. 行程伴侣（FR-10）

### 10.1 获取行程伴侣状态

**GET** `/api/trip-companion/{guidebookId}`

响应 `data`：

```json
{
  "guidebookId": 50001,
  "status": "executing",
  "todayNodes": [],
  "suggestions": [
    {
      "suggestionId": 1,
      "type": "weather_relocate",
      "reason": "今天预报「小雨」，户外活动体验差",
      "action": "将 Day2 的 2 个户外节点移到 Day5",
      "operations": [
        { "op": "move_node", "nodeId": "n2", "toDay": 5,
          "timeSlot": { "start": "09:00", "end": "11:00" } }
      ],
      "alternatives": [],
      "evidence": { "source": "weather", "fetchedAt": "2026-11-02T07:00:00" },
      "status": "pending",
      "createdAt": "2026-11-02T07:00:00",
      "expiresAt": "2026-11-02T23:59:59"
    }
  ]
}
```

`suggestions` 只返回最近 24 小时内的 `pending` / `rejected` 建议，按创建时间倒序；读取时会把已过期的 `pending` 惰性置为 `expired`。`type` 枚举：`weather_relocate` / `opening_hours` / `local_news`。只有带 `operations` 的建议才可自动改行程（阶段 C）；`local_news` 恒为空操作，仅作提示。

### 10.2 接受调整建议

**POST** `/api/trip-companion/{guidebookId}/suggestions/{suggestionId}/accept`

响应 `data`：

```json
{
  "suggestionId": 1,
  "status": "accepted",
  "applied": true,
  "version": 3,
  "summary": "将 Day2 的 2 个户外节点移到 Day5"
}
```

- `applied=true`：已实际改动行程。服务端深拷贝 `days` → 应用 `move_node` / `swap_node` → 冲突复检 → 乐观锁写回 → 追加 `companion`（行程调整）版本快照，`version` +1。
- `applied=false`：提示类建议（`operations` 为空，如 `local_news`）只把建议标记为 `accepted`，**版本不变**，`version` 为当前版本。前端应提示「已记录，请自行核实」，不要刷新行程。
- 仅 `executing` 状态的攻略可接受建议；其他状态 40900。
- 调整后存在冲突：42200，攻略与建议都不变（建议仍为 `pending`）。
- 建议已被处理或已过期：40900；建议不属于该攻略：40400；节点/目标日期不存在：40400。
- 非所有者：40301。

### 10.3 拒绝调整建议

**POST** `/api/trip-companion/{guidebookId}/suggestions/{suggestionId}/reject`

响应 `data`：

```json
{ "suggestionId": 1, "status": "rejected", "applied": false, "version": null, "summary": null }
```

只改建议状态，不改攻略内容、不产生版本。仅 `pending` 可拒绝，否则 40900；非所有者 40301；建议不存在 40400。

### 10.4 标记节点执行状态

**POST** `/api/trip-companion/{guidebookId}/nodes/{nodeId}/status`

请求：

```json
{ "status": "executed" }
```

`status` 枚举：`executed` / `skipped` / `rescheduled`。

### 10.5 按需刷新调整建议

**POST** `/api/trip-companion/{guidebookId}/refresh`

仅所有者可调用；攻略状态必须是 `confirmed` / `executing` 且今天在行程窗口内，否则返回 `created = 0`。

响应 `data`：

```json
{ "guidebookId": 50001, "created": 2 }
```

- 非所有者：40301；攻略不存在：40400。
- 同一攻略同一天使用 Redis 锁（`travel:companion:job:{guidebookId}:{date}`，TTL 5 分钟），并发刷新不会重复拉取。
- 去重键为「类型 + 触发日期 + 目标天 + 排序后的节点 ID」，数据库唯一索引兜底，重复扫描不会产生重复建议。
- 任一数据源不可用时只是不生成对应建议，不新增猜测内容，也不删除已有建议。

---

## 11. 反馈闭环（FR-11）

### 11.1 获取复盘摘要

**GET** `/api/reviews/{guidebookId}/summary`

响应 `data`：

```json
{
  "guidebookId": 50001,
  "status": "completed",
  "totalCount": 15,
  "executedCount": 12,
  "skippedCount": 2,
  "rescheduledCount": 1,
  "untouchedCount": 0,
  "skipRate": 0.1333,
  "aiSummary": "共 15 个节点，已执行 12、跳过 2、改期 1。跳过集中在第 3 天。",
  "summarySource": "template",
  "profileSuggestion": null,
  "review": null
}
```

- 统计只计入当前 `days` 中仍存在的节点；状态表中已被删除节点的行忽略。未标记节点单列为 `untouchedCount`，不并入跳过。
- `skipRate` 为 0–1 的数值，保留四位小数；`totalCount` 为 0 时为 0。
- `summarySource`：`template`（确定性模板，模型不可用时回落）或 `ai`（模型生成）。`ai` 摘要缓存在评价行上，重复打开摘要不重复调用模型；模板每次实时生成，不缓存。
- `profileSuggestion`：确定性规则给出的画像建议，没有依据时为 `null`。已提交评价后以提交时落库的那一版为准；未提交时实时计算、不落库：

```json
{
  "fields": { "pace": "relaxed" },
  "reason": "本次行程跳过率较高且每日节点偏多，建议把节奏调整为轻松",
  "confidence": 0.65
}
```

  - 规则：跳过率 ≥ 30% 且日均节点 ≥ 5 时建议 `pace=relaxed`；上一本评价跳过率也 ≥ 30% 时 `confidence` 提升到 0.85；画像已是 `relaxed` 时不再建议。`fields` 仅可能出现 `pace` / `budgetLevel` / `interestTags` / `tripDays`。
- `review` 为已提交的评价，未提交时 `null`。
- 仅所有者且攻略为 `completed` 时可调用：非所有者 40301、不存在 40400、其他状态 40900。

### 11.2 提交评价

**POST** `/api/reviews`

请求：

```json
{
  "guidebookId": 50001,
  "overallScore": 5,
  "aiAccuracyScore": 4,
  "nodeExecutionScore": 5,
  "content": "很贴合我的节奏",
  "images": ["file-key-1"]
}
```

- `overallScore` 必填，1–5；`aiAccuracyScore` / `nodeExecutionScore` 可选，1–5；`content` 最多 1000 字；`images` 最多 9 个 `fileKey`（`§15.3` 上传能力接入前前端不展示入口）。
- 同一用户对同一攻略重复提交走更新，`reviewId` 不变；不会重置画像迭代相关字段。

响应 `data`（评价 + 提交时的统计快照）：

```json
{
  "reviewId": 901,
  "guidebookId": 50001,
  "overallScore": 5,
  "aiAccuracyScore": 4,
  "nodeExecutionScore": 5,
  "content": "很贴合我的节奏",
  "images": ["file-key-1"],
  "executedCount": 12,
  "skippedCount": 2,
  "rescheduledCount": 1,
  "untouchedCount": 0,
  "totalCount": 15,
  "iterationStatus": "pending",
  "profileVersionAfter": null,
  "decidedAt": null,
  "createdAt": "2026-10-21T20:00:00",
  "updatedAt": "2026-10-21T20:00:00"
}
```

- `iterationStatus`：`pending` / `accepted` / `rejected`；`profileVersionAfter` 仅在接受后非空，`decidedAt` 为决策时间。
- 攻略未完成：40900；参数非法：40001；非所有者 40301；不存在 40400。
- 并发重复提交由唯一索引兜底，后到者返回 40900。

### 11.3 确认画像迭代

**POST** `/api/reviews/{guidebookId}/profile-iteration`

请求：

```json
{ "accept": true }
```

响应 `data`：`{ "profileVersion": 4 }`

- `accept=true`：画像写入建议字段，版本 +1；评价行标记 `accepted`，并记录 `profileVersionAfter` 与 `decidedAt`。
- `accept=false`：只标记 `rejected`，画像字段与版本都不变，返回当前版本。
- 两个决策条件都必须满足：评价行的画像迭代仍为 `pending`，且画像版本仍是建议生成时看到的版本。任一不满足返回 40900（前端刷新后重试）。接受时画像更新失败会整体回滚，评价状态保持 `pending`。
- 没有评价行 / 攻略不存在 40400；非所有者 40301；没有可确认的建议、建议已处理、建议字段不合法或画像版本冲突 40900；`accept` 缺失 40001。

---

## 12. 分享与再生成（FR-12）

### 12.1 创建分享

**POST** `/api/guidebooks/{guidebookId}/share`

请求：

```json
{ "channel": "link" }
```

`channel` 枚举：本期仅支持 `link` / `qrcode`；`market` 返回 `42200`（由达人发布流程提供），其它值返回 `40001`。

响应 `data`：

```json
{
  "shareId": "Xk3pQ9mZr2Lw7BcT",
  "shareUrl": "https://.../share/Xk3pQ9mZr2Lw7BcT",
  "qrcodeUrl": null
}
```

- `shareId` 是 16 位 Base62 短码（约 95 bit，`SecureRandom` 生成），不是数字 ID；`shareUrl` 由 `app.share.base-url` 拼接。
- `qrcodeUrl` 仅在配置 `app.share.qrcode-url-template`（使用 `{url}` 占位）时返回，否则为 `null`。
- 可分享状态仅 `confirmed / executing / completed`，其它状态返回 `40900`。
- 幂等：同一攻略 + 渠道 + 当前版本重复创建返回已有分享；攻略版本变化时先撤回旧分享，再创建新快照。
- 快照创建后不可变，不包含画像、用户 ID、会话与对比等内部字段。
- 过期时间由 `app.share.expire-days` 决定（默认 30 天，0 表示不过期）。
- 错误码：未登录 `40100`、非所有者 `40301`、攻略不存在 `40400`、状态不支持 `40900`、`market` 渠道 `42200`、缺失 `channel` `40001`。

### 12.2 获取分享攻略（公开）

**GET** `/api/share/{shareId}`

> 无需登录（`/api/share/**` 已放行），返回只读快照，绝不含原始画像、`ownerId`、会话或对比信息。

响应 `data` 主要字段：`shareCode`、`title`、`guidebookVersion`、`sharedAt`、`expiresAt`、`days`、`budgetBreakdown`、`packingList`、`culturalTips`、`pitfallTips`、`disclaimer`、`degradedMode`、`travelDataAt`。

- 短码不存在返回 `40400`；已撤回或已过期返回 `40900`（先按短码查询，才能区分二者）。
- 公开读取不读取用户上下文，未登录请求不会触发 `40100`。

### 12.3 基于分享再生成

**POST** `/api/share/{shareId}/regenerate`

请求：

```json
{
  "sessionId": "s40002"
}
```

响应 `data`：

```json
{
  "guidebookId": "g50010",
  "status": "generating",
  "dataFreshnessDiff": [
    { "nodeId": "n1", "old": "2026-08-01", "new": "pending_refresh" }
  ]
}
```

- 该接口位于公开前缀 `/api/share/**` 下，但**必须登录**：服务层显式 `StpUtil.checkLogin()`，未登录返回 `40100`。
- 分享不存在 `40400`；已撤回/已过期 `40900`；会话不属于本人 `40301`；会话未就绪 `42200`；会话已有活跃攻略 `40900`；配额用尽或已有生成任务 `42900`。
- 新攻略归属接收者（`user_id` 来自登录态），`source_share_id` 记录来源分享，`session_id` 为请求中的会话；原分享与快照不变。
- 参考内容只进入提示词（时间、类型、地点名），新攻略的 `citations` 只来自本次生成；生成失败沿用普通生成的失败链路。
- `dataFreshnessDiff` 只提示、不阻断：节点数据缺失、无法解析或超过 48 小时时输出，`new` 固定为 `pending_refresh`（新数据尚未采集）；48 小时内不输出。

### 12.4 撤回分享

**DELETE** `/api/guidebooks/{guidebookId}/share/{shareId}`

- 需登录且为分享所有者；只把 `status` 置为 `revoked`，快照保留用于审计。
- 重复撤回幂等（仍返回 200）；短码不存在 `40400`，非所有者 `40301`。
- 撤回后公开读取返回 `40900`；再次创建同渠道分享会生成新短码。

---

## 13. 达人工作台（FR-13，L3）

> 权限：`/api/expert/**` 统一由 `ExpertInterceptor` 校验，规则与运营一致——用户等级 `L3` 或命中 `app.admin.user-ids` 白名单；不满足返回 40300。客户归属不符返回 40301。
>
> 客户授权状态机：`pending`（已发起，待客户确认）→ `active`（客户已确认）→ `revoked`。每次访问客户数据都会重新校验授权，不依赖前端缓存。

### 13.1 客户列表

**GET** `/api/expert/clients?page=1&pageSize=20&status=active`

`status` 可选 `active` / `archived`。列表项：

```json
{
  "clientId": "11",
  "name": "客户A",
  "contact": "13800000000",
  "note": "",
  "linkedUserId": "10002",
  "status": "active",
  "createdAt": "2026-10-01 10:00:00",
  "authorizationStatus": "active",
  "authorizedScopes": ["profile", "guidebooks"],
  "authorizedAt": "2026-10-02 09:00:00"
}
```

`authorizationStatus` 为 `active` / `pending` / `none`，是派生字段，不单独落表。

### 13.2 创建客户档案与详情

**POST** `/api/expert/clients`

```json
{
  "name": "客户A",
  "contact": "13800000000",
  "note": "",
  "linkedUserId": "10002"
}
```

`name` 必填（≤80）；`linkedUserId` 可空，非空时必须是已存在用户（否则 42200）。创建后不可通过该接口修改绑定账号。

**GET** `/api/expert/clients/{clientId}`：返回与列表项相同的结构，并记录一次 `view_client` 访问审计。

**DELETE** `/api/expert/clients/{clientId}`：归档客户，只改状态，不删除历史发布与审计。

### 13.3 客户授权画像访问

**POST** `/api/expert/clients/{clientId}/authorize`

```json
{ "scope": ["profile", "guidebooks"] }
```

响应 `data`：

```json
{
  "authorizationId": "9",
  "confirmToken": "一次性明文令牌",
  "expiresAt": "2026-10-09 10:00:00",
  "scope": ["profile", "guidebooks"]
}
```

- `confirmToken` 只在本次响应出现一次；服务端只保存 SHA-256 哈希，有效期 7 天。
- 同一客户同时最多一条 `active` 或 `pending` 授权；重新发起会把上一条 `pending` 置为撤回。
- `scope` 必须是 `profile` / `guidebooks` 的子集；批量生成同时要求两个 scope。

客户侧确认与撤回（不要求达人权限）：

**POST** `/api/client-authorizations/{token}/confirm`

响应 `data`：`{ authorizationId, clientId, status, scope, grantedAt }`。

**POST** `/api/client-authorizations/{authorizationId}/revoke`

达人侧撤回当前授权：**DELETE** `/api/expert/clients/{clientId}/authorization`（幂等）。

访问审计：

**GET** `/api/expert/clients/{clientId}/access-logs?page=1&pageSize=20`

列表项：`{ logId, action, scope, resourceType, resourceId, ip, createdAt }`。`action` 取值 `view_client` / `read_profile` / `generate_guidebook` / `list_guidebooks`。

### 13.4 为客户批量生成攻略

**POST** `/api/expert/clients/{clientId}/guidebooks/generate`

```json
{
  "intents": [
    {
      "destination": "日本",
      "dateWindow": { "start": "2026-11-01", "end": "2026-11-07" },
      "party": { "adults": 2 },
      "budget": { "amount": 12000, "currency": "CNY" }
    }
  ]
}
```

- `intents` 数量 1–5；所有意图先全部校验，任一不完整返回 42200，不创建任何攻略。
- 配额按本扣减（N 条意图 = N 次配额），在创建攻略的同一事务内一次性预扣；不足 N 次则整批失败返回 42900，不创建任何攻略。
- 同一达人同时只允许一个批量提交（Redis 锁 `travel:expert:batch:lock:{expertId}`，300 秒），重复返回 42900。
- 每本攻略独立入池；单本提交失败只把该行置为 `failed`，不影响其余。

响应 `data`：

```json
{
  "tasks": [
    { "taskId": "1201", "status": "generating" },
    { "taskId": "1202", "status": "failed" }
  ]
}
```

`taskId` 即生成的攻略 ID；进度查询与 WebSocket 复用 `GET /api/guidebooks/{guidebookId}`。

### 13.5 发布攻略到市场

**POST** `/api/expert/guidebooks/{guidebookId}/publish`

```json
{
  "title": "东京大阪 7 天温泉樱花行",
  "description": "...",
  "coverUrl": "https://example.com/cover.jpg",
  "priceCents": 9900,
  "tags": ["温泉", "樱花"]
}
```

- 仅攻略所有者；攻略状态允许 `draft` / `confirmed` / `executing` / `completed`，否则 40900。
- 发布时把攻略内容冻结为 `t_guide_post` 快照；待审期间推荐流与详情不可见。
- 同一攻略同时最多一条 `pending_review` / `published` 记录；同版本重复发布幂等，返回已有记录；不同版本需先下线，否则 40900。

响应 `data`：

```json
{ "publishedId": "50", "status": "pending_review", "commissionRate": 0.2 }
```

**GET** `/api/expert/publications?status=pending_review&page=1&pageSize=20`

列表项：

```json
{
  "publicationId": "50",
  "guidebookId": "31",
  "title": "成都四日",
  "status": "pending_review",
  "priceCents": 19900,
  "commissionRate": 0.2,
  "rejectReason": null,
  "createdAt": "2026-10-01 10:00:00"
}
```

状态：`pending_review` / `published` / `rejected` / `offline`。

**POST** `/api/expert/publications/{publicationId}/offline`：仅本人；把发布与对应帖子置为 `offline`。`rejected` / `offline` 调用幂等。

### 13.6 达人收益列表

**GET** `/api/expert/earnings?page=1&pageSize=20`

```json
{
  "list": [],
  "page": 1,
  "pageSize": 20,
  "total": 0,
  "settlementAvailable": false
}
```

- 只返回当前达人（登录态）的数据，不接受 `expertId` 参数。
- 本期无订单、支付与结算链路，`t_expert_earning` 无写入路径，空列表是正确结果；`settlementAvailable` 恒为 `false`，前端据此展示占位声明。
- `pageSize` 最大 50；发布记录缺失时 `guideTitle` 回退为「已下架的发布记录」。

---

## 14. 运营与知识库（运营角色）

> 权限：`AdminGuard` 最小实现——用户等级为 `L3`，或用户 ID 命中配置 `app.admin.user-ids`（环境变量 `ADMIN_USER_IDS`，逗号分隔）。不满足返回 40300。

### 14.1 知识库列表

**GET** `/api/admin/knowledge?status=approved&page=1&pageSize=20`

`status` 可选 `pending` / `approved` / `rejected`；不传返回全部。列表项含 `id` / `destination` / `docType` / `title` / `content` / `tags` / `sourceName` / `sourceUrl` / `status` / `sourceType` / `sourceRefId` / `createdAt`。

### 14.2 新增知识条目

**POST** `/api/admin/knowledge`

请求：

```json
{
  "destination": "东京",
  "docType": "guide",
  "title": "东京赏樱路线",
  "content": "...",
  "tags": ["樱花", "路线"],
  "sourceName": "运营整理",
  "sourceUrl": "https://example.com/source"
}
```

`destination` / `title` / `content` 必填；新增条目直接为 `approved`，立即进入检索池。

### 14.3 审核达人发布攻略

**POST** `/api/admin/guidebooks/{publishedId}/review`

请求：

```json
{ "approved": true, "comment": "" }
```

响应 `data`：`{ postId, reviewStatus, knowledgeDocId }`。通过时写入/更新 `t_knowledge_doc`（`sourceType=guide`、`sourceRefId=postId` 幂等）并使其可被检索；驳回只更新状态，已入库条目转为 `rejected` 并退出检索池。

**FR-13 扩展：审核发布记录**

**POST** `/api/admin/publications/{publicationId}/review`

```json
{ "approved": true, "reason": "" }
```

响应 `data`：`{ publicationId, status, postId }`。

- 只处理 `pending_review` 的发布；条件更新保证并发审核只有一个成功，其余返回 40900。
- 通过：发布置 `published`，帖子置 `published` / `approved`，并按 7C 的同一路径写入知识库。
- 驳回：发布与帖子置 `rejected`，原因写入 `reject_reason`；已入库的知识条目转为 `rejected`。
- 发布记录缺失 40400；发布内容缺失（无 `postId`）40900。

### 14.4 数据源配置

**GET** `/api/admin/data-sources`

**PUT** `/api/admin/data-sources/{sourceId}`

```json
{
  "enabled": true,
  "priority": 1,
  "cacheTtlSeconds": 1800
}
```

`sourceId` 为 `weather` / `exchange` / `local_news` / `visa` / `flight_price` / `hotel_price`；`enabled=false` 时该源不采集，生成时的对应块标为 `unavailable`。

### 14.5 实时数据查询（内部）

**GET** `/api/admin/data/weather?destination=东京&date=2026-11-01`

复用天气 provider；未配置或外部源失败时返回不可用标记，不返回编造值。

---

## 15. 通知与文件

### 15.1 通知列表

**GET** `/api/notifications?page=1&pageSize=20`

### 15.2 标记已读

**POST** `/api/notifications/{notificationId}/read`

### 15.3 上传文件

**POST** `/api/files/upload`

请求：`multipart/form-data`，字段 `file`。

响应 `data`：

```json
{
  "fileKey": "file-key-1",
  "url": "https://.../file-key-1",
  "size": 102400,
  "contentType": "image/jpeg"
}
```

---

## 16. WebSocket / 事件推送

### 16.1 攻略生成进度推送

**WS** `/api/ws/guidebook/{guidebookId}?token=<token>`

连接建立后立即下发当前进度，之后每次步骤切换推送：

```json
{
  "type": "guidebook_progress",
  "guidebookId": "g50001",
  "status": "generating",
  "currentStep": "itinerary_compose",
  "progress": 60
}
```

完成事件：

```json
{
  "type": "guidebook_done",
  "guidebookId": "g50001",
  "status": "draft",
  "progress": 100
}
```

失败事件：

```json
{
  "type": "error",
  "guidebookId": "g50001",
  "status": "failed",
  "message": "AI 服务暂不可用"
}
```

> 前端在 WS 未连上或断开时回退为每 2 秒轮询 `GET /api/guidebooks/{guidebookId}`，终态自动停止。

### 16.2 出行中调整建议推送

**WS** `/api/ws/trip-companion/{guidebookId}`

```json
{
  "event": "adjustment_suggestion",
  "suggestionId": "sg1",
  "reason": "今天下雨",
  "action": "将户外景点换到 Day5"
}
```

### 16.3 对话消息流式推送

**WS** `/api/ws/chat/{sessionId}`

```json
{
  "event": "message_delta",
  "messageId": "m2",
  "delta": "明白了。我理解为",
  "done": false
}
```

---

## 17. 接口与功能模块对照表

| 功能模块 | 接口分组 | 主要端点 |
|---|---|---|
| FR-1 画像 | §3 | `/api/profile*` |
| FR-2 对话 | §4 | `/api/chat/sessions*` |
| FR-3 生成 | §5 | `/api/guidebooks/generate`, `/api/guidebooks/{id}` |
| FR-4 对比 | §6 | `/api/guidebooks/compare*` |
| FR-5 编辑 | §7 | `/api/guidebooks/{id}/days/*`, `/api/guidebooks/{id}/adjust` |
| FR-6 实时数据 | §14.5（内部）+ §5.2 快照 | `/api/admin/data/*`、`travelData` |
| FR-7 知识库 | §14.1-14.3 + §5.2 引用 | `/api/admin/knowledge*`、节点 `citations` |
| FR-8 转链 | §8 | `/api/resources`, `/api/link/*` |
| FR-9 离线包 | §9 | `/api/guidebooks/{id}/offline-pack` |
| FR-10 行程伴侣 | §10 | `/api/trip-companion/*` |
| FR-11 反馈 | §11 | `/api/reviews*` |
| FR-12 分享 | §12 | `/api/guidebooks/{id}/share`, `/api/share/*` |
| FR-13 达人工作台 | §13 | `/api/expert/*` |
| 推荐攻略流（新增，§13.5 消费侧） | §5.7 | `/api/guides/recommend` |
| 推荐攻略详情（新增，§13.5 消费侧） | §5.8 | `/api/guides/{postId}` |
| 通用 | §2, §15, §16 | `/api/auth/*`, `/api/notifications`, `/api/files`, `/api/ws/*` |

---

## 18. 说明

- 所有接口路径不带版本号（`/api/...`，非 `/api/v1/...`）。
- 本文件为接口契约定义，不包含服务实现细节；鉴权、限流、幂等、并发控制在实现层处理。
- 涉及 AI 生成的接口需按 PRD §7.7 降级链处理，返回体通过 `degradedMode` 字段标识是否降级。
- 转链相关接口（§8.3、§8.4）涉及资金流向合作方，平台不经手，需按合规要求做跳转提示。
- 本期实现偏差（详见 `README.md` 的“与 PRD 的偏差”）：FR-4 未强制 L2；FR-7 仅关键词检索、未接 Milvus；FR-8 使用演示资源目录；FR-9 静态地图为尽力而为。
