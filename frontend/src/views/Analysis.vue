<template>
  <div class="workplace-page">
    <section class="bento-grid">
      <article class="bento-card control-card">
        <div class="card-head">
          <h3>Workspace Scope</h3>
          <span class="mono subtle">mode/workplace</span>
        </div>
        <div class="control-row">
          <el-select
            v-model="selectedGroupId"
            placeholder="Select workplace"
            class="dark-select"
            @change="onGroupChange"
          >
            <el-option
              v-for="group in groups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
          <el-select
            v-model="focusDatasetIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="Focus tables (optional)"
            class="dark-select"
          >
            <el-option
              v-for="ds in workspaceDatasets"
              :key="ds.id"
              :label="`${ds.name} (${ds.tableName})`"
              :value="ds.id"
            />
          </el-select>
        </div>
        <div class="sub-hint">
          <el-icon><Connection /></el-icon>
          <span>{{ workspaceDatasets.length }} tables connected · {{ workspaceRelations.length }} declared relations</span>
        </div>
      </article>

      <article class="bento-card metric-card" v-for="metric in metrics" :key="metric.title">
        <div class="metric-top">
          <span class="metric-title">{{ metric.title }}</span>
          <span class="metric-delta" :class="{ up: metric.delta >= 0, down: metric.delta < 0 }">
            {{ metric.delta >= 0 ? '+' : '' }}{{ metric.delta.toFixed(1) }}%
          </span>
        </div>
        <div class="metric-value">{{ metric.value }}</div>
        <svg class="spark" viewBox="0 0 120 36" preserveAspectRatio="none">
          <path :d="metric.sparkPath" fill="none" stroke="url(#spark-gradient)" stroke-width="2.4" />
          <defs>
            <linearGradient id="spark-gradient" x1="0%" x2="100%" y1="0%" y2="0%">
              <stop offset="0%" stop-color="#4f46e5" />
              <stop offset="100%" stop-color="#22d3ee" />
            </linearGradient>
          </defs>
        </svg>
      </article>

      <article class="bento-card trend-card">
        <div class="card-head">
          <h3>Sales Trend</h3>
          <div class="head-meta">
            <span class="subtle">interactive time-series</span>
            <span v-if="executionTime" class="mono subtle">{{ executionTime }}ms</span>
          </div>
        </div>

        <div v-if="analyzing" class="chart-skeleton">
          <div class="bone shimmer" />
          <div class="bone shimmer short" />
          <div class="bone shimmer" />
        </div>
        <div v-else ref="chartRef" class="trend-chart" />
      </article>

      <article class="bento-card products-card">
        <div class="card-head">
          <h3>Top Products</h3>
          <span class="subtle">ranked by metric</span>
        </div>
        <div class="product-list">
          <div class="product-row" v-for="(row, idx) in topProducts" :key="idx">
            <span class="rank">#{{ idx + 1 }}</span>
            <span class="name">{{ row.name }}</span>
            <span class="value mono">{{ row.value }}</span>
          </div>
          <div v-if="!topProducts.length" class="empty-inline">No ranked data yet</div>
        </div>
      </article>

      <article class="bento-card geo-card">
        <div class="card-head">
          <h3>Geographic Heatmap</h3>
          <span class="subtle">preview</span>
        </div>
        <div class="geo-preview">
          <div class="heat-block" v-for="(g, idx) in geoPreview" :key="idx" :style="{ opacity: g.opacity }">
            <span>{{ g.label }}</span>
            <strong class="mono">{{ g.value }}</strong>
          </div>
          <div v-if="!geoPreview.length" class="empty-inline">Waiting for geo-like fields</div>
        </div>
      </article>
    </section>

    <transition name="drill">
      <section v-if="drillDownVisible" class="drill-panel">
        <div class="drill-head">
          <h4>Drill-down: {{ drillDownTitle }}</h4>
          <el-button text @click="drillDownVisible = false">Close</el-button>
        </div>
        <el-table :data="drillDownRows" size="small" border max-height="300">
          <el-table-column
            v-for="col in drillColumns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
      </section>
    </transition>

    <section class="ai-command-center" :class="{ collapsed: !aiOpen }">
      <button class="ai-toggle" @click="aiOpen = !aiOpen">
        <el-icon><ChatDotRound /></el-icon>
        <span>{{ aiOpen ? 'Hide' : 'AI' }}</span>
      </button>

      <template v-if="aiOpen">
        <header class="ai-header">
          <div class="agent">
            <div class="avatar-wrap" :class="{ thinking: analyzing }">
              <div class="avatar">AI</div>
            </div>
            <div>
              <div class="agent-name">AI Command Center</div>
              <div class="agent-status">{{ analyzing ? 'Thinking with context...' : 'Ready' }}</div>
            </div>
          </div>
        </header>

        <div class="ai-stream">
          <div v-for="(msg, idx) in conversation" :key="idx" class="msg" :class="msg.role">
            <div class="msg-content">{{ msg.content }}</div>
          </div>
          <div v-if="assistantTyping" class="msg assistant">
            <div class="msg-content">{{ assistantStreamText }}<span class="cursor">▋</span></div>
          </div>

          <div v-if="analyzing" class="thinking-skeleton">
            <div class="line shimmer" />
            <div class="line shimmer short" />
            <div class="line shimmer" />
          </div>
        </div>

        <footer class="ai-input">
          <el-input
            v-model="queryText"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="Ask for analysis, e.g. monthly trend and top products"
            @keydown.ctrl.enter.prevent="executeAnalysis"
          />
          <div class="send-row">
            <span class="subtle">Ctrl + Enter</span>
            <el-button type="primary" :loading="analyzing" :disabled="!selectedGroupId || !queryText.trim()" @click="executeAnalysis">
              Run
            </el-button>
          </div>
        </footer>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { analysisApi, datasetApi, groupApi } from '@/api'

