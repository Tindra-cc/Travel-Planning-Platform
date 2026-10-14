<template>
    <el-card header="智能出行助手">
    <div class="chat-box" style="height:400px;overflow-y:auto;margin-bottom:16px;border:1px solid #eee;padding:10px">
        <div>AI：你好！告诉我你的出行需求，我帮你规划旅行路线。</div>
    </div>
    <div style="display:flex;gap:8px">
        <el-input v-model="msg" placeholder="输入你的需求"/>
        <el-button type="primary">发送</el-button>
    </div>
    </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const msg = ref('')
</script>
