// 与 travel-planning-api.md 对齐的通用与领域类型定义

export interface Result<T = unknown> {
  code: number
  message: string
  requestId: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  page: number
  pageSize: number
  total: number
}

// ---- §2 用户与鉴权 ----

export interface RegisterRequest {
  account: string
  accountType: 'mobile' | 'email'
  verifyCode: string
  password: string
}

export interface LoginRequest {
  account: string
  password: string
}

export interface VerifyCodeRequest {
  account: string
  accountType: 'mobile' | 'email'
  scene: 'register' | 'login' | 'reset-password'
}

export interface VerifyCodeResponse {
  sent: boolean
  code?: string | null
}

export interface AuthResponse {
  userId: string
  token: string
  userLevel: string
  profileCompleted?: boolean
}

export interface CurrentUser {
  userId: string
  account: string
  accountType: 'mobile' | 'email' | string
  nickname: string
  /** 预置 emoji 或图片 URL，为空时显示昵称首字 */
  avatar: string | null
  userLevel: string
  profileCompleted: boolean
  dailyGenerateQuota: number
  dailyGenerateUsed: number
  /** 是否可使用达人工作台（由服务端 L3 / 白名单计算） */
  expertEligible: boolean
}

// ---- §3 用户画像 ----

export interface PartyStructure {
  adults?: number
  children?: number
  elderly?: number
  pets?: number
}

export interface BasicProfile {
  budgetLevel?: string
  pace?: string
  interestTags?: string[]
  dietaryRestrictions?: string
  partyStructure?: PartyStructure
  /** 常规行程天数 */
  tripDays?: number
  /** 人均预算金额（元） */
  budgetAmount?: number
}

export interface AdvancedProfile {
  specialPreferences?: string[]
  acceptRedEye?: boolean
  transportPreference?: string
  accommodationPreference?: string
}

export interface ProfileResponse {
  profileId: string
  userId: string
  basic: BasicProfile
  advanced: AdvancedProfile
  version: number
  updatedAt: string
}

export interface ProfileUpdateRequest {
  basic?: BasicProfile
  advanced?: AdvancedProfile
}

export interface ProfileCheckResponse {
  completed: boolean
  missingFields: string[]
}

export interface TravelerRequest {
  name: string
  idType?: string
  idNumber?: string
  birthDate?: string
  specialNeeds?: string
}

export interface TravelerResponse extends TravelerRequest {
  travelerId: string
}

// ---- §4 对话式意图采集 ----

export interface PendingQuestion {
  questionId: string
  text: string
}

