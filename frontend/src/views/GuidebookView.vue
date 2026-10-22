<template>
  <div class="guidebook-page">
    <template v-if="!guidebookId">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>我的攻略</span>
            <el-button size="small" type="primary" @click="goChat">新建攻略</el-button>
          </div>
        </template>

        <div class="list-filters">
          <el-radio-group v-model="activeStatus" size="small" @change="loadList">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="generating">生成中</el-radio-button>
            <el-radio-button value="draft">草稿</el-radio-button>
            <el-radio-button value="editing">编辑中</el-radio-button>
            <el-radio-button value="confirmed">已确认</el-radio-button>
            <el-radio-button value="completed">已完成</el-radio-button>
          </el-radio-group>
        </div>

        <el-skeleton v-if="listLoading" :rows="5" animated />

        <ul v-else-if="guidebooks.length" class="guidebook-list">
          <li
            v-for="item in guidebooks"
            :key="item.guidebookId"
            @click="openGuidebook(item.guidebookId)"
          >
            <div class="gb-main">
              <div class="gb-title">{{ item.title || '未命名攻略' }}</div>
              <div class="gb-meta">
                <el-tag size="small" :type="statusType(item.status)">
                  {{ statusLabel(item.status) }}
                </el-tag>
                <el-tag v-if="item.version" size="small" effect="plain">v{{ item.version }}</el-tag>
                <span class="muted">更新于 {{ formatTime(item.updatedAt) }}</span>
              </div>
            </div>
            <div class="gb-side">
              <el-progress
                v-if="item.status === 'generating'"
                :percentage="item.progress ?? 0"
                :stroke-width="6"
                class="gb-progress"
              />
              <span class="gb-arrow">›</span>
            </div>
          </li>
        </ul>

        <div v-else class="empty-state">
          <div class="empty-ico">🗺️</div>
          <h3>这里还没有攻略</h3>
          <p>在「对话」里描述你的旅行需求，信息收集完成后即可一键生成行程与攻略。</p>
          <el-button type="primary" @click="goChat">去对话</el-button>
        </div>
      </el-card>
    </template>

    <el-card v-else-if="loading && !detail">
      <el-skeleton :rows="8" animated />
    </el-card>

    <el-card v-else-if="detail" class="detail-card">
      <template #header>
        <div class="card-header">
          <div class="title-group">
            <span class="detail-title">{{ detail.title || '攻略生成中' }}</span>
            <el-tag size="small" :type="statusType(detail.status)">
              {{ statusLabel(detail.status) }}
            </el-tag>
            <el-tag v-if="detail.version" size="small" effect="plain">v{{ detail.version }}</el-tag>
          </div>
          <el-button size="small" text @click="goChat">返回对话</el-button>
        </div>
      </template>

      <!-- 生成中：WS 推送 + 轮询兜底 -->
      <div v-if="detail.status === 'generating'" class="generating">
        <el-progress
          type="dashboard"
          :percentage="progress"
          :width="148"
          :stroke-width="10"
          color="#3b82f6"
        />
        <div class="step-title">{{ stepLabel }}</div>
        <el-steps :active="stepIndex" align-center finish-status="success" class="steps">
          <el-step v-for="s in HIGH_LEVEL_STEPS" :key="s" :title="s" />
        </el-steps>
        <p class="generating-note">正在结合你的画像、目的地资料与实时数据生成，预计约 30 秒。</p>
      </div>

      <!-- 失败：可重试 -->
      <div v-else-if="detail.status === 'failed'" class="failed">
        <el-alert
          type="error"
          :title="detail.errorMessage || '攻略生成失败'"
          description="可以重新生成，已消耗的配额不会重复扣除。"
          show-icon
          :closable="false"
        />
        <div class="actions">
          <el-button type="primary" :loading="retrying" @click="onRetry">重新生成</el-button>
          <el-button @click="goChat">返回对话</el-button>
        </div>
      </div>

      <!-- 完成：日程 / 预算 / 贴士 / 溯源 -->
      <div v-else class="draft">
        <el-alert
          v-if="detail.degradedMode"
          type="warning"
          :closable="false"
          show-icon
          title="本次为降级生成"
          :description="degradationText"
          class="degraded-alert"
        />

        <div v-if="travelBlocks.length" class="freshness-panel">
          <div class="freshness-head">
            <span>数据时效</span>
            <span v-if="dataFetchedAt" class="muted">采集于 {{ dataFetchedAt }}</span>
          </div>
          <div class="freshness-list">
            <el-tag
              v-for="block in travelBlocks"
              :key="block.source"
              size="small"
              effect="plain"
              :type="block.status === 'ok' ? 'success' : 'info'"
            >
              {{ sourceLabel(block.source) }}：{{
                block.status === 'ok' ? (block.summary || '已采集') : '时效未知'
              }}
            </el-tag>
          </div>
        </div>

        <div class="edit-toolbar">
          <template v-if="editable">
            <el-input
              v-model="instruction"
              class="adjust-input"
              maxlength="500"
              show-word-limit
              placeholder="用一句话描述想怎么调整，例如：Day2 太赶了，把博物馆挪到第三天"
              @keyup.enter="onAdjust"
            />
            <el-button type="primary" :loading="adjusting" @click="onAdjust">AI 调整</el-button>
          </template>
          <el-button @click="openVersions">版本历史</el-button>
          <el-button v-if="canCompare" @click="openCompare">生成对比方案</el-button>
          <el-button v-if="canUseCompanion" type="primary" plain @click="openCompanion">
            {{ detail.status === 'executing' ? '进入行程伴侣' : '行程伴侣' }}
          </el-button>
          <el-button
            v-if="editable"
            type="success"
            :loading="confirming"
            :disabled="conflicts.length > 0"
            @click="onConfirm"
          >
            确认攻略
          </el-button>
        </div>

        <el-alert
          v-if="conflicts.length"
          type="warning"
          show-icon
          :closable="false"
          class="conflict-alert"
          :title="`存在 ${conflicts.length} 处冲突，处理后才能确认`"
        >
          <ul class="conflict-list">
            <li v-for="(c, i) in conflicts" :key="`${c.type}-${i}`">
              {{ c.day ? `第 ${c.day} 天 · ` : '' }}{{ c.message }}
            </li>
          </ul>
        </el-alert>

        <el-tabs v-model="activeDay">
          <el-tab-pane
            v-for="day in days"
            :key="String(day.day)"
            :label="`第 ${day.day} 天`"
            :name="String(day.day)"
          >
            <div v-if="editable" class="day-toolbar">
              <el-button size="small" @click="openCreate(day.day)">新增节点</el-button>
              <span class="muted">拖动节点可调整同日顺序；跨天请用「移到」菜单。</span>
            </div>

            <div v-if="day.nodes.length" class="node-list">
              <VueDraggable
                v-model="day.nodes"
                :animation="150"
                :handle="editable ? '.drag-handle' : undefined"
                :disabled="!editable"
                @end="onDragEnd(day, $event)"
              >
                <div
                  v-for="node in day.nodes"
                  :key="node.nodeId ?? node.place?.name ?? 'node'"
                  class="node-item"
                >
                  <span v-if="editable" class="drag-handle" title="拖动排序">⠿</span>
                  <div class="node-card">
                    <div class="node-head">
                      <strong>{{ node.place?.name || '待定地点' }}</strong>
                      <div class="node-head-right">
                        <el-tag size="small" effect="plain">{{ typeLabel(node.type) }}</el-tag>
                        <span class="node-time">{{ timeRange(node) }}</span>
                      </div>
                    </div>
                    <p v-if="node.note" class="node-note">{{ node.note }}</p>
                    <div class="node-source">
                      来源：{{ node.source || '通用知识' }}
                      <span v-if="node.dataFreshness"> · 数据时间 {{ node.dataFreshness }}</span>
                    </div>
                    <div v-if="node.openingHours" class="node-source">
                      营业时间：{{ node.openingHours }}
                      <span v-if="node.openingHoursSource">
                        （{{ node.openingHoursSource }}，出行前请核实）
                      </span>
                    </div>
                    <div v-if="node.citations?.length" class="node-citations">
                      <component
                        :is="citation.sourceUrl ? 'a' : 'span'"
                        v-for="(citation, i) in node.citations"
                        :key="`${citation.code ?? 'general'}-${i}`"
                        :href="citation.sourceUrl ?? undefined"
                        :target="citation.sourceUrl ? '_blank' : undefined"
                        :rel="citation.sourceUrl ? 'noopener noreferrer' : undefined"
                        class="citation"
                        :title="citation.summary ?? undefined"
                      >
                        {{ citationLabel(citation) }}
                      </component>
                    </div>
                    <div v-if="node.resource" class="node-resource">
                      <div class="resource-head">
                        <span class="resource-supplier">{{ node.resource.supplier || '资源供应商' }}</span>
                        <el-tag
                          size="small"
                          :type="availabilityTagType(node.resource.availability)"
                        >
                          {{ availabilityLabel(node.resource.availability) }}
                        </el-tag>
                      </div>
                      <div v-if="node.resource.priceRange" class="node-source">
                        价格：{{ priceRangeText(node.resource.priceRange) }}
                      </div>
                      <div v-if="node.resource.refundPolicy" class="node-source">
                        退改：{{ node.resource.refundPolicy }}
                      </div>
                      <div v-if="node.resource.freshness" class="node-source">
                        数据时间：{{ node.resource.freshness }}
                      </div>
                      <div v-if="linkIssue(node.resource)" class="node-source resource-issue">
                        {{ linkIssue(node.resource) }}
                      </div>
                      <el-button
                        size="small"
                        type="primary"
                        plain
                        class="book-button"
                        :disabled="!node.resource.bookingLink"
                        @click="onBook(node.resource)"
                      >
                        {{ node.resource.bookingLink ? '预订' : '暂不可预订' }}
                      </el-button>
                    </div>
                    <div v-if="editable && node.nodeId" class="node-actions">
                      <el-button size="small" text @click="openEdit(day.day, node)">编辑</el-button>
                      <el-button size="small" text @click="openBind(day.day, node)">绑定资源</el-button>
                      <el-button size="small" text type="danger" @click="onDeleteNode(day.day, node)">
                        删除
                      </el-button>
                      <el-dropdown
                        v-if="days.length > 1"
                        trigger="click"
                        @command="moveHandler(day.day, node)"
                      >
                        <el-button size="small" text>移到</el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item
                              v-for="d in days"
                              :key="d.day"
                              :command="d.day"
                              :disabled="d.day === day.day"
                            >
                              第 {{ d.day }} 天
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </div>
                </div>
              </VueDraggable>
            </div>
            <el-empty v-else description="本日暂无安排" />
          </el-tab-pane>
        </el-tabs>

        <div class="info-grid">
          <section v-if="budget">
            <h3>预算拆解</h3>
            <el-table :data="budget.items ?? []" size="small" border>
              <el-table-column prop="category" label="类别" />
              <el-table-column label="最低" align="right">
                <template #default="{ row }">{{ money(row.min) }}</template>
              </el-table-column>
              <el-table-column label="最高" align="right">
                <template #default="{ row }">{{ money(row.max) }}</template>
              </el-table-column>
            </el-table>
            <p v-if="budget.total" class="budget-total">
              合计 {{ money(budget.total.min) }} - {{ money(budget.total.max) }}
              {{ budget.currency || 'CNY' }}
            </p>
          </section>

          <section class="tips">
            <h3>行前准备</h3>
            <ul v-if="detail.packingList?.length">
              <li v-for="item in detail.packingList" :key="item">{{ item }}</li>
            </ul>
            <p v-else class="muted">暂无</p>

            <h3>文化贴士</h3>
            <ul v-if="detail.culturalTips?.length">
              <li v-for="item in detail.culturalTips" :key="item">{{ item }}</li>
            </ul>
            <p v-else class="muted">暂无</p>

            <h3>避坑提示</h3>
            <ul v-if="detail.pitfallTips?.length">
              <li v-for="item in detail.pitfallTips" :key="item">{{ item }}</li>
            </ul>
            <p v-else class="muted">暂无</p>
          </section>
        </div>

        <div class="actions">
          <el-button @click="onExport('markdown')">导出 Markdown</el-button>
          <el-button @click="onExport('html')">导出 HTML</el-button>
          <el-button @click="openOfflinePack">离线包</el-button>
          <el-button v-if="shareable" @click="shareDialog = true">分享</el-button>
          <el-button type="danger" plain :loading="deleting" @click="onDelete">删除攻略</el-button>
        </div>

        <p v-if="detail.disclaimer" class="disclaimer">{{ detail.disclaimer }}</p>

        <ReviewPanel
          v-if="detail.status === 'completed'"
          :guidebook-id="detail.guidebookId"
          class="review-section"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="nodeDialog"
      :title="nodeMode === 'create' ? '新增节点' : '编辑节点'"
      width="520px"
    >
      <el-form label-width="72px">
        <el-form-item v-if="nodeMode === 'create'" label="类型">
          <el-select v-model="nodeForm.type">
            <el-option
              v-for="(label, key) in TYPE_LABELS"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="nodeForm.name" placeholder="例如：宽窄巷子" />
        </el-form-item>
        <el-form-item label="时间">
          <div class="time-row">
            <el-input v-model="nodeForm.start" placeholder="09:00" maxlength="5" />
            <span class="time-sep">至</span>
            <el-input v-model="nodeForm.end" placeholder="11:00" maxlength="5" />
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="nodeForm.note"
            type="textarea"
            :rows="2"
            placeholder="可选，例如：提前预约"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialog = false">取消</el-button>
        <el-button type="primary" :loading="submittingNode" @click="onSubmitNode">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resourceDialog" title="绑定资源" width="560px">
      <el-form label-width="84px">
        <el-form-item label="资源类型">
          <el-select
            v-model="resourceForm.type"
            :disabled="resourceBinding"
            @change="onSearchResources"
          >
            <el-option
              v-for="(label, key) in RESOURCE_TYPE_LABELS"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div v-loading="resourceSearching" class="resource-results">
        <p class="muted">
          {{ resourceContext.name ? `为「${resourceContext.name}」选择资源` : '选择要绑定的资源' }}
        </p>
        <div
          v-for="item in resourceResults"
          :key="item.resourceSlotId"
          class="resource-option"
        >
          <div class="resource-option-main">
            <div class="resource-head">
              <strong>{{ item.supplier || item.resourceSlotId }}</strong>
              <el-tag size="small" :type="availabilityTagType(item.availability)">
                {{ availabilityLabel(item.availability) }}
              </el-tag>
            </div>
            <div v-if="item.priceRange" class="node-source">
              {{ priceRangeText(item.priceRange) }}
            </div>
            <div v-if="item.refundPolicy" class="node-source">
              退改：{{ item.refundPolicy }}
            </div>
          </div>
          <el-button
            size="small"
            type="primary"
            :loading="resourceBinding"
            @click="onBindResource(item)"
          >
            绑定
          </el-button>
        </div>
        <el-empty v-if="!resourceSearching && !resourceResults.length" description="没有可用资源" />
      </div>

      <template #footer>
        <el-button
          v-if="resourceForm.currentId"
          type="danger"
          plain
          :loading="resourceBinding"
          @click="onUnbindResource"
        >
          解绑当前资源
        </el-button>
        <el-button @click="resourceDialog = false">取消</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="versionDrawer" title="版本历史" size="420px">
      <div v-loading="versionsLoading" class="version-body">
        <el-timeline v-if="versions.length">
          <el-timeline-item
            v-for="v in versions"
            :key="v.version"
            :timestamp="v.createdAt"
            placement="top"
          >
            <div class="version-row">
              <div class="version-info">
                <div class="version-head">
                  <strong>v{{ v.version }}</strong>
                  <el-tag size="small" effect="plain">{{ changeTypeLabel(v.changeType) }}</el-tag>
                </div>
                <p class="muted">{{ v.changeSummary || '无变更摘要' }}</p>
              </div>
              <el-button
                v-if="editable"
                size="small"
                :loading="restoringVersion === v.version"
                @click="onRestore(v)"
              >
                回溯
              </el-button>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无版本记录" />
      </div>
    </el-drawer>

    <el-dialog v-model="compareDialog" title="生成对比方案" width="440px">
      <p class="muted compare-hint">
        一次对比计为一次生成；三套方案顺序生成，通常需要 1-3 分钟。
      </p>
      <el-checkbox-group v-model="compareVariants">
        <el-checkbox value="budget">省钱优先</el-checkbox>
        <el-checkbox value="balanced">均衡方案</el-checkbox>
        <el-checkbox value="luxury">品质优先</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="compareDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="compareStarting"
          :disabled="compareVariants.length < 2"
          @click="onStartCompare"
        >
          生成
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="offlineDialog" title="离线攻略包" width="460px">
      <el-form label-width="88px">
        <el-form-item label="格式">
          <el-radio-group v-model="offlineForm.format" :disabled="offlineBusy">
            <el-radio value="pdf">PDF</el-radio>
            <el-radio value="html">离线 HTML</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="地图">
          <el-switch v-model="offlineForm.includeMaps" :disabled="offlineBusy" />
          <span class="offline-hint">包含每日路线截图；获取失败时保留坐标</span>
        </el-form-item>
      </el-form>

      <div v-if="offlineStatus" class="offline-status">
        <el-alert
          v-if="offlineStatus === 'processing'"
          type="info"
          :closable="false"
          show-icon
          title="正在生成离线包…"
          description="通常需要几秒到一分钟，可以关闭弹窗稍后回来查看。"
        />
        <el-alert
          v-else-if="offlineStatus === 'completed'"
          type="success"
          :closable="false"
          show-icon
          title="离线包已生成"
        />
        <el-alert
          v-else
          type="error"
          :closable="false"
          show-icon
          title="生成失败"
          :description="offlineError || '请重试'"
        />
      </div>

      <template #footer>
        <el-button @click="offlineDialog = false">关闭</el-button>
        <el-button
          v-if="!offlineStatus || offlineStatus === 'failed'"
          type="primary"
          :loading="offlineCreating"
          @click="onCreateOfflinePack"
        >
          生成
        </el-button>
        <el-button
          v-if="offlineStatus === 'completed'"
          type="success"
          :loading="offlineDownloading"
          @click="onDownloadOfflinePack"
        >
          下载
        </el-button>
      </template>
    </el-dialog>

    <ShareDialog
      v-if="detail"
      v-model="shareDialog"
      :guidebook-id="detail.guidebookId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VueDraggable } from 'vue-draggable-plus'
