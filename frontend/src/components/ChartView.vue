<template>
  <div class="chart-container">
    <div class="chart-toolbar">
      <el-radio-group v-model="currentChartType" size="large" class="modern-radio-group">
        <el-radio-button value="BAR">
          <el-icon><DataLine /></el-icon> 柱状图
        </el-radio-button>
        <el-radio-button value="LINE">
          <el-icon><TrendCharts /></el-icon> 折线图
        </el-radio-button>
        <el-radio-button value="PIE">
          <el-icon><PieChart /></el-icon> 饼图
        </el-radio-button>
        <el-radio-button value="SCATTER">
          <el-icon><CopyDocument /></el-icon> 散点图
        </el-radio-button>
      </el-radio-group>
    </div>
    
    <div ref="chartRef" class="chart-area"></div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick, shallowRef } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  chartType: {
    type: String,
    default: 'BAR'
  }
})

const chartRef = ref(null)
const currentChartType = ref(props.chartType || 'BAR')
const chartInstance = shallowRef(null)

// Apple Color Palette (Solid, vibrant, clean)
const appleBlue = '#007AFF';
const appleBlueHover = '#0066CC';
const applePurple = '#AF52DE';
const appleOrange = '#FF9500';
const appleGreen = '#34C759';
const appleRed = '#FF3B30';
const appleTeal = '#5AC8FA';
const appleIndigo = '#5856D6';

const colorPalette = [appleBlue, applePurple, appleOrange, appleGreen, appleRed, appleTeal, appleIndigo];

// 通用坐标轴配置
const commonAxisLine = {
  lineStyle: { color: 'rgba(0,0,0,0.1)' }
}
const commonAxisLabel = {
  color: '#86868b', // apple-text-secondary
  fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif'
}

// 通用Tooltip配置 (Heavy Glassmorphism matching App.vue)
const commonTooltip = {
  backgroundColor: 'rgba(255, 255, 255, 0.65)',
  borderColor: 'rgba(0, 0, 0, 0.08)',
  borderWidth: 1,
  padding: [12, 16],
  textStyle: {
    color: '#1d1d1f', // apple-text-primary
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
    fontSize: 13,
    fontWeight: 500
  },
  extraCssText: 'box-shadow: 0 16px 32px rgba(0,0,0,0.06), 0 4px 8px rgba(0,0,0,0.03); backdrop-filter: blur(40px); -webkit-backdrop-filter: blur(40px); border-radius: 12px;'
}

// 获取数据的列名
const getColumns = () => {
  if (!props.data || props.data.length === 0) return []
  return Object.keys(props.data[0])
}

// 判断是否是数值列
const isNumericColumn = (colName) => {
  if (!props.data || props.data.length === 0) return false
  const firstValue = props.data[0][colName]
  return typeof firstValue === 'number'
}

const getColumnTypes = () => {
  const columns = getColumns()
  const categoryColumns = []
  const numericColumns = []
  
  columns.forEach(col => {
    if (isNumericColumn(col)) {
      numericColumns.push(col)
    } else {
      categoryColumns.push(col)
    }
  })
  
  return { categoryColumns, numericColumns }
}

// 高级配置生成函数
const getBarOption = () => {
  const { categoryColumns, numericColumns } = getColumnTypes()
  const categoryCol = categoryColumns[0] || getColumns()[0]
  const valueCol = numericColumns[0] || getColumns()[1]
  if (!categoryCol || !valueCol) return {}
  
  const categories = props.data.map(item => String(item[categoryCol]))
  const values = props.data.map(item => Number(item[valueCol]) || 0)
  
  return {
    animationDuration: 1200,
    animationEasing: 'cubicOut',
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(0, 0, 0, 0.03)' } }
    },
    grid: { left: '3%', right: '4%', bottom: '5%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: categories,
      axisLine: commonAxisLine,
      axisTick: { show: false },
      axisLabel: { ...commonAxisLabel, margin: 12, rotate: categories.length > 8 ? 25 : 0 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.04)', type: 'solid' } },
      axisLabel: commonAxisLabel
    },
    series: [{
      name: valueCol,
      type: 'bar',
      data: values,
      barMaxWidth: 36, // Apple prefers slightly thinner bars
      itemStyle: {
        color: appleBlue,
        borderRadius: [4, 4, 0, 0] // 更符合 iOS UI 的小圆角
      },
      emphasis: {
        itemStyle: {
          color: appleBlueHover,
        }
      }
    }]
  }
}

const getLineOption = () => {
  const { categoryColumns, numericColumns } = getColumnTypes()
  const categoryCol = categoryColumns[0] || getColumns()[0]
  const valueCol = numericColumns[0] || getColumns()[1]
  if (!categoryCol || !valueCol) return {}
  
  const categories = props.data.map(item => String(item[categoryCol]))
  const values = props.data.map(item => Number(item[valueCol]) || 0)
  
  return {
    animationDuration: 1500,
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(0,0,0,0.1)' } } // 苹果很少用十字准星，通常是一条细线
    },
    grid: { left: '3%', right: '4%', bottom: '5%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: categories,
      axisLine: commonAxisLine,
      axisTick: { show: false },
      axisLabel: { ...commonAxisLabel, margin: 12 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.04)' } },
      axisLabel: commonAxisLabel
    },
    series: [{
      name: valueCol,
      type: 'line',
      smooth: 0.4, // Apple's bezier curves are very smooth
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: false,
      data: values,
      lineStyle: {
        width: 3,
        color: appleBlue,
        shadowColor: 'rgba(0, 122, 255, 0.2)',
        shadowBlur: 10,
        shadowOffsetY: 4
      },
      itemStyle: { color: appleBlue, borderColor: '#fff', borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0, 122, 255, 0.15)' }, // Apple fade
          { offset: 1, color: 'rgba(0, 122, 255, 0)' }
        ])
      }
    }]
  }
}