const route = useRoute()

const groups = ref([])
const selectedGroupId = ref(null)
const workspaceDatasets = ref([])
const workspaceRelations = ref([])
const focusDatasetIds = ref([])

const queryText = ref('')
const result = ref(null)
const analyzing = ref(false)
const executionTime = ref(null)

const chartRef = ref(null)
const chartInstance = ref(null)
const trendMeta = ref({ xCol: null, yCol: null, categories: [], values: [] })

const aiOpen = ref(true)
const conversation = ref([])
const assistantTyping = ref(false)
const assistantStreamText = ref('')
let typewriterTimer = null

const drillDownVisible = ref(false)
const drillDownTitle = ref('')
const drillDownRows = ref([])
const drillColumns = computed(() => {
  if (!drillDownRows.value.length) return []
  return Object.keys(drillDownRows.value[0])
})

const toNumber = (val) => {
  const n = Number(val)
  return Number.isFinite(n) ? n : null
}

const numericColumns = computed(() => {
  const rows = result.value?.data || []
  if (!rows.length) return []
  const cols = Object.keys(rows[0])
  return cols.filter((col) => rows.some((r) => toNumber(r[col]) !== null))
})

const dateLikeColumns = computed(() => {
  const rows = result.value?.data || []
  if (!rows.length) return []
  const cols = Object.keys(rows[0])
  return cols.filter((c) => /date|time|month|day|week|year/i.test(c))
})

const trendValues = computed(() => trendMeta.value.values || [])

const formatCurrency = (val) => {
  const n = toNumber(val) ?? 0
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(n)
}

const formatCompact = (val) => {
  const n = toNumber(val) ?? 0
  return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(n)
}