export interface ChatMessage {
  messageId: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

export interface SessionResponse {
  sessionId: string
  status: string
  messages: ChatMessage[]
  pendingQuestions: PendingQuestion[]
}

export interface SessionDetail {
  sessionId: string
  status: string
  guidebookId: string | null
  intent: Record<string, unknown> | null
  pendingQuestions: PendingQuestion[]
  messages: ChatMessage[]
}

export interface SendMessageResponse {
  messageId: string
  role: string
  content: string
  pendingQuestions: PendingQuestion[]
  sessionStatus: string
}

export interface ConfirmResponse {
  sessionId: string
  status: string
  guidebookId: string | null
}

export interface SessionListItem {
  sessionId: string
  title: string
  status: string
  updatedAt: string
}

// ---- §5.7 推荐攻略流 ----

export interface GuidePostItem {
  postId: string
  title: string
  destination: string
  coverUrl: string | null
  summary: string | null
  authorName: string | null
  tags: string[] | null
  likeCount: number
  viewCount: number
  durationDays: number | null
  budgetLevel: string | null
}

// ---- §5.8 攻略详情 ----

export interface GuidePlanItem {
  name: string | null
  note: string | null
  price: string | null
}

export interface GuidePlanSection {
  /** stay / food / spot / transport，未知值走默认图标 */
  type: string
  title: string
  items: GuidePlanItem[]
}

export interface GuidePostDetail extends GuidePostItem {
  /** 图集外链，首元素与 coverUrl 同源 */
  images: string[] | null
  /** 原文外链；接口保留字段，前端不再展示 */
  sourceUrl: string | null
  planSections: GuidePlanSection[] | null
}

// ---- §5 FR-3 个性化攻略生成 ----

export interface GuidebookTimeSlot {
  start: string | null
  end: string | null
}

export interface GuidebookPlace {
  name: string | null
  lat?: number | null
  lng?: number | null
}

export interface GuidebookNode {
  nodeId: string | null
  timeSlot: GuidebookTimeSlot | null
  /** activity / meal / transport / stay / free */
  type: string | null
  place: GuidebookPlace | null
  resourceSlotId?: string | null
  /** 已绑定的资源详情；未绑定或解析失败为 null */
  resource?: ResourceItem | null
  source: string | null
  dataFreshness: string | null
  /** OSM 营业时间原始值；未查到为 null */
  openingHours?: string | null
  openingHoursSource?: string | null
  /** 结构化溯源引用；无资料时为一条 general 引用 */
  citations?: Citation[] | null
  note: string | null
}

export interface Citation {
  /** prompt 中的 [K1] 编号；通用知识为 null */
  code: string | null
  /** encyclopedia / travelogue / route / expert / guide / general */
  type: string
  title: string | null
  sourceName: string | null
  sourceUrl: string | null
  publishedAt: string | null
  summary: string | null
}

// ---- §13 FR-6 实时数据 ----

export interface DataBlock {
  source: string
  /** ok / unavailable */
  status: string
  summary: string | null
  data: Record<string, unknown> | null
  fetchedAt: string | null
}

export interface TravelDataSnapshot {
  fetchedAt: string | null
  blocks: DataBlock[]
}

export interface GuidebookDay {
  day: number | null
  nodes: GuidebookNode[] | null
}

export interface BudgetItem {
  category: string | null
  min: number | null
  max: number | null
}

export interface BudgetBreakdown {
  currency: string | null
  items: BudgetItem[] | null
  total: { min: number | null; max: number | null } | null
}

export interface GuidebookDetail {
  guidebookId: string
  status: string
  version: number | null
  title: string | null
  intent: Record<string, unknown> | null
  days: GuidebookDay[] | null
  budgetBreakdown: BudgetBreakdown | null
  packingList: string[] | null
  culturalTips: string[] | null
  pitfallTips: string[] | null
  disclaimer: string | null
  degradedMode: boolean | null
  degradationReasons: string[] | null
  travelData?: TravelDataSnapshot | null
  travelDataAt?: string | null
  currentStep: string | null
  progress: number | null
  errorMessage: string | null
}

export interface GenerateGuidebookResponse {
  guidebookId: string
  status: string
  estimatedSeconds: number
}

export interface GuidebookListItem {
  guidebookId: string
  title: string | null
  status: string
  progress: number | null
  version: number | null
  updatedAt: string
}

export interface GuidebookExportResponse {
  format: string
  filename: string
  content: string
}

// ---- §7 FR-5 攻略编辑 ----
export interface NodeConflict{
  /** time_overlap / unreachable / budget_exceeded / closed_time */
  type: string
  day: number | null
  nodeIds: string[]
  message: string
}

export interface GuidebookEditResponse{
  guidebookId: string
  version: number
  changeSummary: string
  conflicts: NodeConflict[]
}

export interface GuidebookValidateResponse{
  valid: boolean
  conflicts: NodeConflict[]
}

export interface GuidebookConfirmResponse{
  guidebookId: string
  status: string
}

export interface GuidebookVersionItem{
  version: number
  title: string | null
  /** generate / manual / adjust / restore */
  changeType: string
  changeSummary: string | null
  createdAt: string
}

export interface GuidebookAdjustResponse extends GuidebookEditResponse{
  changes: Record<string, unknown>[]
}

export interface NodePayload{
  timeSlot: GuidebookTimeSlot
  place: GuidebookPlace
  note?: string | null
}

// ---- §8 FR-8 资源与预订 ----

export interface ResourcePriceRange {
  min: number | null
  max: number | null
  currency: string | null
}

export interface ResourceItem {
  resourceSlotId: string
  /** flight / hotel / ticket / car / dining */
  type: string
  supplier: string | null
  priceRange: ResourcePriceRange | null
  /** available / tight / sold_out / link_error */
  availability: string | null
  refundPolicy: string | null
  /** 服务端签名后的转链；不可预订时为 null */
  bookingLink: string | null
  freshness: string | null
}

export interface LinkValidateResult {
  resourceSlotId: string
  valid: boolean
  /** not_found / stale / sold_out / link_error */
  reason: string | null
  fallbackSupplier: string | null
}

// ---- §9 FR-9 离线攻略包 ----
export type OfflinePackFormat = 'pdf' | 'html'

export interface OfflinePackTask{
  taskId: string
  /** processing / completed / failed */
  status: string
}

export interface OfflinePackStatus{
  taskId: string
  /** processing / completed / failed */
  status: string
  downloadUrl: string | null
  expiresAt: string | null
  errorMessage: string | null
}

// ---- §6 FR-4 多方案对比与混搭 ----

export interface CompareGuidebookItem {
  guidebookId: string
  /** budget / balanced / luxury */
  variant: string
  totalBudget: { min: number | null; max: number | null } | null
}

export interface CompareResponse {
  comparisonId: string
  guidebooks: CompareGuidebookItem[]
}

export interface ComparisonVariant {
  guidebookId: string
  variant: string
  status: string
  progress: number | null
  title: string | null
  totalBudget: { min: number | null; max: number | null } | null
  dayCount: number
  highlights: string[] | null
}

export interface ComparisonDetail {
  comparisonId: string
  baseGuidebookId: string
  /** processing / completed / partial / failed */
  status: string
  errorMessage: string | null
  variants: ComparisonVariant[]
}

export interface MixPick {
  guidebookId: string
  nodeId: string
}

export interface MixResponse {
  guidebookId: string | null
  /** draft / blocked */
  status: string
  conflicts: NodeConflict[]
}

// ---- §10 FR-10 行程伴侣 ----

export interface SuggestionOperation {
  /** move_node / swap_node */
  op: string
  nodeId: string | null
  toDay: number | null
  timeSlot: GuidebookTimeSlot | null
  /** swap_node 使用 */
  nodeB: string | null
}

export interface TripSuggestion {
  suggestionId: string
  /** weather_relocate / opening_hours / local_news */
  type: string
  reason: string | null
  action: string | null
  operations: SuggestionOperation[] | null
  alternatives: Record<string, unknown>[] | null
  evidence: Record<string, unknown> | null
  status: 'pending' | 'accepted' | 'rejected' | 'expired'
  createdAt: string | null
  expiresAt: string | null
}

export interface TodayNode {
  nodeId: string
  day: number
  timeSlotStart: string | null
  timeSlotEnd: string | null
  type: string | null
  placeName: string | null
  note: string | null
  executionStatus: 'executed' | 'skipped' | 'rescheduled' | null
  markedAt: string | null
}

export interface TripCompanion {
  guidebookId: string
  status: string
  destination: string | null
  startDate: string | null
  endDate: string | null
  currentDay: number | null
  executedCount: number
  skippedCount: number
  rescheduledCount: number
  totalCount: number
  progress: number
  todayNodes: TodayNode[] | null
  suggestions: TripSuggestion[] | null
}

export interface SuggestionDecision {
  suggestionId: string
  status: string
  /** false 表示提示类建议只记录、未改动行程 */
  applied: boolean
  version: number | null
  summary: string | null
}

export interface NodeStatusResult {
  guidebookId: string
  nodeId: string
  status: string
  markedAt: string
  progress: number
}

export interface RefreshResult {
  guidebookId: string
  created: number
}

// ---- §11 FR-11 复盘反馈 ----
export interface ProfileSuggestion {
  /** 只可能出现 pace / budgetLevel / interestTags / tripDays */
  fields: Record<string, unknown>
  reason: string | null
  confidence: number | null
}

export type ReviewIterationStatus = 'pending' | 'accepted' | 'rejected'

export interface ReviewDto {
  reviewId: string
  guidebookId: string
  overallScore: number
  aiAccuracyScore: number | null
  nodeExecutionScore: number | null
  content: string | null
  images: string[] | null
  executedCount: number
  skippedCount: number
  rescheduledCount: number
  untouchedCount: number
  totalCount: number
  iterationStatus: ReviewIterationStatus
  profileVersionAfter: number | null
  decidedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface ReviewSummary {
  guidebookId: string
  status: string
  totalCount: number
  executedCount: number
  skippedCount: number
  rescheduledCount: number
  untouchedCount: number
  /** 0–1，展示时再换成百分比 */
  skipRate: number
  aiSummary: string
  summarySource: 'ai' | 'template'
  profileSuggestion: ProfileSuggestion | null
  review: ReviewDto | null
}

export interface ReviewSubmitBody {
  guidebookId: string
  overallScore: number
  aiAccuracyScore?: number | null
  nodeExecutionScore?: number | null
  content?: string | null
  images?: string[]
}

// ---- §12 FR-12 分享与再生成 ----

export interface ShareCreateResponse {
  /** 短码，不是数字 ID */
  shareId: string
  shareUrl: string
  qrcodeUrl: string | null
}

export interface SharedGuidebook {
  shareCode: string
  title: string | null
  guidebookVersion: number | null
  sharedAt: string | null
  expiresAt: string | null
  days: GuidebookDay[] | null
  budgetBreakdown: BudgetBreakdown | null
  packingList: string[] | null
  culturalTips: string[] | null
  pitfallTips: string[] | null
  disclaimer: string | null
  degradedMode: boolean | null
  travelDataAt: string | null
}

export interface FreshnessDiffItem {
  nodeId: string
  /** 分享快照里的采集时间；缺失时为空 */
  old: string | null
  /** 后端当前恒为 pending_refresh */
  new: string
}

export interface RegenerateResponse {
  guidebookId: string
  status: string
  dataFreshnessDiff: FreshnessDiffItem[]
}

// ---- §13 FR-13 达人工作台 ----

export interface ExpertClientItem {
  clientId: string
  name: string
  contact: string | null
  note: string | null
  linkedUserId: string | null
  status: string
  createdAt: string
  /** active / pending / none：由服务端派生的授权状态 */
  authorizationStatus: string | null
  authorizedScopes: string[] | null
  authorizedAt: string | null
}

export interface ExpertClientCreateBody {
  name: string
  contact?: string | null
  note?: string | null
  linkedUserId?: string | null
}

export interface AuthorizeIssued {
  authorizationId: string
  /** 明文令牌，只在创建响应中出现一次 */
  confirmToken: string
  expiresAt: string
  scope: string[]
}

export interface ExpertClientAccessLog {
  logId: string
  action: string
  scope: string | null
  resourceType: string | null
  resourceId: string | null
  ip: string | null
  createdAt: string
}

export interface BatchIntent {
  destination: string
  dateWindow: { start: string; end: string }
  party: { adults: number }
  budget: { amount: number; currency: string }
}

export interface BatchTask {
  taskId: string
  /** generating / failed */
  status: string
}

export interface ExpertPublicationItem {
  publicationId: string
  guidebookId: string
  title: string
  status: string
  priceCents: number
  commissionRate: number
  rejectReason: string | null
  createdAt: string
}

export interface PublishBody {
  title: string
  description?: string | null
  coverUrl?: string | null
  priceCents?: number
  tags?: string[]
}

export interface EarningItem {
  earningId: string
  publicationId: string
  guideTitle: string
  amountCents: number
  /** pending / settled */
  status: string
  createdAt: string
}

export interface EarningsPage {
  list: EarningItem[]
  page: number
  pageSize: number
  total: number
  settlementAvailable: boolean
}
