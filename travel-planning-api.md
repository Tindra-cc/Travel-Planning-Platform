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
  "dailyGenerateUsed": 1
}
```

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
          "source": "知识库：近半年游记 3 篇；建议清晨前往避开人流",
          "dataFreshness": "2026-10-12T09:00:00Z",
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
  "disclaimer": "AI 生成，请核实",
  "degradedMode": false
}
```

`status` 枚举：`generating` / `failed` / `draft` / `confirmed` / `executing` / `completed`。

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
    { "guidebookId": "g50002", "variant": "budget", "totalBudget": { "min": 10000, "max": 12000 } },
    { "guidebookId": "g50003", "variant": "balanced", "totalBudget": { "min": 14000, "max": 16000 } },
    { "guidebookId": "g50004", "variant": "luxury", "totalBudget": { "min": 20000, "max": 24000 } }
  ]
}
```

### 6.2 获取对比详情

**GET** `/api/guidebooks/compare/{comparisonId}`

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

> 若存在时段/地理冲突，`conflicts` 返回冲突详情，状态为 `blocked`，需用户解决后重新提交。

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

### 8.3 获取转链（点击跳转）

**GET** `/api/link/click?rid={resourceSlotId}&sig={signature}`

说明：302 跳转到合作方页面，带联盟追踪参数。跳转前展示中转页提示「即将离开本站」（合规）。

响应：`302 Found`，`Location: <合作方 URL>`

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
  "taskId": "op80001",
  "status": "processing"
}
```

### 9.2 查询离线包状态

**GET** `/api/offline-packs/{taskId}`

响应 `data`：

```json
{
  "taskId": "op80001",
  "status": "completed",
  "downloadUrl": "/api/offline-packs/op80001/download",
  "expiresAt": "2026-11-01T00:00:00Z"
}
```

`status` 枚举：`processing` / `completed` / `failed`。

### 9.3 下载离线包

**GET** `/api/offline-packs/{taskId}/download`

响应：文件流（`application/pdf` 或 `text/html`）。

---

## 10. 行程伴侣（FR-10）

### 10.1 获取行程伴侣状态

**GET** `/api/trip-companion/{guidebookId}`

响应 `data`：

```json
{
  "guidebookId": "g50001",
  "status": "executing",
  "todayNodes": [],
  "suggestions": [
    {
      "suggestionId": "sg1",
      "reason": "今天下雨，户外景点体验差",
      "action": "将 Day2 户外景点换到 Day5",
      "alternatives": [],
      "createdAt": "2026-11-02T07:00:00Z"
    }
  ]
}
```

### 10.2 接受调整建议

**POST** `/api/trip-companion/{guidebookId}/suggestions/{suggestionId}/accept`

响应 `data`：返回更新后的攻略（`version` +1）。

### 10.3 拒绝调整建议

**POST** `/api/trip-companion/{guidebookId}/suggestions/{suggestionId}/reject`

### 10.4 标记节点执行状态

**POST** `/api/trip-companion/{guidebookId}/nodes/{nodeId}/status`

请求：

```json
{ "status": "executed" }
```

`status` 枚举：`executed` / `skipped` / `rescheduled`。

---

## 11. 反馈闭环（FR-11）

### 11.1 获取复盘摘要

**GET** `/api/reviews/{guidebookId}/summary`

响应 `data`：

```json
{
  "guidebookId": "g50001",
  "executedCount": 12,
  "skippedCount": 2,
  "rescheduledCount": 1,
  "aiSummary": "整体节奏符合预期，Day3 偏紧已记入画像",
  "profileSuggestion": {
    "preferRelaxedPace": true,
    "reason": "用户多次跳过紧凑节点"
  }
}
```

### 11.2 提交评价

**POST** `/api/reviews`

请求：

```json
{
  "guidebookId": "g50001",
  "overallScore": 5,
  "aiAccuracyScore": 4,
  "nodeExecutionScore": 5,
  "content": "很贴合我的节奏",
  "images": ["file-key-1"]
}
```

### 11.3 确认画像迭代

**POST** `/api/reviews/{guidebookId}/profile-iteration`

请求：

```json
{ "accept": true }
```

响应 `data`：`{ "profileVersion": 4 }`

---

## 12. 分享与再生成（FR-12）

### 12.1 创建分享

**POST** `/api/guidebooks/{guidebookId}/share`

请求：

```json
{ "channel": "link" }
```

`channel` 枚举：`link` / `qrcode` / `market`（`market` 仅 L3）。

响应 `data`：

```json
{
  "shareId": "sh90001",
  "shareUrl": "https://.../share/sh90001",
  "qrcodeUrl": "https://.../qrcode/sh90001.png"
}
```

### 12.2 获取分享攻略（公开）

**GET** `/api/share/{shareId}`

> 无需登录，返回只读副本（不含原始画像）。

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
    { "nodeId": "n1", "old": "2026-08-01", "new": "2026-11-01" }
  ]
}
```

### 12.4 撤回分享

**DELETE** `/api/guidebooks/{guidebookId}/share/{shareId}`

---

## 13. 达人工作台（FR-13，L3）

### 13.1 客户列表

**GET** `/api/expert/clients?page=1&pageSize=20`

### 13.2 创建客户档案

**POST** `/api/expert/clients`

请求：

```json
{
  "name": "客户A",
  "contact": "13800000000",
  "note": ""
}
```

### 13.3 客户授权画像访问

**POST** `/api/expert/clients/{clientId}/authorize`

请求：

```json
{ "scope": ["profile", "guidebooks"] }
```

> 客户需确认授权，返回授权凭证，访问留痕可审计。

### 13.4 为客户批量生成攻略

**POST** `/api/expert/clients/{clientId}/guidebooks/generate`

请求：

```json
{
  "intents": [
    { "destination": "日本", "dateWindow": {}, "party": {}, "budget": {} }
  ]
}
```

响应 `data`：

```json
{
  "tasks": [
    { "taskId": "bt10001", "status": "generating" }
  ]
}
```

### 13.5 发布攻略到市场

**POST** `/api/expert/guidebooks/{guidebookId}/publish`

请求：

```json
{
  "price": 9900,
  "title": "东京大阪 7 天温泉樱花行",
  "description": "..."
}
```

响应 `data`：

```json
{
  "publishedId": "pg11001",
  "status": "pending_review",
  "commissionRate": 0.2
}
```

### 13.6 达人收益列表

**GET** `/api/expert/earnings?page=1&pageSize=20`

---

## 14. 运营与知识库（运营角色）

### 14.1 知识库列表

**GET** `/api/admin/knowledge?page=1&pageSize=20`

### 14.2 新增知识条目

**POST** `/api/admin/knowledge`

请求：

```json
{
  "type": "guide",
  "destination": "东京",
  "content": "...",
  "source": "达人攻略 pg11001"
}
```

### 14.3 审核达人发布攻略

**POST** `/api/admin/guidebooks/{publishedId}/review`

请求：

```json
{ "approved": true, "comment": "" }
```

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

### 14.5 实时数据查询（内部）

**GET** `/api/admin/data/weather?destination=东京&date=2026-11-01`

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
| FR-6 实时数据 | §14.5（内部） | `/api/admin/data/*` |
| FR-7 知识库 | §14.1-14.3 | `/api/admin/knowledge*` |
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