import * as compareApi from '@/api/compare'
import * as guidebookApi from '@/api/guidebook'
import * as offlinePackApi from '@/api/offlinePack'
import * as resourceApi from '@/api/resource'
import ReviewPanel from '@/components/ReviewPanel.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import { openGuidebookSocket, type GuidebookProgressEvent } from '@/utils/guidebookWs'
import type {
  BudgetBreakdown,
  Citation,
  GuidebookDetail,
  GuidebookListItem,
  GuidebookNode,
  GuidebookVersionItem,
  LinkValidateResult,
  NodeConflict,
  OfflinePackFormat,
  ResourceItem,
  ResourcePriceRange
} from '@/types/api'

const route = useRoute()
const router = useRouter()

const STATUS_LABELS: Record<string, string> = {
  generating: '生成中',
  draft: '草稿',
  editing: '编辑中',
  failed: '生成失败',
  confirmed: '已确认',
  executing: '行程中',
  completed: '已完成'
}
const STEP_LABELS: Record<string, string> = {
  queued: '排队中',
  intent_validation: '校验意图',
  knowledge_retrieval: '检索目的地资料',
  travel_data: '获取天气与汇率',
  prompt_assembly: '组装生成提示',
  itinerary_compose: 'AI 生成行程',
  output_validation: '校验行程结构',
  opening_hours_enrichment: '补充营业时间',
  traceability: '标注数据溯源',
  persist: '保存攻略',
  completed: '生成完成',
  failed: '生成失败'
}
const HIGH_LEVEL_STEPS = ['校验意图', '检索资料', '实时数据', 'AI 生成', '校验保存']
const TYPE_LABELS: Record<string, string> = {
  activity: '活动',
  meal: '餐饮',
  transport: '交通',
  stay: '住宿',
  free: '自由活动'
}
const CHANGE_TYPE_LABELS: Record<string, string> = {
  generate: '初次生成',
  manual: '手动编辑',
  adjust: 'AI 调整',
  restore: '版本回溯',
  companion: '行程调整'
}
const DEGRADE_LABELS: Record<string, string> = {
  no_knowledge: '未检索到目的地资料',
  weather_unavailable: '天气数据不可用',
  exchange_unavailable: '汇率数据不可用',
  local_news_unavailable: '当地动态不可用',
  visa_unavailable: '签证政策暂无资料',
  flight_price_unavailable: '机票价格时效未知',
  hotel_price_unavailable: '酒店价格时效未知',
  opening_hours_enrichment_failed: '营业时间补充失败',
  model_fallback: 'AI 模型不可用，已使用模板',
  json_repaired: 'AI 输出经过一次修复',
  validation_fallback: '行程校验失败，已使用模板',
  validation_repaired: '行程结构经过自动修复',
  day_count_mismatch: '生成天数与请求不一致',
  data_unavailable: '部分实时数据缺失'
}
const EDITABLE_STATUSES = ['draft', 'editing']
const SHAREABLE_STATUSES = ['confirmed', 'executing', 'completed']
const TIME_PATTERN = /^\d{2}:\d{2}$/
const RESOURCE_TYPE_LABELS: Record<string, string> = {
  flight: '机票',
  hotel: '酒店',
  ticket: '门票/体验',
  car: '用车',
  dining: '餐饮'
}
const RESOURCE_TYPE_BY_NODE: Record<string, string> = {
  stay: 'hotel',
  transport: 'flight',
  meal: 'dining',
  activity: 'ticket',
  free: 'ticket'
}
const AVAILABILITY_LABELS: Record<string, string> = {
  available: '可预订',
  tight: '余量紧张',
  sold_out: '已售罄',
  link_error: '链接异常'
}
const LINK_REASON_LABELS: Record<string, string> = {
  not_found: '资源已下架',
  stale: '数据已过期',
  sold_out: '已售罄',
  link_error: '预订链接异常'
}

