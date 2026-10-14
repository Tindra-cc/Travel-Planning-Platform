<template>
    <div class="container">
    <el-card header="用户信息">
        <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="姓名">
            <el-input v-model="form.name"></el-input>
        </el-form-item>
        <el-form-item label="年龄">
            <el-input v-model="form.age"></el-input>
        </el-form-item>
        <el-form-item label="出行偏好">
            <el-checkbox-group v-model="form.hobby">
            <el-checkbox label="自然风光"/>
            <el-checkbox label="人文古迹"/>
            <el-checkbox label="美食探店"/>
            </el-checkbox-group>
        </el-form-item>
        <el-form-item>
            <el-button type="primary" @click="onSubmit">保存</el-button>
        </el-form-item>
        </el-form>
    </el-card>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const formRef = ref()
const form = ref({
    name: '',
    age: '',
    hobby: []
})

const onSubmit = () => {
    console.log('表单数据', form.value)
}
</script>
