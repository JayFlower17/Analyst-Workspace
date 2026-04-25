<template>
  <div class="chat-page">
    <el-row :gutter="16" class="full-height">
      <el-col :span="6" class="full-height">
        <el-card class="panel full-height">
          <template #header>
            <div class="header-row">
              <span>Chat Sessions</span>
              <el-button type="primary" size="small" @click="createSession">新建</el-button>
            </div>
          </template>
          <div class="session-list">
            <div
              v-for="session in sessions"
              :key="session.id"
              class="session-item"
              :class="{ active: session.id === currentSessionId }"
              @click="switchSession(session.id)"
            >
              <div class="title">{{ session.title || `Session #${session.id}` }}</div>
              <div class="time">{{ formatDate(session.updatedAt || session.createdAt) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="18" class="full-height">
        <el-card class="panel full-height">
          <template #header>
            <div class="header-row">
              <span>Chat</span>
              <div class="actions">
                <el-select
                  v-model="selectedDatasetId"
                  clearable
                  placeholder="可选：指定分析数据表"
                  style="width: 260px"
                >
                  <el-option
                    v-for="ds in sessionDatasets"
                    :key="ds.id"
                    :label="`${ds.name} (${ds.tableName})`"
                    :value="ds.id"
                  />
                </el-select>

                <el-upload
                  :show-file-list="false"
                  :http-request="handleUpload"
                  :before-upload="beforeUpload"
                  accept=".csv,.xlsx,.xls"
                >
                  <el-button :loading="uploading">上传数据</el-button>
                </el-upload>

                <el-button type="primary" @click="openPromoteDialog">升级为 Workplace</el-button>
              </div>
            </div>
          </template>

          <div class="chat-body">
            <div ref="messageContainerRef" class="messages">
              <div
                v-for="msg in messages"
                :key="msg.id"
                class="message"
                :class="(msg.role || '').toLowerCase()"
              >
                <div class="bubble">
                  <div class="meta">{{ msg.role }}</div>
                  <div class="content">{{ msg.content }}</div>
                </div>
              </div>
            </div>

            <div v-if="lastResult && lastResult.success" class="result-box">
              <div class="result-title">最近一次分析结果</div>
              <div class="result-summary">{{ lastResult.summary || '分析已完成' }}</div>

              <el-table
                v-if="lastResult.data && lastResult.data.length > 0"
                :data="lastResult.data.slice(0, 20)"
                border
                size="small"
                max-height="220"
              >
                <el-table-column
                  v-for="col in resultColumns"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="120"
                  show-overflow-tooltip
                />
              </el-table>

              <pre v-if="lastResult.generatedCodeOrSql" class="code"><code>{{ lastResult.generatedCodeOrSql }}</code></pre>
            </div>
          </div>

          <div class="composer">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="输入问题：可直接聊天，也可以结合已上传数据分析。"
              @keydown.ctrl.enter.prevent="sendMessage"
            />
            <div class="composer-actions">
              <span class="hint">Ctrl + Enter 发送</span>
              <el-button type="primary" :loading="analyzing" @click="sendMessage">发送</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="promoteVisible" title="升级为 Workplace" width="520px">
      <el-form label-position="top">
        <el-form-item label="Workplace 名称">
          <el-input v-model="workspaceName" />
        </el-form-item>
        <el-form-item label="描述（可选）">
          <el-input v-model="workspaceDescription" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promoteVisible = false">取消</el-button>
        <el-button type="primary" :loading="promoting" @click="promoteToWorkspace">确认升级</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { chatApi } from '@/api'

const router = useRouter()

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const sessionDatasets = ref([])
const selectedDatasetId = ref(null)
const inputText = ref('')
const analyzing = ref(false)
const uploading = ref(false)
const lastResult = ref(null)
const messageContainerRef = ref(null)

const promoteVisible = ref(false)
const promoting = ref(false)
const workspaceName = ref('')
const workspaceDescription = ref('')

const resultColumns = computed(() => {
  if (!lastResult.value?.data?.length) return []
  return Object.keys(lastResult.value.data[0])
})

const loadSessions = async () => {
  try {
    const res = await chatApi.getSessions()
    if (res.success) {
      sessions.value = res.data || []
      if (!sessions.value.length) {
        await createSession()
        return
      }
      if (!currentSessionId.value || !sessions.value.find(s => s.id === currentSessionId.value)) {
        currentSessionId.value = sessions.value[0].id
      }
      await loadSessionData()
    }
  } catch (error) {
    ElMessage.error('加载聊天会话失败')
    console.error(error)
  }
}

const createSession = async () => {
  try {
    const res = await chatApi.createSession()
    if (res.success) {
      currentSessionId.value = res.data.id
      await loadSessions()
    } else {
      ElMessage.error(res.message || '创建会话失败')
    }
  } catch (error) {
    ElMessage.error('创建会话失败')
  }
}

const switchSession = async (sessionId) => {
  if (!sessionId) return
  currentSessionId.value = sessionId
  lastResult.value = null
  selectedDatasetId.value = null
  await loadSessionData()
}

const loadSessionData = async () => {
  if (!currentSessionId.value) return
  await Promise.all([loadMessages(), loadSessionDatasets()])
  await scrollToBottom()
}

const loadMessages = async () => {
  if (!currentSessionId.value) return
  const res = await chatApi.getMessages(currentSessionId.value)
  if (res.success) {
    messages.value = res.data || []
  }
}

const loadSessionDatasets = async () => {
  if (!currentSessionId.value) return
  const res = await chatApi.getDatasets(currentSessionId.value)
  if (res.success) {
    sessionDatasets.value = res.data || []
    if (selectedDatasetId.value && !sessionDatasets.value.find(d => d.id === selectedDatasetId.value)) {
      selectedDatasetId.value = null
    }
  }
}

const beforeUpload = (file) => {
  const okType = ['.csv', '.xlsx', '.xls'].some(ext => file.name.toLowerCase().endsWith(ext))
  if (!okType) {
    ElMessage.error('只支持 CSV/Excel 文件')
    return false
  }
  const isLt100M = file.size / 1024 / 1024 < 100
  if (!isLt100M) {
    ElMessage.error('文件大小不能超过100MB')
    return false
  }
  return true
}

const handleUpload = async ({ file }) => {
  if (!currentSessionId.value) return
  uploading.value = true
  try {
    const res = await chatApi.upload(currentSessionId.value, file, file.name)
    if (res.success) {
      ElMessage.success('上传成功')
      await loadSessionData()
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const sendMessage = async () => {
  const query = inputText.value.trim()
  if (!query) return
  if (!currentSessionId.value) {
    ElMessage.warning('请先创建会话')
    return
  }
  analyzing.value = true
  try {
    const res = await chatApi.analyze(currentSessionId.value, query, selectedDatasetId.value)
    if (res.success) {
      inputText.value = ''
      lastResult.value = res
      await loadMessages()
      await loadSessions()
      await scrollToBottom()
    } else {
      ElMessage.error(res.message || '分析失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '分析失败')
  } finally {
    analyzing.value = false
  }
}

const openPromoteDialog = () => {
  if (!currentSessionId.value) return
  if (!sessionDatasets.value.length) {
    ElMessage.warning('当前会话没有可升级的数据集，请先上传数据')
    return
  }
  const active = sessions.value.find(s => s.id === currentSessionId.value)
  workspaceName.value = active?.title ? `${active.title}-Workplace` : `Workplace-${Date.now()}`
  workspaceDescription.value = ''
  promoteVisible.value = true
}

const promoteToWorkspace = async () => {
  if (!workspaceName.value.trim()) {
    ElMessage.warning('请输入 Workplace 名称')
    return
  }
  promoting.value = true
  try {
    const res = await chatApi.promote(currentSessionId.value, {
      workspaceName: workspaceName.value.trim(),
      description: workspaceDescription.value
    })
    if (res.success) {
      ElMessage.success('升级成功，正在跳转到 Workplace 分析页')
      promoteVisible.value = false
      router.push({
        path: '/analysis',
        query: { groupId: res.data.id }
      })
    } else {
      ElMessage.error(res.message || '升级失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '升级失败')
  } finally {
    promoting.value = false
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messageContainerRef.value) {
    messageContainerRef.value.scrollTop = messageContainerRef.value.scrollHeight
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(async () => {
  await loadSessions()
})
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 120px);
}

.full-height {
  height: 100%;
}

.panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.panel .el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
}

.session-item {
  border: 1px solid #eceef2;
  border-radius: 10px;
  padding: 10px;
  cursor: pointer;
  background: #fff;
}

.session-item:hover {
  border-color: #a9c6ff;
}

.session-item.active {
  border-color: #409eff;
  background: #f0f7ff;
}

.session-item .title {
  font-size: 14px;
  font-weight: 600;
}

.session-item .time {
  color: #909399;
  margin-top: 4px;
  font-size: 12px;
}

.chat-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}

.message {
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant,
.message.system {
  justify-content: flex-start;
}

.bubble {
  max-width: 80%;
  border-radius: 12px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  background: #fff;
}

.message.user .bubble {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}

.meta {
  font-size: 12px;
  opacity: 0.75;
}

.content {
  white-space: pre-wrap;
  line-height: 1.5;
  margin-top: 4px;
}

.result-box {
  border-top: 1px dashed #dcdfe6;
  padding-top: 10px;
}

.result-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.result-summary {
  margin-bottom: 8px;
  color: #606266;
  line-height: 1.6;
}

.code {
  margin-top: 8px;
  background: #1f2430;
  color: #dfe6f3;
  border-radius: 8px;
  padding: 10px;
  overflow-x: auto;
  font-size: 12px;
}

.composer {
  border-top: 1px solid #ebeef5;
  padding-top: 10px;
}

.composer-actions {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hint {
  color: #909399;
  font-size: 12px;
}
</style>

