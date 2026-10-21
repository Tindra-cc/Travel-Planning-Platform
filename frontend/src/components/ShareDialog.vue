<template>
  <el-dialog v-model="visible" title="分享攻略" width="480px" @closed="reset">
    <el-form label-width="72px">
      <el-form-item label="渠道">
        <el-radio-group v-model="channel" :disabled="!!result">
          <el-radio value="link">链接</el-radio>
          <el-radio value="qrcode">二维码</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template v-if="result">
      <el-input :model-value="result.shareUrl" readonly>
        <template #append>
          <el-button @click="copyLink">复制</el-button>
        </template>
      </el-input>
      <div v-if="result.qrcodeUrl" class="qrcode">
        <img :src="result.qrcodeUrl" alt="分享二维码" />
      </div>
      <p v-else class="hint">二维码未配置，可复制链接分享。</p>
      <p class="hint">分享内容为创建时的只读快照，之后编辑原攻略不会改变它。</p>
    </template>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="result" type="danger" plain :loading="revoking" @click="onRevoke">
        撤回分享
      </el-button>
      <el-button v-else type="primary" :loading="creating" @click="onCreate">
        生成分享
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createShare, revokeShare } from '@/api/share'
import type { ShareCreateResponse } from '@/types/api'

const props = defineProps<{ guidebookId: string }>()
const visible = defineModel<boolean>({ required: true })

const channel = ref<'link' | 'qrcode'>('link')
const creating = ref(false)
const revoking = ref(false)
const result = ref<ShareCreateResponse | null>(null)

async function onCreate() {
  creating.value = true
  try {
    result.value = await createShare(props.guidebookId, channel.value)
  } catch {
    // 拦截器已提示；保留当前状态以便重试
  } finally {
    creating.value = false
  }
}

async function copyLink() {
  if (!result.value) return
  try {
    await navigator.clipboard.writeText(result.value.shareUrl)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择链接复制')
  }
}

async function onRevoke() {
  if (!result.value) return
  revoking.value = true
  try {
    await revokeShare(props.guidebookId, result.value.shareId)
    ElMessage.success('分享已撤回')
    result.value = null
    visible.value = false
  } catch {
    // 拦截器已提示
  } finally {
    revoking.value = false
  }
}

function reset() {
  result.value = null
  channel.value = 'link'
}
</script>

<style scoped>
.qrcode {
  margin-top: 16px;
  text-align: center;
}
.qrcode img {
  width: 200px;
  height: 200px;
}
.hint {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-3);
}
</style>