/** 本地可编辑副本：nodes 始终为数组，拖拽后以服务端返回为准重新加载。 */
interface EditableDay {
  day: number
  nodes: GuidebookNode[]
}

const guidebookId = computed(() => {
  const id = route.params.guidebookId
  return typeof id === 'string' ? id : ''
})
const detail = ref<GuidebookDetail | null>(null)
const guidebooks = ref<GuidebookListItem[]>([])
const listLoading = ref(false)
const activeStatus = ref('')
const days = ref<EditableDay[]>([])
const loading = ref(false)
const retrying = ref(false)
const deleting = ref(false)
const activeDay = ref('1')

const instruction = ref('')
const adjusting = ref(false)
const confirming = ref(false)
const conflicts = ref<NodeConflict[]>([])

const nodeDialog = ref(false)
const nodeMode = ref<'create' | 'edit'>('create')
const submittingNode = ref(false)
const nodeContext = reactive({ day: 1, nodeId: '', lat: null as number | null, lng: null as number | null })
const nodeForm = reactive({ type: 'activity', name: '', start: '', end: '', note: '' })

const versionDrawer = ref(false)
const versions = ref<GuidebookVersionItem[]>([])
const versionsLoading = ref(false)
const restoringVersion = ref<number | null>(null)

const compareDialog = ref(false)
const compareVariants = ref<string[]>(['budget', 'balanced', 'luxury'])
const compareStarting = ref(false)