const sparkPath = (vals) => {
  if (!vals.length) return 'M0,18 L120,18'
  const safe = vals.map((v) => toNumber(v) ?? 0)
  const min = Math.min(...safe)
  const max = Math.max(...safe)
  const range = max - min || 1
  return safe
    .map((v, idx) => {
      const x = (idx / Math.max(1, safe.length - 1)) * 120
      const y = 30 - ((v - min) / range) * 24
      return `${idx === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')
}

const metrics = computed(() => {
  const rows = result.value?.data || []
  if (!rows.length || !numericColumns.value.length) {
    return [
      { title: 'Revenue', value: '$0', delta: 0, sparkPath: sparkPath([]) },
      { title: 'Orders', value: '0', delta: 0, sparkPath: sparkPath([]) },
      { title: 'Conversion', value: '0%', delta: 0, sparkPath: sparkPath([]) }
    ]
  }

  const mainCol = numericColumns.value[0]
  const secondaryCol = numericColumns.value[1] || numericColumns.value[0]
  const mainSeries = rows.map((r) => toNumber(r[mainCol]) ?? 0)
  const secondSeries = rows.map((r) => toNumber(r[secondaryCol]) ?? 0)
  const totalRevenue = mainSeries.reduce((sum, n) => sum + n, 0)
  const avgConversion = totalRevenue === 0 ? 0 : (secondSeries.reduce((s, n) => s + n, 0) / Math.max(totalRevenue, 1)) * 100

  const deltaFromSeries = (series) => {
    if (series.length < 2) return 0
    const half = Math.max(1, Math.floor(series.length / 2))
    const prev = series.slice(0, half).reduce((s, n) => s + n, 0) / half
    const curr = series.slice(half).reduce((s, n) => s + n, 0) / (series.length - half)
    if (!prev) return 0
    return ((curr - prev) / Math.abs(prev)) * 100
  }

  return [
    {
      title: 'Revenue',
      value: formatCurrency(totalRevenue),
      delta: deltaFromSeries(mainSeries),
      sparkPath: sparkPath(mainSeries.slice(-24))
    },
    {
      title: 'Orders',
      value: formatCompact(rows.length),
      delta: deltaFromSeries(mainSeries.slice(-Math.max(2, Math.floor(mainSeries.length / 2)))),
      sparkPath: sparkPath(mainSeries.slice(-18))
    },
    {
      title: 'Conversion',
      value: `${avgConversion.toFixed(2)}%`,
      delta: deltaFromSeries(secondSeries),
      sparkPath: sparkPath(secondSeries.slice(-18))
    }
  ]
})

const topProducts = computed(() => {
  const rows = result.value?.data || []
  if (!rows.length) return []
  const cols = Object.keys(rows[0])
  const valueCol = numericColumns.value[0]
  if (!valueCol) return []
  const nameCol = cols.find((c) => c !== valueCol) || cols[0]
  return rows
    .map((r) => ({ name: String(r[nameCol]), value: toNumber(r[valueCol]) ?? 0 }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 5)
    .map((r) => ({ ...r, value: formatCompact(r.value) }))
})

const geoPreview = computed(() => {
  const rows = result.value?.data || []
  if (!rows.length || !numericColumns.value.length) return []
  const cols = Object.keys(rows[0])
  const geoCol = cols.find((c) => /state|region|city|province|country|zip/i.test(c))
  if (!geoCol) return []
  const valueCol = numericColumns.value[0]

  const grouped = new Map()
  rows.forEach((row) => {
    const key = String(row[geoCol] ?? 'Unknown')
    const value = toNumber(row[valueCol]) ?? 0
    grouped.set(key, (grouped.get(key) || 0) + value)
  })
  const list = Array.from(grouped.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
  const max = list.length ? list[0][1] : 1
  return list.map(([label, value]) => ({
    label,
    value: formatCompact(value),
    opacity: Math.max(0.35, value / max)
  }))
})

const prepareTrendSeries = (rows) => {
  if (!rows.length) {
    return { xCol: null, yCol: null, categories: [], values: [] }
  }
  const cols = Object.keys(rows[0])
  const yCol = numericColumns.value[0] || cols[0]
  const xCol = dateLikeColumns.value[0] || cols.find((c) => c !== yCol) || null

  const categories = rows.map((row, idx) => (xCol ? String(row[xCol]) : `${idx + 1}`))
  const values = rows.map((row) => toNumber(row[yCol]) ?? 0)

  return { xCol, yCol, categories, values }
}

const renderTrendChart = async () => {
  await nextTick()
  const rows = result.value?.data || []
  if (!chartRef.value || !rows.length) {
    if (chartInstance.value) {
      chartInstance.value.clear()
    }
    return
  }
  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  }

  const meta = prepareTrendSeries(rows)
  trendMeta.value = meta

  chartInstance.value.off('click')
  chartInstance.value.on('click', (params) => {
    const clickedCategory = params.name
    const key = meta.xCol
    drillDownRows.value = key
      ? rows.filter((r) => String(r[key]) === String(clickedCategory)).slice(0, 100)
      : rows.slice(0, 100)
    drillDownTitle.value = key ? `${key} = ${clickedCategory}` : 'Selected Segment'
    drillDownVisible.value = true
  })

  chartInstance.value.setOption({
    backgroundColor: 'transparent',
    grid: { left: 44, right: 18, top: 28, bottom: 34 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#121215',
      borderColor: '#27272a',
      borderWidth: 1,
      textStyle: { color: '#e4e4e7' }
    },
    xAxis: {
      type: 'category',
      data: meta.categories,
      axisLine: { lineStyle: { color: '#27272a' } },
      axisLabel: { color: '#a1a1aa', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1f1f23' } },
      axisLabel: { color: '#a1a1aa', fontSize: 11 }
    },
    series: [
      {
        type: 'line',
        data: meta.values,
        smooth: 0.35,
        symbol: 'circle',
        symbolSize: 7,
        showSymbol: false,
        lineStyle: {
          width: 2.4,
          color: '#4f46e5'
        },
        itemStyle: {
          color: '#22d3ee'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(79,70,229,0.26)' },
            { offset: 1, color: 'rgba(79,70,229,0.02)' }
          ])
        }
      }
    ]
  })
}

const loadGroups = async () => {
  try {
    const res = await groupApi.getAll()
    if (res.success) {
      groups.value = res.data || []
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('Failed to load workplaces')
  }
}

const onGroupChange = async (groupId) => {
  if (!groupId) {
    workspaceDatasets.value = []
    workspaceRelations.value = []
    focusDatasetIds.value = []
    return
  }
  try {
    const [dsRes, relRes] = await Promise.all([
      groupApi.getDatasets(groupId),
      groupApi.getRelations(groupId)
    ])
    workspaceDatasets.value = dsRes.success ? (dsRes.data || []) : []
    workspaceRelations.value = relRes.success ? (relRes.data || []) : []
  } catch (error) {
    console.error(error)
    ElMessage.error('Failed to load workplace context')
  }
}

const typewrite = (text) => {
  if (typewriterTimer) {
    clearInterval(typewriterTimer)
    typewriterTimer = null
  }
  assistantTyping.value = true
  assistantStreamText.value = ''
  const target = text || 'Done.'
  let idx = 0
  typewriterTimer = setInterval(() => {
    assistantStreamText.value += target[idx] || ''
    idx += 1
    if (idx >= target.length) {
      clearInterval(typewriterTimer)
      typewriterTimer = null
      assistantTyping.value = false
      conversation.value.push({ role: 'assistant', content: target })
      assistantStreamText.value = ''
    }
  }, 18)
}

const executeAnalysis = async () => {
  const text = queryText.value.trim()
  if (!text) {
    ElMessage.warning('Please enter a query')
    return
  }
  if (!selectedGroupId.value) {
    ElMessage.warning('Select a workplace first')
    return
  }

  conversation.value.push({ role: 'user', content: text })
  analyzing.value = true
  drillDownVisible.value = false

  try {
    const res = await analysisApi.query(selectedGroupId.value, text, focusDatasetIds.value)
    if (res.success) {
      result.value = res
      executionTime.value = res.executionTime
      await renderTrendChart()
      typewrite(res.summary || 'Analysis completed.')
    } else {
      const msg = res.message || 'Analysis failed'
      conversation.value.push({ role: 'assistant', content: msg })
      ElMessage.error(msg)
    }
  } catch (error) {
    const msg = error?.response?.data?.message || 'Network error'
    conversation.value.push({ role: 'assistant', content: msg })
    ElMessage.error(msg)
  } finally {
    analyzing.value = false
    queryText.value = ''
  }
}

const handleResize = () => {
  if (chartInstance.value) {
    chartInstance.value.resize()
  }
}

watch(() => route.query.groupId, async (newId) => {
  if (!newId) return
  selectedGroupId.value = Number(newId)
  await onGroupChange(selectedGroupId.value)
}, { immediate: true })

watch(() => route.query.datasetId, async (datasetId) => {
  if (!datasetId) return
  try {
    const dsRes = await datasetApi.getById(Number(datasetId))
    if (dsRes.success && dsRes.data?.groupId) {
      selectedGroupId.value = dsRes.data.groupId
      await onGroupChange(dsRes.data.groupId)
    }
  } catch (error) {
    console.warn(error)
  }
}, { immediate: true })

onMounted(async () => {
  await loadGroups()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance.value) {
    chartInstance.value.dispose()
  }
  if (typewriterTimer) {
    clearInterval(typewriterTimer)
    typewriterTimer = null
  }
})
</script>

<style scoped>
.workplace-page {
  position: relative;
  min-height: calc(100vh - 104px);
  padding-right: 430px;
}

.bento-grid {
  display: grid;
  grid-template-columns: 2.1fr 1fr 1fr;
  grid-template-rows: 132px 140px minmax(300px, 1fr);
  gap: 12px;
}

.bento-card {
  background: #121215;
  border: 1px solid #27272a;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.35);
  transition: transform 0.24s cubic-bezier(0.2, 0.8, 0.2, 1), border-color 0.2s ease, box-shadow 0.2s ease;
}

.bento-card:hover {
  transform: translateY(-2px);
  border-color: #3f3f46;
  box-shadow: 0 18px 36px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(79, 70, 229, 0.22);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #fafafa;
}

.head-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mono {
  font-family: 'IBM Plex Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
}

.subtle {
  color: #a1a1aa;
  font-size: 12px;
}

.control-card {
  grid-column: 1 / 2;
  grid-row: 1 / 2;
}

.control-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.dark-select :deep(.el-select__wrapper) {
  background: #09090b;
  border: 1px solid #27272a;
  box-shadow: none;
}

.dark-select :deep(.el-select__placeholder),
.dark-select :deep(.el-select__selected-item) {
  color: #d4d4d8;
}

.sub-hint {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #a1a1aa;
  font-size: 12px;
}

.metric-card {
  min-height: 132px;
}

.metric-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.metric-title {
  color: #a1a1aa;
  font-size: 12px;
}

.metric-delta {
  font-size: 12px;
  font-weight: 600;
}

.metric-delta.up {
  color: #34d399;
}

.metric-delta.down {
  color: #fb7185;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: #fafafa;
}

.spark {
  margin-top: 8px;
  width: 100%;
  height: 34px;
}

.trend-card {
  grid-column: 1 / 3;
  grid-row: 2 / 4;
}

.trend-chart {
  height: calc(100% - 26px);
  min-height: 360px;
}

.chart-skeleton {
  height: 420px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
}

.bone {
  height: 14px;
  border-radius: 999px;
  background: #1f1f24;
}

.bone.short {
  width: 62%;
}

.products-card {
  grid-column: 3 / 4;
  grid-row: 2 / 3;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.product-row {
  display: grid;
  grid-template-columns: 34px 1fr auto;
  align-items: center;
  gap: 8px;
  border: 1px solid #27272a;
  border-radius: 10px;
  padding: 8px 10px;
  background: #101014;
}

.rank {
  color: #a1a1aa;
  font-size: 12px;
}

.name {
  color: #e4e4e7;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.value {
  color: #d4d4d8;
  font-size: 12px;
}

.geo-card {
  grid-column: 3 / 4;
  grid-row: 3 / 4;
}

.geo-preview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.heat-block {
  border: 1px solid #27272a;
  border-radius: 10px;
  padding: 10px;
  background: linear-gradient(160deg, rgba(79, 70, 229, 0.22), rgba(34, 211, 238, 0.08));
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.heat-block span {
  color: #d4d4d8;
  font-size: 12px;
}

.heat-block strong {
  color: #fafafa;
  font-size: 14px;
}

.empty-inline {
  color: #71717a;
  font-size: 12px;
  margin-top: 6px;
}

.drill-panel {
  position: absolute;
  left: 0;
  right: 430px;
  bottom: 10px;
  z-index: 12;
  border: 1px solid #3f3f46;
  border-radius: 12px;
  background: rgba(10, 10, 12, 0.92);
  backdrop-filter: blur(10px);
  padding: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
}

.drill-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.drill-head h4 {
  margin: 0;
  color: #fafafa;
  font-size: 13px;
  font-weight: 600;
}

.ai-command-center {
  position: fixed;
  right: 22px;
  bottom: 16px;
  width: 402px;
  height: calc(100vh - 112px);
  max-height: 760px;
  background: rgba(12, 12, 15, 0.95);
  border: 1px solid #27272a;
  border-radius: 14px;
  box-shadow: 0 26px 50px rgba(0, 0, 0, 0.58);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 20;
}

.ai-command-center.collapsed {
  height: 48px;
}

.ai-toggle {
  height: 48px;
  border: none;
  background: #0f0f13;
  color: #e4e4e7;
  border-bottom: 1px solid #27272a;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  cursor: pointer;
}

.ai-header {
  border-bottom: 1px solid #27272a;
  padding: 10px 12px;
}

.agent {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-wrap {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  border: 1px solid #3f3f46;
}

.avatar-wrap.thinking {
  animation: glow 1.3s infinite ease-in-out;
  box-shadow: 0 0 0 1px rgba(79, 70, 229, 0.35), 0 0 20px rgba(79, 70, 229, 0.35);
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4f46e5, #06b6d4);
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.agent-name {
  color: #fafafa;
  font-size: 13px;
  font-weight: 600;
}

.agent-status {
  color: #a1a1aa;
  font-size: 11px;
}

.ai-stream {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg {
  display: flex;
}

.msg.user {
  justify-content: flex-end;
}

.msg.assistant {
  justify-content: flex-start;
}

.msg-content {
  max-width: 88%;
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
}

.msg.user .msg-content {
  background: rgba(79, 70, 229, 0.24);
  border: 1px solid rgba(79, 70, 229, 0.45);
  color: #eef2ff;
}

.msg.assistant .msg-content {
  background: #111117;
  border: 1px solid #27272a;
  color: #d4d4d8;
}

.cursor {
  animation: blink 1s steps(1, start) infinite;
}

.thinking-skeleton {
  margin-top: 6px;
}

.thinking-skeleton .line {
  height: 10px;
  border-radius: 999px;
  background: #1f1f24;
  margin-bottom: 8px;
}

.thinking-skeleton .line.short {
  width: 68%;
}

.ai-input {
  border-top: 1px solid #27272a;
  padding: 10px;
}

.ai-input :deep(.el-textarea__inner) {
  background: #09090b;
  border: 1px solid #27272a;
  color: #e4e4e7;
  box-shadow: none;
}

.send-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.send-row :deep(.el-button) {
  background: #4f46e5;
  border: 1px solid #6366f1;
}

.send-row :deep(.el-button:hover) {
  background: #6366f1;
}

.shimmer {
  position: relative;
  overflow: hidden;
}

.shimmer::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.09), transparent);
  animation: shimmer 1.2s infinite;
}

@keyframes shimmer {
  100% {
    transform: translateX(100%);
  }
}

@keyframes glow {
  0%,
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 1px rgba(79, 70, 229, 0.35), 0 0 12px rgba(79, 70, 229, 0.24);
  }
  50% {
    transform: scale(1.03);
    box-shadow: 0 0 0 1px rgba(79, 70, 229, 0.55), 0 0 24px rgba(79, 70, 229, 0.4);
  }
}

@keyframes blink {
  0%,
  50% {
    opacity: 1;
  }
  50.01%,
  100% {
    opacity: 0;
  }
}

.drill-enter-active,
.drill-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.drill-enter-from,
.drill-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 1460px) {
  .workplace-page {
    padding-right: 0;
  }

  .ai-command-center {
    position: static;
    width: 100%;
    height: 420px;
    margin-top: 12px;
  }

  .drill-panel {
    right: 0;
  }
}

@media (max-width: 1100px) {
  .bento-grid {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
  }

  .control-card,
  .trend-card,
  .products-card,
  .geo-card {
    grid-column: auto;
    grid-row: auto;
  }
}
</style>

