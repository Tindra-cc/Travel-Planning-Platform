<template>
  <div class="profile-page">
    <el-card header="基础画像">
      <el-form :model="basic" label-width="100px">
        <el-form-item label="预算档位">
          <el-select v-model="basic.budgetLevel" placeholder="请选择">
            <el-option v-for="o in budgetOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="人均预算">
          <el-input-number v-model="basic.budgetAmount" :min="0" :max="200000" :step="500" />
          <span class="field-unit">元</span>
          <span class="field-hint">{{ budgetHint }}</span>
        </el-form-item>
        <el-form-item label="行程天数">
          <el-input-number v-model="basic.tripDays" :min="1" :max="60" />
          <span class="field-unit">天</span>
        </el-form-item>
        <el-form-item label="行程节奏">
          <el-select v-model="basic.pace" placeholder="请选择">
            <el-option v-for="o in paceOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="兴趣标签">
          <el-select v-model="basic.interestTags" multiple placeholder="至少选择一项">
            <el-option v-for="o in interestOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="饮食禁忌">
          <el-input v-model="basic.dietaryRestrictions" placeholder="如：无 / 素食" />
        </el-form-item>
        <el-form-item label="出行人结构">
          <div class="party-row">
            <span class="party-label">成人</span><el-input-number v-model="basic.partyStructure.adults" :min="0" />
            <span class="party-label">儿童</span><el-input-number v-model="basic.partyStructure.children" :min="0" />
            <span class="party-label">老人</span><el-input-number v-model="basic.partyStructure.elderly" :min="0" />
            <span class="party-label">宠物</span><el-input-number v-model="basic.partyStructure.pets" :min="0" />
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card header="深度画像（L2）">
      <el-form :model="advanced" label-width="100px">
        <el-form-item label="特殊偏好">
          <el-select v-model="advanced.specialPreferences" multiple placeholder="可多选">
            <el-option
              v-for="o in specialOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="接受红眼航班">
          <el-switch v-model="advanced.acceptRedEye" />
        </el-form-item>
        <el-form-item label="交通偏好">
          <el-select v-model="advanced.transportPreference" placeholder="请选择">
            <el-option v-for="o in transportOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="住宿偏好">
          <el-select v-model="advanced.accommodationPreference" placeholder="请选择">
            <el-option
              v-for="o in accommodationOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="actions">
        <el-button type="primary" :loading="saving" @click="onSave">保存画像</el-button>
        <el-button @click="onCheck">完整性检查</el-button>
        <span v-if="version !== null" class="version">版本 {{ version }}</span>
      </div>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>出行人档案</span>
          <el-button type="primary" size="small" @click="openTravelerDialog()">新增</el-button>
        </div>
      </template>
      <el-table :data="travelers" empty-text="暂无出行人档案">
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="idType" label="证件类型" />
        <el-table-column prop="idNumber" label="证件号" />
        <el-table-column prop="birthDate" label="出生日期" />
        <el-table-column prop="specialNeeds" label="特殊需求" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" @click="openTravelerDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDeleteTraveler(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="travelerForm.travelerId ? '编辑出行人' : '新增出行人'">
      <el-form :model="travelerForm" label-width="90px">
        <el-form-item label="姓名"><el-input v-model="travelerForm.name" /></el-form-item>
        <el-form-item label="证件类型"><el-input v-model="travelerForm.idType" /></el-form-item>
        <el-form-item label="证件号"><el-input v-model="travelerForm.idNumber" /></el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="travelerForm.birthDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="特殊需求"><el-input v-model="travelerForm.specialNeeds" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSaveTraveler">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as profileApi from '@/api/profile'
import type { TravelerResponse } from '@/types/api'

const budgetOptions = [
  { value: 'budget', label: '穷游' },
  { value: 'economy', label: '经济' },
  { value: 'balanced', label: '均衡' },
  { value: 'comfort', label: '舒适' },
  { value: 'luxury', label: '奢华' }
]
const paceOptions = [
  { value: 'tight', label: '紧凑' },
  { value: 'balanced', label: '适中' },
  { value: 'relaxed', label: '松弛' }
]
const interestOptions = [
  { value: 'nature', label: '自然风光' },
  { value: 'food', label: '美食' },
  { value: 'culture', label: '人文' },
  { value: 'history', label: '历史' },
  { value: 'shopping', label: '购物' },
  { value: 'nightlife', label: '夜生活' },
  { value: 'photography', label: '摄影' },
  { value: 'adventure', label: '探险' }
]
const specialOptions = [
  { value: 'photography', label: '摄影' },
  { value: 'hotspring', label: '温泉' },
  { value: 'cruise', label: '邮轮' },
  { value: 'hiking', label: '徒步' }
]
const transportOptions = [
  { value: 'rail', label: '铁路' },
  { value: 'flight', label: '飞机' },
  { value: 'self_drive', label: '自驾' },
  { value: 'public', label: '公共交通' }
]
const accommodationOptions = [
  { value: 'hostel', label: '青旅' },
  { value: 'standard', label: '标准酒店' },
  { value: 'boutique', label: '精品酒店' },
  { value: 'resort', label: '度假村' }
]

// 档位 -> 人均预算参考区间，仅作提示，不自动改写用户已填金额
const BUDGET_AMOUNT_HINT: Record<string, string> = {
  budget: '穷游档参考 ¥1,000–2,500/人',
  economy: '经济档参考 ¥2,500–4,000/人',
  balanced: '均衡档参考 ¥4,000–6,500/人',
  comfort: '舒适档参考 ¥6,500–12,000/人',
  luxury: '奢华档参考 ¥12,000+/人'
}

const basic = ref({
  budgetLevel: '',
  budgetAmount: undefined as number | undefined,
  tripDays: undefined as number | undefined,
  pace: '',
  interestTags: [] as string[],
  dietaryRestrictions: '',
  partyStructure: { adults: 0, children: 0, elderly: 0, pets: 0 }
})

const budgetHint = computed(
  () => BUDGET_AMOUNT_HINT[basic.value.budgetLevel] ?? '选择预算档位后给出参考区间'
)
const advanced = ref({
  specialPreferences: [] as string[],
  acceptRedEye: false,
  transportPreference: '',
  accommodationPreference: ''
})
const version = ref<number | null>(null)
const saving = ref(false)

const travelers = ref<TravelerResponse[]>([])
const dialogVisible = ref(false)
const emptyTraveler = (): Partial<TravelerResponse> => ({
  travelerId: undefined,
  name: '',
  idType: 'id_card',
  idNumber: '',
  birthDate: '',
  specialNeeds: ''
})
const travelerForm = ref<Partial<TravelerResponse>>(emptyTraveler())

async function loadProfile() {
  const res = await profileApi.getProfile()
  basic.value = {
    budgetLevel: res.basic?.budgetLevel ?? '',
    budgetAmount: res.basic?.budgetAmount ?? undefined,
    tripDays: res.basic?.tripDays ?? undefined,
    pace: res.basic?.pace ?? '',
    interestTags: res.basic?.interestTags ?? [],
    dietaryRestrictions: res.basic?.dietaryRestrictions ?? '',
    partyStructure: {
      adults: res.basic?.partyStructure?.adults ?? 0,
      children: res.basic?.partyStructure?.children ?? 0,
      elderly: res.basic?.partyStructure?.elderly ?? 0,
      pets: res.basic?.partyStructure?.pets ?? 0
    }
  }
  advanced.value = {
    specialPreferences: res.advanced?.specialPreferences ?? [],
    acceptRedEye: res.advanced?.acceptRedEye ?? false,
    transportPreference: res.advanced?.transportPreference ?? '',
    accommodationPreference: res.advanced?.accommodationPreference ?? ''
  }
  version.value = res.version
}

async function loadTravelers() {
  travelers.value = await profileApi.listTravelers()
}

async function onSave() {
  saving.value = true
  try {
    await profileApi.updateProfile({ basic: basic.value, advanced: advanced.value })
    ElMessage.success('画像已保存')
    await loadProfile()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onCheck() {
  const res = await profileApi.checkProfile()
  if (res.completed) {
    ElMessage.success('画像已完整，可以生成攻略')
  } else {
    ElMessage.warning(`仍缺失字段：${res.missingFields.join('、')}`)
  }
}

function openTravelerDialog(row?: TravelerResponse) {
  travelerForm.value = row ? { ...row } : emptyTraveler()
  dialogVisible.value = true
}

async function onSaveTraveler() {
  const form = travelerForm.value
  if (!form.name) {
    ElMessage.warning('请填写姓名')
    return
  }
  try {
    if (form.travelerId) {
      await profileApi.updateTraveler(form.travelerId, form as TravelerResponse)
    } else {
      await profileApi.addTraveler(form as TravelerResponse)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await loadTravelers()
  } catch {
    // 拦截器已提示
  }
}

async function onDeleteTraveler(row: TravelerResponse) {
  await profileApi.deleteTraveler(row.travelerId)
  ElMessage.success('已删除')
  await loadTravelers()
}

onMounted(async () => {
  await loadProfile()
  await loadTravelers()
})
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.field-unit {
  color: var(--text-2);
  font-size: 13px;
  margin-left: 8px;
}
.field-hint {
  color: var(--text-3);
  font-size: 12px;
  margin-left: 8px;
}

.party-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}
.party-label {
  color: var(--text-2);
  font-size: 13px;
}

.actions {
  margin-top: var(--space-4);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.version {
  color: var(--text-3);
  font-size: 13px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