const offlineDialog = ref(false)
const offlineForm = reactive({ format: 'pdf' as OfflinePackFormat, includeMaps: true })
const offlineTaskId = ref<string | null>(null)
const offlineStatus = ref<string | null>(null)
const offlineError = ref<string | null>(null)
const offlineCreating = ref(false)
const offlineDownloading = ref(false)
const shareDialog = ref(false)

const resourceDialog = ref(false)
const resourceSearching = ref(false)
const resourceBinding = ref(false)
const resourceResults = ref<ResourceItem[]>([])
const resourceContext = reactive({ day: 1, nodeId: '', name: '' })
const resourceForm = reactive({ type: 'ticket', currentId: null as string | null })
const linkChecks = ref<Record<string, LinkValidateResult>>({})

let socket: WebSocket | null = null
let pollTimer: number | null = null
let offlinePollTimer: number | null = null
let offlinePollStartedAt = 0

const editable = computed(() => EDITABLE_STATUSES.includes(detail.value?.status ?? ''))
const canCompare = computed(() =>
  ['draft', 'editing', 'confirmed'].includes(detail.value?.status ?? '')
)
const shareable = computed(() =>
  SHAREABLE_STATUSES.includes(detail.value?.status ?? '')
)
const canUseCompanion = computed(() =>
  ['confirmed', 'executing'].includes(detail.value?.status ?? '')
)
const offlineBusy = computed(() => offlineStatus.value === 'processing')
const budget = computed<BudgetBreakdown | null>(() => detail.value?.budgetBreakdown ?? null)
const progress = computed(() => Math.max(0, Math.min(100, detail.value?.progress ?? 0)))
const stepLabel = computed(() => STEP_LABELS[detail.value?.currentStep ?? ''] ?? '生成中')
const stepIndex = computed(() => {
  const step = detail.value?.currentStep ?? ''
  if (step === 'knowledge_retrieval') return 1
  if (step === 'travel_data' || step === 'prompt_assembly') return 2
  if (step === 'itinerary_compose') return 3
  if (step === 'output_validation' || step === 'opening_hours_enrichment'
    || step === 'traceability' || step === 'persist' || step === 'completed') return 5
  return 0
})
const degradationText = computed(() => {
  const reasons = detail.value?.degradationReasons ?? []
  if (!reasons.length) return '部分资料或实时数据缺失，内容仅供参考。'
  return reasons.map((r) => DEGRADE_LABELS[r] ?? r).join('；')
})
const travelBlocks = computed(() => detail.value?.travelData?.blocks ?? [])
const dataFetchedAt = computed(
  () => detail.value?.travelDataAt ?? detail.value?.travelData?.fetchedAt ?? '',
)
const SOURCE_LABELS: Record<string, string> = {
  weather: '天气',
  exchange: '汇率',
  local_news: '当地动态',
  visa: '签证',
  flight_price: '机票',
  hotel_price: '酒店',
}
function sourceLabel(source: string) {
  return SOURCE_LABELS[source] ?? source
}