const getPieOption = () => {
  const { categoryColumns, numericColumns } = getColumnTypes()
  const categoryCol = categoryColumns[0] || getColumns()[0]
  const valueCol = numericColumns[0] || getColumns()[1]
  if (!categoryCol || !valueCol) return {}
  
  const pieData = props.data.map(item => ({
    name: String(item[categoryCol]),
    value: Number(item[valueCol]) || 0
  }))
  
  return {
    animationDuration: 1200,
    color: colorPalette,
    tooltip: {
      ...commonTooltip,
      trigger: 'item',
      formatter: '<div style="display:flex;align-items:center;gap:8px;"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background-color:{color}"></span>{b}</div><div style="font-weight:600;margin-top:4px;font-size:16px;">{c} <span style="font-size:12px;color:#86868b;font-weight:400;">({d}%)</span></div>'
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: '5%',
      top: 'middle',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#86868b', fontSize: 13, fontFamily: 'Inter' }
    },
    series: [{
      name: valueCol,
      type: 'pie',
      radius: ['50%', '75%'], // Apple likes thick rings
      center: ['40%', '50%'],
      roseType: false,
      itemStyle: {
        borderRadius: 4, // 缝隙边缘小圆角
        borderColor: '#fff',
        borderWidth: 2
      },
      label: { show: false },
      labelLine: { show: false },
      emphasis: {
        scaleSize: 5,
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.1)'
        }
      },
      data: pieData
    }]
  }
}

const getScatterOption = () => {
  const { numericColumns } = getColumnTypes()
  if (numericColumns.length < 2) return getBarOption()
  
  const xCol = numericColumns[0]
  const yCol = numericColumns[1]
  const scatterData = props.data.map(item => [Number(item[xCol]) || 0, Number(item[yCol]) || 0])
  
  return {
    animationDuration: 1200,
    tooltip: { ...commonTooltip, trigger: 'item' },
    grid: { left: '3%', right: '4%', bottom: '5%', top: '10%', containLabel: true },
    xAxis: { name: xCol, type: 'value', axisLine: commonAxisLine, splitLine: { show: false }, axisLabel: commonAxisLabel },
    yAxis: { name: yCol, type: 'value', splitLine: { lineStyle: { color: 'rgba(0,0,0,0.04)', type: 'solid' } }, axisLabel: commonAxisLabel },
    series: [{
      type: 'scatter',
      data: scatterData,
      symbolSize: 10,
      itemStyle: {
        color: applePurple,
        opacity: 0.8,
        shadowBlur: 5,
        shadowColor: 'rgba(175, 82, 222, 0.3)'
      },
      emphasis: {
        focus: 'series',
        itemStyle: { opacity: 1, color: appleBlue, borderColor: '#fff', borderWidth: 2 }
      }
    }]
  }
}

const getChartOption = () => {
  switch (currentChartType.value) {
    case 'BAR': return getBarOption()
    case 'LINE': return getLineOption()
    case 'PIE': return getPieOption()
    case 'SCATTER': return getScatterOption()
    default: return getBarOption()
  }
}

const renderChart = () => {
  if (!chartRef.value || !props.data || props.data.length === 0) return
  if (currentChartType.value === 'TABLE') return
  
  nextTick(() => {
    if (!chartInstance.value) {
      chartInstance.value = echarts.init(chartRef.value)
    }
    chartInstance.value.setOption(getChartOption(), true)
  })
}

watch(() => props.data, renderChart, { deep: true })
watch(() => props.chartType, (newVal) => {
  currentChartType.value = newVal || 'BAR'
})
watch(currentChartType, renderChart)

const handleResize = () => {
  if (chartInstance.value) {
    chartInstance.value.resize()
  }
}

onMounted(() => {
  if (currentChartType.value === 'TABLE') currentChartType.value = 'BAR' // Default to chart view over table
  renderChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance.value) {
    chartInstance.value.dispose()
  }
})
</script>

<style scoped>
.chart-container {
  width: 100%;
}

.chart-toolbar {
  margin-bottom: 24px;
  display: flex;
  justify-content: center;
}

/* 按钮组样式苹果化 (Segmented Control style) */
.modern-radio-group {
  background: rgba(0, 0, 0, 0.05); /* 类似 iOS 分段控制器的底色 */
  padding: 3px;
  border-radius: 9px;
  display: inline-flex;
}

.modern-radio-group :deep(.el-radio-button__inner) {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: none !important;
  color: #86868b;
  transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);
  font-weight: 500;
  font-size: 13px;
  background: transparent !important;
  border-radius: 7px !important;
  box-shadow: none !important;
}

.modern-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #ffffff !important;
  color: #1d1d1f !important;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08), 0 1px 1px rgba(0, 0, 0, 0.04) !important;
  transform: none;
}

.modern-radio-group :deep(.el-radio-button__inner:hover) {
  color: #1d1d1f;
}

.modern-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner:hover) {
  color: #1d1d1f;
}

.chart-area {
  width: 100%;
  height: 480px; 
}
</style>
