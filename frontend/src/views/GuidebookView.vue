<template>
    <el-card header="旅行攻略">
    <el-table :data="tableData" border>
        <el-table-column prop="name" label="景点名称"/>
        <el-table-column prop="price" label="票价"/>
        <el-table-column prop="address" label="地址"/>
    </el-table>
    </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const tableData = ref([
    {name:'龙门石窟', price:90, address:'洛阳'},
    {name:'老君山', price:100, address:'洛阳'}
])
</script>