function statusLabel(status: string) {
  return STATUS_LABELS[status] ?? status
}
function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'draft') return 'info'
  if (status === 'editing') return 'warning'
  if (status === 'generating') return 'warning'
  if (status === 'failed') return 'danger'
  return 'success'
}
function typeLabel(type: string | null) {
  return type ? TYPE_LABELS[type] ?? type : '活动'
}
function changeTypeLabel(type: string) {
  return CHANGE_TYPE_LABELS[type] ?? type
}
function timeRange(node: GuidebookNode) {
  const { start, end } = node.timeSlot ?? {}
  if (start && end) return `${start} - ${end}`
  return start ?? '时间待定'
}
function money(value: number | null | undefined) {
  if (value == null) return '-'
  return value.toLocaleString('zh-CN')
}

function availabilityLabel(value: string | null) {
  return value ? AVAILABILITY_LABELS[value] ?? value : '状态未知'
}
function availabilityTagType(value: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'available') return 'success'
  if (value === 'tight') return 'warning'
  if (value === 'sold_out') return 'danger'
  return 'info'
}
function priceRangeText(range: ResourcePriceRange) {
  return `${money(range.min)} - ${money(range.max)} ${range.currency || 'CNY'}`
}
function linkIssue(resource: ResourceItem) {
  const check = linkChecks.value[resource.resourceSlotId]
  if (!check || check.valid) return ''
  const reason = LINK_REASON_LABELS[check.reason ?? ''] ?? '链接暂不可用'
  return check.fallbackSupplier
    ? `${reason}，可参考「${check.fallbackSupplier}」`
    : reason
}
function citationLabel(citation: Citation) {
  const prefix = citation.code ? `[${citation.code}] ` : ''
  return prefix + (citation.title || citation.sourceName || '引用')
}
function tripDestination() {
  const value = detail.value?.intent?.destination
  return typeof value === 'string' ? value : ''
}

async function refreshLinkChecks() {
  const ids = new Set<string>()
  for (const day of days.value) {
    for (const node of day.nodes) {
      if (node.resourceSlotId) ids.add(node.resourceSlotId)
    }
  }
  if (!ids.size) {
    linkChecks.value = {}
    return
  }
  try {
    const res = await resourceApi.validateLinks([...ids])
    const map: Record<string, LinkValidateResult> = {}
    for (const item of res.results ?? []) map[item.resourceSlotId] = item
    linkChecks.value = map
  } catch {
    linkChecks.value = {}
  }
}

