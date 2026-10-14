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
  nickname: string
  userLevel: string
  profileCompleted: boolean
  dailyGenerateQuota: number
  dailyGenerateUsed: number
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
