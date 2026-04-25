<template>
  <div class="datasets-container">
    <div class="page-header">
      <h2>数据集管理</h2>
      <div class="toolbar">
        <el-select
          v-model="selectedGroupId"
          clearable
          placeholder="全部工作区"
          style="width: 240px"
          @change="loadDatasets"
        >
          <el-option
            v-for="group in groups"
            :key="group.id"
            :label="group.name"
            :value="group.id"
          />
        </el-select>

        <el-button @click="openCreateGroupDialog">
          <el-icon><FolderAdd /></el-icon>
          新建工作区
        </el-button>

        <el-button :disabled="!selectedGroupId" @click="openGroupDescDialog">
          <el-icon><EditPen /></el-icon>
          编辑工作区描述
        </el-button>
      </div>
    </div>

    <el-upload
      drag
      :show-file-list="false"
      :before-upload="beforeUpload"
      :http-request="handleUpload"
      accept=".csv,.xlsx,.xls"
      class="upload-dragger"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        将数据表拖拽到这里，或 <em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          建议先选择工作区再上传，便于多表联合分析。支持 CSV / Excel，单文件 ≤ 100MB。
        </div>
      </template>
    </el-upload>

    <el-table
      v-loading="loading"
      :data="datasets"
      stripe
      style="width: 100%"
    >
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column label="工作区" min-width="140">
        <template #default="{ row }">
          {{ getGroupName(row.groupId) }}
        </template>
      </el-table-column>
      <el-table-column prop="originalFileName" label="原始文件" min-width="150" />
      <el-table-column prop="rowCount" label="行数" width="100">
        <template #default="{ row }">
          {{ formatNumber(row.rowCount) }}
        </template>
      </el-table-column>
      <el-table-column prop="columnCount" label="列数" width="80" />
      <el-table-column label="表描述" min-width="180">
        <template #default="{ row }">
          <span class="desc-brief">{{ briefDescription(row.descriptionMd) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewData(row)">
            <el-icon><View /></el-icon>
            查看
          </el-button>
          <el-button size="small" @click="openDatasetDescDialog(row)">
            <el-icon><EditPen /></el-icon>
            描述
          </el-button>
          <el-button size="small" type="primary" @click="goAnalysis(row)">
            <el-icon><TrendCharts /></el-icon>
            分析
          </el-button>
          <el-button size="small" type="danger" @click="deleteDataset(row)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createGroupVisible" title="新建工作区" width="560px">
      <el-form label-position="top">
        <el-form-item label="工作区名称">
          <el-input v-model="newGroupName" placeholder="例如：电商经营分析-2026Q2" />
        </el-form-item>
        <el-form-item label="工作区描述（Markdown）">
          <el-input
            v-model="newGroupDescription"
            type="textarea"
            :rows="8"
            placeholder="描述本工作区业务目标、核心口径、分析边界。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createGroupVisible = false">取消</el-button>
        <el-button type="primary" @click="createGroup">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupDescVisible" title="编辑工作区描述（Markdown）" width="700px">
      <el-input
        v-model="editingGroupDescription"
        type="textarea"
        :rows="14"
        placeholder="例如：GMV口径、退货处理规则、渠道定义等。"
      />
      <template #footer>
        <el-button @click="groupDescVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGroupDescription">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="datasetDescVisible" title="编辑数据表描述（Markdown）" width="700px">
      <div class="dialog-subtitle">当前数据表：{{ editingDatasetName }}</div>
      <el-input
        v-model="editingDatasetDescription"
        type="textarea"
        :rows="14"
        placeholder="建议写清：表用途、关键字段含义、统计口径、时间字段说明。"
      />
      <template #footer>
        <el-button @click="datasetDescVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDatasetDescription">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="'数据预览 - ' + previewDatasetName" width="90%" top="5vh">
      <el-table
        v-loading="previewLoading"
        :data="previewData"
        max-height="500"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column
          v-for="col in previewColumns"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <div style="margin-top: 12px; color: #909399; font-size: 13px;">
        共显示 {{ previewData.length }} 行数据
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { datasetApi, analysisApi, groupApi } from '@/api'

const router = useRouter()
const loading = ref(false)
const datasets = ref([])
const groups = ref([])
const selectedGroupId = ref(null)

const createGroupVisible = ref(false)
const newGroupName = ref('')
const newGroupDescription = ref('')

const groupDescVisible = ref(false)
const editingGroupDescription = ref('')

const datasetDescVisible = ref(false)
const editingDatasetId = ref(null)
const editingDatasetName = ref('')
const editingDatasetDescription = ref('')

const previewVisible = ref(false)
const previewData = ref([])
const previewColumns = ref([])
const previewLoading = ref(false)
const previewDatasetName = ref('')

const loadGroups = async () => {
  try {
    const res = await groupApi.getAll()
    if (res.success) {
      groups.value = res.data || []
    }
  } catch (error) {
    console.error('加载工作区失败', error)
  }
}

const loadDatasets = async (retryCount = 0) => {
  loading.value = true
  const maxRetries = 5

  try {
    const res = await datasetApi.getAll(selectedGroupId.value)
    if (res.success) {
      datasets.value = res.data || []
    }
  } catch (error) {
    if (retryCount < maxRetries) {
      const delay = Math.min(1000 * Math.pow(2, retryCount), 5000)
      setTimeout(() => loadDatasets(retryCount + 1), delay)
      return
    }
    ElMessage.error('加载数据集失败，多次重试无响应，请确认后端已启动')
  } finally {
    loading.value = false
  }
}

const beforeUpload = (file) => {
  const isValidType = ['text/csv', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'application/vnd.ms-excel'].includes(file.type)
    || file.name.endsWith('.csv') || file.name.endsWith('.xlsx') || file.name.endsWith('.xls')

  if (!isValidType) {
    ElMessage.error('只支持 CSV 和 Excel 格式文件')
    return false
  }

  const isLt100M = file.size / 1024 / 1024 < 100
  if (!isLt100M) {
    ElMessage.error('文件大小不能超过 100MB')
    return false
  }
  return true
}

const handleUpload = async ({ file }) => {
  if (selectedGroupId.value == null) {
    ElMessage.warning('建议先选择工作区再上传，便于后续多表联合分析')
  }

  const loadingMsg = ElMessage.info({
    message: '正在上传...',
    duration: 0
  })

  try {
    const res = await datasetApi.upload(file, file.name, selectedGroupId.value)
    loadingMsg.close()
    if (res.success) {
      ElMessage.success('上传成功')
      loadDatasets()
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch (error) {
    loadingMsg.close()
    const backendMsg = error?.response?.data?.message
    ElMessage.error(backendMsg || '上传失败')
  }
}

const openCreateGroupDialog = () => {
  createGroupVisible.value = true
  newGroupName.value = ''
  newGroupDescription.value = ''
}

const createGroup = async () => {
  if (!newGroupName.value.trim()) {
    ElMessage.warning('请输入工作区名称')
    return
  }
  try {
    const res = await groupApi.create({
      name: newGroupName.value.trim(),
      description: newGroupDescription.value
    })
    if (res.success) {
      ElMessage.success('工作区创建成功')
      createGroupVisible.value = false
      await loadGroups()
      selectedGroupId.value = res.data.id
      loadDatasets()
    } else {
      ElMessage.error(res.message || '创建工作区失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '创建工作区失败')
  }
}

const openGroupDescDialog = async () => {
  if (!selectedGroupId.value) {
    ElMessage.warning('请先选择工作区')
    return
  }
  try {
    const res = await groupApi.getById(selectedGroupId.value)
    if (res.success) {
      editingGroupDescription.value = res.data?.description || ''
      groupDescVisible.value = true
    }
  } catch (error) {
    ElMessage.error('读取工作区描述失败')
  }
}

const saveGroupDescription = async () => {
  try {
    const res = await groupApi.updateDescription(selectedGroupId.value, editingGroupDescription.value)
    if (res.success) {
      ElMessage.success('工作区描述已保存')
      groupDescVisible.value = false
      loadGroups()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  }
}

const openDatasetDescDialog = (row) => {
  editingDatasetId.value = row.id
  editingDatasetName.value = row.name
  editingDatasetDescription.value = row.descriptionMd || ''
  datasetDescVisible.value = true
}

const saveDatasetDescription = async () => {
  if (!editingDatasetId.value) return
  try {
    const res = await datasetApi.updateDescription(editingDatasetId.value, editingDatasetDescription.value)
    if (res.success) {
      ElMessage.success('数据表描述已保存')
      datasetDescVisible.value = false
      loadDatasets()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  }
}

const viewData = async (row) => {
  previewLoading.value = true
  previewDatasetName.value = row.name || row.originalFileName
  previewVisible.value = true
  previewData.value = []
  previewColumns.value = []
  try {
    const res = await analysisApi.preview(row.id, 100)
    if (res.success) {
      previewData.value = res.data || []
      if (previewData.value.length > 0) {
        previewColumns.value = Object.keys(previewData.value[0])
      }
    } else {
      ElMessage.error(res.message || '加载数据失败')
    }
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    previewLoading.value = false
  }
}

const goAnalysis = (row) => {
  const query = {}
  if (row.groupId) {
    query.groupId = row.groupId
  } else {
    query.datasetId = row.id
  }
  router.push({
    path: '/analysis',
    query
  })
}

const deleteDataset = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该数据集吗？', '提示', {
      type: 'warning'
    })

    const res = await datasetApi.delete(row.id)
    if (res.success) {
      ElMessage.success('删除成功')
      loadDatasets()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const formatNumber = (num) => {
  return num?.toLocaleString() || '0'
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getGroupName = (groupId) => {
  if (!groupId) return '未归组'
  const group = groups.value.find(g => g.id === groupId)
  return group ? group.name : `#${groupId}`
}

const briefDescription = (descriptionMd) => {
  if (!descriptionMd || !descriptionMd.trim()) return '未填写'
  return descriptionMd.replace(/\s+/g, ' ').slice(0, 30) + (descriptionMd.length > 30 ? '...' : '')
}

onMounted(() => {
  loadGroups()
  loadDatasets()
})
</script>

<style scoped>
.datasets-container {
  background: white;
  border-radius: 12px;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  color: #303133;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.upload-dragger {
  margin-bottom: 18px;
}

.desc-brief {
  color: #606266;
}

.dialog-subtitle {
  margin-bottom: 10px;
  color: #606266;
}
</style>