async function onBook(resource: ResourceItem) {
  if (!resource.bookingLink) return
  try {
    await ElMessageBox.confirm(
      '即将离开本站，预订与交易由合作方完成，是否继续？',
      '前往预订',
      { type: 'info', confirmButtonText: '前往预订', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  window.open(resource.bookingLink, '_blank', 'noopener')
}

function openBind(day: number, node: GuidebookNode) {
  if (!node.nodeId) return
  if (!tripDestination()) {
    ElMessage.warning('攻略缺少目的地信息，无法搜索资源')
    return
  }
  resourceContext.day = day
  resourceContext.nodeId = node.nodeId
  resourceContext.name = node.place?.name ?? ''
  resourceForm.type = RESOURCE_TYPE_BY_NODE[node.type ?? ''] ?? 'ticket'
  resourceForm.currentId = node.resourceSlotId ?? null
  resourceResults.value = []
  resourceDialog.value = true
  void onSearchResources()
}

async function onSearchResources() {
  const destination = tripDestination()
  if (!destination) return
  resourceSearching.value = true
  try {
    const res = await resourceApi.searchResources({
      type: resourceForm.type,
      destination,
      page: 1,
      pageSize: 10
    })
    resourceResults.value = res.list ?? []
  } catch {
    resourceResults.value = []
  } finally {
    resourceSearching.value = false
  }
}

async function onBindResource(item: ResourceItem) {
  if (!guidebookId.value || !resourceContext.nodeId) return
  resourceBinding.value = true
  try {
    await resourceApi.bindResource(guidebookId.value, resourceContext.nodeId, item.resourceSlotId)
    ElMessage.success('已绑定资源')
    resourceDialog.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    resourceBinding.value = false
  }
}

async function onUnbindResource() {
  if (!guidebookId.value || !resourceContext.nodeId) return
  resourceBinding.value = true
  try {
    await resourceApi.bindResource(guidebookId.value, resourceContext.nodeId, null)
    ElMessage.success('已解绑资源')
    resourceDialog.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    resourceBinding.value = false
  }
}

function isGenerating() {
  return detail.value?.status === 'generating'
}

function syncDays(source: GuidebookDetail | null) {
  days.value = (source?.days ?? []).map((d) => ({
    day: d.day ?? 0,
    nodes: (d.nodes ?? []).map((n) => ({ ...n }))
  }))
  if (!days.value.length) return
  if (!days.value.some((d) => String(d.day) === activeDay.value)) {
    activeDay.value = String(days.value[0]?.day ?? 1)
  }
}

async function load() {
  if (!guidebookId.value) return
  loading.value = true
  try {
    detail.value = await guidebookApi.getGuidebook(guidebookId.value)
    syncDays(detail.value)
    void refreshLinkChecks()
    if (isGenerating()) {
      startPolling()
    } else {
      stopPolling()
    }
    if (editable.value) {
      void refreshConflicts()
    } else {
      conflicts.value = []
    }
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function refreshConflicts() {
  if (!guidebookId.value || !editable.value) return
  try {
    const res = await guidebookApi.validateGuidebook(guidebookId.value)
    conflicts.value = res.conflicts ?? []
  } catch {
    // 校验失败不阻断页面，确认时会再次由服务端复检
  }
}

function startPolling() {
  if (pollTimer != null) return
  pollTimer = window.setInterval(() => {
    if (!isGenerating()) {
      stopPolling()
      return
    }
    void load()
  }, 2000)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function openSocket() {
  closeSocket()
  if (!guidebookId.value || !isGenerating()) return
  socket = openGuidebookSocket(guidebookId.value, {
    onProgress: (ev: GuidebookProgressEvent) => {
      if (!detail.value) return
      detail.value.currentStep = ev.currentStep ?? detail.value.currentStep
      detail.value.progress = ev.progress ?? detail.value.progress
      detail.value.status = ev.status
      if (ev.status !== 'generating') {
        stopPolling()
        void load()
      }
    },
    onDone: () => {
      closeSocket()
      stopPolling()
      ElMessage.success('攻略生成完成')
      void load()
    },
    onError: (ev) => {
      ElMessage.error(ev.message || '攻略生成失败')
      closeSocket()
      void load()
    },
    onClose: () => {
      socket = null
      if (isGenerating()) startPolling()
    }
  })
}

function closeSocket() {
  if (socket) {
    socket.close()
    socket = null
  }
}

async function onRetry() {
  if (!guidebookId.value) return
  retrying.value = true
  try {
    await guidebookApi.retryGuidebook(guidebookId.value)
    if (detail.value) {
      detail.value = {
        ...detail.value,
        status: 'generating',
        progress: 0,
        currentStep: 'queued',
        errorMessage: null
      }
    }
    ElMessage.success('已重新开始生成')
    openSocket()
    startPolling()
  } catch {
    // 拦截器已提示
  } finally {
    retrying.value = false
  }
}

async function onAdjust() {
  if (!guidebookId.value) return
  const text = instruction.value.trim()
  if (!text) {
    ElMessage.warning('请先输入调整指令')
    return
  }
  adjusting.value = true
  try {
    const res = await guidebookApi.adjustGuidebook(guidebookId.value, text)
    instruction.value = ''
    ElMessage.success(res.changeSummary || '已完成调整')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    adjusting.value = false
  }
}

async function onConfirm() {
  if (!guidebookId.value) return
  if (conflicts.value.length) {
    ElMessage.warning('请先处理行程冲突再确认')
    return
  }
  try {
    await ElMessageBox.confirm('确认后攻略将不能再编辑，是否继续？', '确认攻略', {
      type: 'warning'
    })
  } catch {
    return
  }
  confirming.value = true
  try {
    await guidebookApi.confirmGuidebook(guidebookId.value)
    ElMessage.success('攻略已确认')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    confirming.value = false
  }
}

function openCreate(day: number) {
  nodeMode.value = 'create'
  nodeContext.day = day
  nodeContext.nodeId = ''
  nodeContext.lat = null
  nodeContext.lng = null
  nodeForm.type = 'activity'
  nodeForm.name = ''
  nodeForm.start = ''
  nodeForm.end = ''
  nodeForm.note = ''
  nodeDialog.value = true
}

function openEdit(day: number, node: GuidebookNode) {
  nodeMode.value = 'edit'
  nodeContext.day = day
  nodeContext.nodeId = node.nodeId ?? ''
  nodeContext.lat = node.place?.lat ?? null
  nodeContext.lng = node.place?.lng ?? null
  nodeForm.type = node.type ?? 'activity'
  nodeForm.name = node.place?.name ?? ''
  nodeForm.start = node.timeSlot?.start ?? ''
  nodeForm.end = node.timeSlot?.end ?? ''
  nodeForm.note = node.note ?? ''
  nodeDialog.value = true
}

async function onSubmitNode() {
  if (!guidebookId.value) return
  const name = nodeForm.name.trim()
  if (!name) {
    ElMessage.warning('请填写地点名称')
    return
  }
  const start = nodeForm.start.trim()
  const end = nodeForm.end.trim()
  if ((start && !TIME_PATTERN.test(start)) || (end && !TIME_PATTERN.test(end))) {
    ElMessage.warning('时间格式应为 HH:mm')
    return
  }
  submittingNode.value = true
  try {
    const timeSlot = { start: start || null, end: end || null }
    if (nodeMode.value === 'create') {
      await guidebookApi.addGuidebookNode(guidebookId.value, nodeContext.day, {
        type: nodeForm.type,
        timeSlot,
        place: { name },
        note: nodeForm.note.trim() || null
      })
    } else {
      await guidebookApi.updateGuidebookNode(
        guidebookId.value,
        nodeContext.day,
        nodeContext.nodeId,
        {
          timeSlot,
          place: { name, lat: nodeContext.lat, lng: nodeContext.lng },
          note: nodeForm.note.trim()
        }
      )
    }
    nodeDialog.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submittingNode.value = false
  }
}

async function onDeleteNode(day: number, node: GuidebookNode) {
  if (!guidebookId.value || !node.nodeId) return
  try {
    await ElMessageBox.confirm(`删除「${node.place?.name || '该节点'}」？`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await guidebookApi.deleteGuidebookNode(guidebookId.value, day, node.nodeId)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 拦截器已提示
  }
}

function moveHandler(day: number, node: GuidebookNode) {
  return (target: number) => onMoveToDay(day, node, target)
}

async function onMoveToDay(day: number, node: GuidebookNode, targetDay: number) {
  if (!guidebookId.value || !node.nodeId || targetDay === day) return
  try {
    await guidebookApi.moveGuidebookNode(guidebookId.value, node.nodeId, {
      targetDay,
      targetIndex: 0
    })
    ElMessage.success(`已移到第 ${targetDay} 天`)
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function onDragEnd(day: EditableDay, event?: { oldIndex?: number; newIndex?: number }) {
  if (!editable.value || !guidebookId.value) return
  const oldIndex = event?.oldIndex
  const newIndex = event?.newIndex
  if (oldIndex == null || newIndex == null || oldIndex === newIndex) return
  const node = day.nodes[newIndex]
  if (!node?.nodeId) {
    await load()
    return
  }
  try {
    await guidebookApi.moveGuidebookNode(guidebookId.value, node.nodeId, {
      targetDay: day.day,
      targetIndex: newIndex
    })
  } finally {
    // 以服务端顺序为准，失败时也能恢复
    await load()
  }
}

async function openVersions() {
  if (!guidebookId.value) return
  versionDrawer.value = true
  await loadVersions()
}

async function loadVersions() {
  if (!guidebookId.value) return
  versionsLoading.value = true
  try {
    versions.value = await guidebookApi.listGuidebookVersions(guidebookId.value)
  } catch {
    // 拦截器已提示
  } finally {
    versionsLoading.value = false
  }
}

async function onRestore(target: GuidebookVersionItem) {
  if (!guidebookId.value) return
  try {
    await ElMessageBox.confirm(
      `回溯到 v${target.version}？当前内容会被复制为新版本，历史记录不会丢失。`,
      '回溯版本',
      { type: 'warning' }
    )
  } catch {
    return
  }
  restoringVersion.value = target.version
  try {
    await guidebookApi.restoreGuidebookVersion(guidebookId.value, target.version)
    ElMessage.success(`已回溯到 v${target.version}`)
    await Promise.all([load(), loadVersions()])
  } catch {
    // 拦截器已提示
  } finally {
    restoringVersion.value = null
  }
}

async function onExport(format: 'markdown' | 'html') {
  if (!guidebookId.value) return
  try {
    const res = await guidebookApi.exportGuidebook(guidebookId.value, format)
    const blob = new Blob([res.content], {
      type: format === 'html' ? 'text/html;charset=utf-8' : 'text/markdown;charset=utf-8'
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = res.filename
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    // 拦截器已提示
  }
}

function openCompare() {
  compareVariants.value = ['budget', 'balanced', 'luxury']
  compareDialog.value = true
}

function openCompanion() {
  if (guidebookId.value) {
    router.push(`/trip/${guidebookId.value}`)
  }
}

async function onStartCompare() {
  if (!guidebookId.value) return
  if (compareVariants.value.length < 2) {
    ElMessage.warning('至少选择两种方案')
    return
  }
  compareStarting.value = true
  try {
    const res = await compareApi.startComparison(guidebookId.value, compareVariants.value)
    compareDialog.value = false
    router.push(`/guidebook/compare/${res.comparisonId}`)
  } catch {
    // 拦截器已提示
  } finally {
    compareStarting.value = false
  }
}

function openOfflinePack() {
  offlineDialog.value = true
  offlineTaskId.value = null
  offlineStatus.value = null
  offlineError.value = null
  stopOfflinePolling()
}

async function onCreateOfflinePack() {
  if (!guidebookId.value) return
  offlineCreating.value = true
  try {
    const task = await offlinePackApi.createOfflinePack(guidebookId.value, {
      format: offlineForm.format,
      includeMaps: offlineForm.includeMaps
    })
    offlineTaskId.value = task.taskId
    offlineStatus.value = task.status
    offlineError.value = null
    if (task.status === 'processing') {
      startOfflinePolling()
    }
  } catch {
    // 拦截器已提示
  } finally {
    offlineCreating.value = false
  }
}

function startOfflinePolling() {
  stopOfflinePolling()
  offlinePollStartedAt = Date.now()
  offlinePollTimer = window.setInterval(() => {
    if (Date.now() - offlinePollStartedAt > 60000) {
      stopOfflinePolling()
      offlineStatus.value = 'failed'
      offlineError.value = '生成超时，请稍后重试'
      return
    }
    void refreshOfflineStatus()
  }, 2000)
}

async function refreshOfflineStatus() {
  if (!offlineTaskId.value) return
  try {
    const res = await offlinePackApi.getOfflinePackStatus(offlineTaskId.value)
    offlineStatus.value = res.status
    offlineError.value = res.errorMessage
    if (res.status !== 'processing') {
      stopOfflinePolling()
    }
  } catch {
    stopOfflinePolling()
  }
}

function stopOfflinePolling() {
  if (offlinePollTimer != null) {
    window.clearInterval(offlinePollTimer)
    offlinePollTimer = null
  }
}

async function onDownloadOfflinePack() {
  if (!offlineTaskId.value) return
  offlineDownloading.value = true
  try {
    const version = detail.value?.version ?? 1
    await offlinePackApi.downloadOfflinePack(
      offlineTaskId.value,
      `旅行攻略-${guidebookId.value}-v${version}.${offlineForm.format}`
    )
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '下载失败')
  } finally {
    offlineDownloading.value = false
  }
}

async function onDelete() {
  if (!guidebookId.value) return
  try {
    await ElMessageBox.confirm('确认删除该攻略？删除后不可恢复。', '提示', { type: 'warning' })
  } catch {
    return
  }
  deleting.value = true
  try {
    await guidebookApi.deleteGuidebook(guidebookId.value)
    ElMessage.success('已删除')
    await router.push({ name: 'chat' })
  } catch {
    // 拦截器已提示
  } finally {
    deleting.value = false
  }
}

function goChat() {
  const sessionId = typeof route.query.sessionId === 'string' ? route.query.sessionId : ''
  router.push(sessionId ? { path: '/chat', query: { sessionId } } : { path: '/chat' })
}

async function loadList() {
  listLoading.value = true
  try {
    const res = await guidebookApi.listGuidebooks(activeStatus.value || undefined, 1, 50)
    guidebooks.value = res.list
  } catch {
    // 拦截器已提示
  } finally {
    listLoading.value = false
  }
}

function openGuidebook(id: string) {
  router.push({ name: 'guidebook-detail', params: { guidebookId: id } })
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function reset() {
  closeSocket()
  stopPolling()
  stopOfflinePolling()
  offlineDialog.value = false
  shareDialog.value = false
  detail.value = null
  days.value = []
  activeDay.value = '1'
  instruction.value = ''
  conflicts.value = []
  versions.value = []
  versionDrawer.value = false
  resourceDialog.value = false
  resourceResults.value = []
  linkChecks.value = {}
}

onMounted(async () => {
  if (guidebookId.value) {
    await load()
    openSocket()
  } else {
    await loadList()
  }
})

watch(guidebookId, async (id) => {
  reset()
  if (id) {
    await load()
    openSocket()
  } else {
    await loadList()
  }
})

onBeforeUnmount(reset)
</script>

<style scoped>
.guidebook-page {
  max-width: 1100px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
}
.title-group {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}
.detail-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}

.list-filters {
  margin-bottom: var(--space-4);
}
.guidebook-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.guidebook-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.guidebook-list li:hover {
  border-color: var(--brand-500);
  background: var(--brand-050);
}
.gb-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.gb-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gb-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}
.gb-side {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}
.gb-progress {
  width: 120px;
}
.gb-arrow {
  color: var(--text-3);
  font-size: 20px;
  line-height: 1;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: 72px var(--space-6);
  text-align: center;
}
.empty-ico {
  font-size: 52px;
  line-height: 1;
}
.empty-state h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}
.empty-state p {
  margin: 0;
  max-width: 420px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-2);
}

.generating {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
  padding: 40px var(--space-4);
}
.step-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
.steps {
  width: 100%;
  max-width: 760px;
}
.generating-note {
  margin: 0;
  font-size: 13px;
  color: var(--text-3);
}

.failed {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4) 0;
}

.degraded-alert,
.conflict-alert {
  margin-bottom: var(--space-4);
}

.freshness-panel {
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}
.freshness-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-2);
  font-size: 13px;
  font-weight: 600;
}
.freshness-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
.freshness-list :deep(.el-tag) {
  height: auto;
  padding: 2px 8px;
  line-height: 1.5;
  white-space: normal;
}
.conflict-list {
  margin: var(--space-2) 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.7;
}

.edit-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}
.adjust-input {
  flex: 1;
  min-width: 260px;
}

.day-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.node-item {
  display: flex;
  align-items: stretch;
  gap: var(--space-2);
}
.drag-handle {
  display: flex;
  align-items: center;
  padding: 0 4px;
  color: var(--text-3);
  cursor: grab;
  user-select: none;
}
.node-card {
  flex: 1;
  padding: var(--space-3);
  border: 1px solid var(--line-1, #e5e7eb);
  border-radius: 10px;
  background: var(--bg-2, #fff);
}
.node-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}
.node-head-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.node-time {
  font-size: 12px;
  color: var(--text-3);
}
.node-note {
  margin: var(--space-2) 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-2);
}
.node-source {
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--text-3);
}
.node-actions {
  display: flex;
  gap: var(--space-1);
  margin-top: var(--space-2);
}

.node-citations {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
.citation {
  font-size: 12px;
  color: var(--primary, #3b82f6);
  text-decoration: none;
}
.citation:hover {
  text-decoration: underline;
}

.node-resource {
  margin-top: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 1px dashed var(--border);
  border-radius: 8px;
  background: var(--surface);
}
.resource-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 13px;
}
.resource-supplier {
  font-weight: 600;
  color: var(--text-1);
}
.resource-issue {
  color: var(--warning, #b45309);
}
.book-button {
  margin-top: var(--space-2);
}

.resource-results {
  min-height: 120px;
}
.resource-option {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  margin-top: var(--space-2);
  border: 1px solid var(--border);
  border-radius: 8px;
}
.resource-option-main {
  min-width: 0;
}

.info-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: var(--space-5);
  margin-top: var(--space-5);
}
.info-grid h3 {
  margin: 0 0 var(--space-2);
  font-size: 15px;
  color: var(--text-1);
}
.info-grid h3:not(:first-child) {
  margin-top: var(--space-4);
}
.info-grid ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-2);
}
.budget-total {
  margin: var(--space-2) 0 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
}
.muted {
  margin: 0;
  font-size: 13px;
  color: var(--text-3);
}

.actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-5);
  flex-wrap: wrap;
}
.disclaimer {
  margin: var(--space-4) 0 0;
  font-size: 12px;
  color: var(--text-3);
}
.review-section {
  margin-top: var(--space-6);
}

.offline-hint {
  margin-left: var(--space-2);
  font-size: 12px;
  color: var(--text-3);
}
.offline-status {
  margin-top: var(--space-3);
}

.time-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
}
.time-sep {
  font-size: 13px;
  color: var(--text-3);
}

.version-body {
  min-height: 120px;
}
.version-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}
.version-info {
  min-width: 0;
}
.version-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.version-info .muted {
  margin-top: var(--space-1);
  line-height: 1.6;
}

@media (max-width: 860px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
