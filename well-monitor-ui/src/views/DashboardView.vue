<template>
  <div class="panel relative-panel">
    <div class="alert-toast-container">
      <div v-for="alert in toastList" :key="alert.id" class="alert-toast">
        <i class="fa-solid fa-triangle-exclamation"></i> {{ alert.message }}
      </div>
    </div>

    <h2 class="panel-title"><i class="fa-solid fa-chart-pie"></i> 油藏区块生产分析大屏</h2>

    <div class="data-cards">
      <div class="card card-total">
        <div class="card-icon"><i class="fa-solid fa-layer-group"></i></div>
        <div class="card-info"><p>总井数</p><h3>{{ totalWells }} <span class="unit">口</span></h3></div>
      </div>
      <div class="card card-oil">
        <div class="card-icon"><i class="fa-solid fa-oil-well"></i></div>
        <div class="card-info"><p>油井数量</p><h3>{{ oilWells }} <span class="unit">口</span></h3></div>
      </div>
      <div class="card card-water">
        <div class="card-icon"><i class="fa-solid fa-droplet"></i></div>
        <div class="card-info"><p>水井数量</p><h3>{{ waterWells }} <span class="unit">口</span></h3></div>
      </div>
      <div class="card card-danger">
        <div class="card-icon"><i class="fa-solid fa-triangle-exclamation"></i></div>
        <div class="card-info"><p>停机异常报警</p><h3>{{ stopWells }} <span class="unit">口</span></h3></div>
      </div>
    </div>

    <div class="bottom-layout">

      <div class="global-alert-panel">
        <div class="alert-header">
          <h3><i class="fa-solid fa-clipboard-list"></i> 全区实时异常日志 </h3>
          <span class="alert-count">{{ globalAlerts.length }} 条记录</span>
        </div>

        <div class="alert-list">
          <div v-if="globalAlerts.length === 0" class="empty-alert">
            <i class="fa-regular fa-circle-check" style="font-size: 30px; color: #27ae60; margin-bottom: 10px;"></i>
            <p>当前全区生产平稳，无异常报警</p>
          </div>

          <div v-for="item in globalAlerts" :key="item.id" class="alert-item">
            <div class="alert-main">
              <div class="alert-meta">
                <span class="alert-time">{{ item.time }}</span>
                <span :class="getStatusClass(item.status)">{{ item.status }}</span>
                <span class="level-tag" v-if="item.level">异常级别: {{ item.level }}</span>
              </div>
              <div class="alert-content">
                <strong>[{{ item.wellId }}]</strong> {{ item.message }}
              </div>
            </div>
            <div class="alert-action">
              <button class="btn-tiny" @click="openAlertEdit(item)"><i class="fa-solid fa-pen"></i> 编辑</button>
            </div>
          </div>
        </div>
      </div>

      <div class="monitor-section">
        <div class="monitor-header">
          <h3><i class="fa-solid fa-satellite-dish" style="color:#e74c3c;"></i> 单井遥测分析 </h3>
          <div class="well-selector">
            <label>监控对象：</label>
            <select v-model="selectedWellId" @change="switchWell">
              <option v-for="well in wellList" :key="well.wellId" :value="well.wellId">
                {{ well.wellId }} ({{ well.wellType }})
              </option>
            </select>
          </div>
        </div>

        <div class="chart-box line-chart-container" ref="lineChartRef" v-show="selectedWellStatus === '正常'"></div>

        <div class="offline-display-box" v-show="selectedWellStatus === '停机'">
          <div class="offline-content">
            <div class="icon-pulse-wrapper"><i class="fa-solid fa-power-off"></i></div>
            <h4>设备已停机</h4>
            <p>该井位当前处于停机断联状态，遥测数据暂无更新。</p>
          </div>
        </div>

      </div>

    </div>

    <Transition name="fade">
      <div class="modal-overlay" v-if="showAlertDialog">
        <div class="modal-content" style="width: 500px;">
          <h3><i class="fa-solid fa-pen-to-square"></i> 编辑异常处理状态</h3>

          <div class="form-group-row">
            <div class="form-group">
              <label>关联井号</label>
              <input type="text" v-model="alertForm.wellId">
            </div>
            <div class="form-group">
              <label>处理状态</label>
              <select v-model="alertForm.status">
                <option value="未处理">未处理</option>
                <option value="处理中">处理中</option>
                <option value="已处理">已处理</option>
              </select>
            </div>
          </div>

          <div class="form-group-row">
            <div class="form-group">
              <label>异常级别</label>
              <select v-model="alertForm.warningLevel">
                <option value="一般">一般</option>
                <option value="严重">严重</option>
                <option value="紧急">紧急</option>
              </select>
            </div>
          </div>

          <div class="form-group-row">
            <div class="form-group">
              <label>异常内容说明</label>
              <textarea v-model="alertForm.warningContent" rows="3" class="custom-textarea"></textarea>
            </div>
          </div>

          <div class="modal-actions">
            <button class="btn btn-secondary" @click="showAlertDialog = false">取消</button>
            <button class="btn btn-primary" @click="saveAlertData">保存修改</button>
          </div>
        </div>
      </div>
    </Transition>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import request from '../utils/request'
import * as echarts from 'echarts'

import { globalRealtimeData, startGlobalEngine } from '../utils/realtimeStore'

const wellList = ref([])
const totalWells = ref(0)
const oilWells = ref(0)
const waterWells = ref(0)
const stopWells = ref(0)

const lineChartRef = ref(null)
const toastList = ref([])
const globalAlerts = ref([])

let lineChart
let alertTimer = null
const selectedWellId = ref('')
const selectedWellType = ref('')
const selectedWellStatus = ref('正常') // 🌟 新增：追踪当前选中井的状态

const showAlertDialog = ref(false)
const alertForm = ref({
  id: null, wellId: '', warningLevel: '一般', status: '未处理', warningContent: ''
})

const getStatusClass = (status) => {
  if (status === '已处理') return 'status-tag bg-success'
  if (status === '处理中') return 'status-tag bg-warning'
  return 'status-tag bg-danger'
}

const openAlertEdit = (item) => {
  alertForm.value = {
    id: item.id, wellId: item.wellId, warningLevel: item.level || '一般',
    status: item.status || '未处理', warningContent: item.message
  }
  showAlertDialog.value = true
}

const saveAlertData = async () => {
  try {
    await request.put('/api/warning/update', alertForm.value)
    showAlertDialog.value = false
    fetchRealAlerts()
  } catch (error) {
    alert("保存失败，请检查网络或后端接口！")
  }
}

const fetchRealAlerts = async () => {
  try {
    const res = await request.get('/api/warning/list')
    globalAlerts.value = res.data.slice(0, 50).map(log => {
      const d = new Date(log.createTime)
      const timeStr = [d.getHours(), d.getMinutes(), d.getSeconds()].map(n => n.toString().padStart(2, '0')).join(':')
      return {
        id: log.id, time: timeStr, wellId: log.wellId, message: log.warningContent,
        level: log.warningLevel || '一般', status: log.status || '未处理'
      }
    })
  } catch (error) {
    console.error("获取日志失败", error)
  }
}

const fetchDataAndRender = async () => {
  try {
    const res = await request.get('/api/well/list-with-data')
    const rawData = res.data || []

    wellList.value = rawData.map(item => item.info ? item.info : item)

    startGlobalEngine(wellList.value)

    totalWells.value = wellList.value.length
    oilWells.value = wellList.value.filter(w => w.wellType === '油井').length
    waterWells.value = wellList.value.filter(w => w.wellType === '水井').length
    stopWells.value = wellList.value.filter(w => w.status === '停机').length

    if (wellList.value.length > 0) {
      selectedWellId.value = wellList.value[0].wellId
      selectedWellType.value = wellList.value[0].wellType
      selectedWellStatus.value = wellList.value[0].status // 🌟 初始化选中井的状态
    }

    fetchRealAlerts()
    await nextTick()

    if (selectedWellStatus.value === '正常') {
      initLineChart()
    }

  } catch (error) {
    console.error('获取数据失败:', error)
  }
}

const switchWell = () => {
  const targetWell = wellList.value.find(w => w.wellId === selectedWellId.value)
  if (targetWell) {
    selectedWellType.value = targetWell.wellType
    selectedWellStatus.value = targetWell.status // 🌟 切换井时同步更新状态

    if (targetWell.status === '正常') {
      nextTick(() => {
        if (!lineChart) initLineChart()
        else {
          lineChart.resize() // 🌟 关键：从隐藏状态恢复时，重新计算宽高
          updateChartOption()
        }
      })
    }
  }
}

const initLineChart = () => {
  if (!lineChartRef.value) return
  lineChart = echarts.init(lineChartRef.value)
  updateChartOption()
}

const updateChartOption = () => {
  if (!lineChart || !selectedWellId.value || selectedWellStatus.value === '停机') return

  const wellData = globalRealtimeData[selectedWellId.value]
  const data = wellData?.history
  if (!data) return

  const isOil = selectedWellType.value === '油井'
  const flowName = isOil ? '产液量 (t/d)' : '注水量 (m³/d)'
  const flowColor = isOil ? '#f39c12' : '#3498db'

  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: '3%', right: '3%', bottom: '5%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: data.time },
    yAxis: [
      { type: 'value', name: '压力(MPa)', position: 'left', min: 0, max: 25, splitLine: { lineStyle: { type: 'dashed' } } },
      { type: 'value', name: flowName, position: 'right', min: 0, max: 150, splitLine: { show: false } }
    ],
    // 🌟 核心：ECharts 的原生浮水印，如果不在线就显示这段文字
    graphic: wellData.isOnline ? [] : [{
      type: 'text',
      left: 'center',
      top: 'center',
      z: 100,
      style: {
        fill: '#94a3b8',
        text: '⚠ 等待传感器信号接入...',
        font: 'bold 22px sans-serif'
      }
    }],
    series: [
      // 注意加上 connectNulls: false，确保图表遇到 null 时断开，而不是连线
      { name: '压力', type: 'line', yAxisIndex: 0, smooth: false, symbol: 'circle', symbolSize: 6, itemStyle: { color: '#e74c3c' }, data: data.pressure, connectNulls: false },
      { name: flowName, type: 'line', yAxisIndex: 1, smooth: false, symbol: 'rect', symbolSize: 6, itemStyle: { color: flowColor }, lineStyle: { type: 'dashed' }, data: data.flow, connectNulls: false }
    ]
  })
}

watch(() => globalRealtimeData[selectedWellId.value]?.history?.time, () => {
  if (selectedWellStatus.value === '正常') {
    updateChartOption()
  }
}, { deep: true })

onMounted(() => {
  fetchDataAndRender()
  window.addEventListener('resize', () => { lineChart?.resize() })
  alertTimer = setInterval(fetchRealAlerts, 5000)
})

onUnmounted(() => {
  if (alertTimer) clearInterval(alertTimer)
})
</script>

<style scoped>
/* 原有基础样式 */
.relative-panel { position: relative; overflow-x: hidden;}
.panel { background-color: #f4f7f6; padding: 20px; min-height: calc(100vh - 112px); box-sizing: border-box; }
.panel-title { margin-top: 0; margin-bottom: 20px; font-size: 20px; color: var(--primary-color); padding-bottom: 12px; }

.alert-toast-container { position: absolute; top: 20px; right: 20px; z-index: 9999; display: flex; flex-direction: column; gap: 12px; pointer-events: none; }
.alert-toast { background: rgba(231, 76, 60, 0.95); color: white; padding: 14px 24px; border-radius: 8px; box-shadow: 0 6px 16px rgba(231, 76, 60, 0.3); font-size: 14px; font-weight: 500; animation: slideIn 0.4s forwards; }
@keyframes slideIn { 0% { transform: translateX(120%); opacity: 0; } 100% { transform: translateX(0); opacity: 1; } }

.data-cards { display: flex; gap: 20px; margin-bottom: 20px; }
.card { flex: 1; display: flex; align-items: center; padding: 16px 20px; border-radius: 10px; color: white; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.card-icon { font-size: 32px; margin-right: 16px; opacity: 0.8; }
.card-info p { margin: 0 0 5px 0; font-size: 13px; opacity: 0.9; }
.card-info h3 { margin: 0; font-size: 24px; font-weight: bold; }
.unit { font-size: 12px; font-weight: normal; opacity: 0.8; }
.card-total { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.card-oil { background: linear-gradient(135deg, #2af598 0%, #009efd 100%); }
.card-water { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
.card-danger { background: linear-gradient(135deg, #ff0844 0%, #ffb199 100%); }

.bottom-layout { display: flex; gap: 20px; height: 420px; }
.global-alert-panel { flex: 0 0 32%; background: white; border-radius: 10px; border: 1px solid #e2e8f0; display: flex; flex-direction: column; overflow: hidden; }
.alert-header { background: #fff5f5; padding: 15px 20px; border-bottom: 1px solid #fed7d7; display: flex; justify-content: space-between; align-items: center;}
.alert-header h3 { margin: 0; font-size: 16px; color: #c53030; }
.alert-count { font-size: 12px; background: #c53030; color: white; padding: 2px 8px; border-radius: 10px;}
.alert-list { flex: 1; overflow-y: auto; padding: 10px; background: #fafafa; }
.alert-list::-webkit-scrollbar { width: 6px; }
.alert-list::-webkit-scrollbar-thumb { background-color: #cbd5e1; border-radius: 10px; }
.empty-alert { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #7f8c8d; font-size: 14px;}

.alert-item { position: relative; display: flex; justify-content: space-between; align-items: center; background: white; border-left: 4px solid #e53e3e; padding: 12px 15px; margin-bottom: 10px; border-radius: 4px; box-shadow: 0 2px 5px rgba(0,0,0,0.05); animation: fadeIn 0.5s; transition: 0.2s;}
.alert-item:hover { transform: translateX(2px); box-shadow: 0 4px 8px rgba(0,0,0,0.1); }
.alert-main { flex: 1; min-width: 0; }
.alert-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.alert-time { font-size: 12px; color: #a0aec0; }
.alert-content { font-size: 13px; color: #2d3748; line-height: 1.4; word-break: break-all;}
.alert-content strong { color: #e53e3e; }

.status-tag, .level-tag { padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: bold; color: white; }
.bg-success { background-color: #27ae60; }
.bg-warning { background-color: #f39c12; color: #fff; }
.bg-danger { background-color: #e74c3c; }
.level-tag { background-color: #95a5a6; }

.alert-action { opacity: 0; transition: opacity 0.2s; padding-left: 10px; }
.alert-item:hover .alert-action { opacity: 1; }
.btn-tiny { background: #3498db; color: white; border: none; padding: 5px 10px; font-size: 12px; border-radius: 4px; cursor: pointer; display: flex; align-items: center; gap: 4px; transition: 0.2s; }
.btn-tiny:hover { background: #2980b9; transform: scale(1.05); }

.monitor-section { flex: 1; background: white; border-radius: 10px; padding: 20px; border: 1px solid #e2e8f0; display: flex; flex-direction: column; }
.monitor-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; padding-bottom: 10px; border-bottom: 2px dashed #cbd5e1;}
.monitor-header h3 { margin: 0; font-size: 16px; color: var(--primary-color);}
.well-selector { display: flex; align-items: center; font-size: 13px; font-weight: bold;}
.well-selector select { margin-left: 10px; padding: 6px 10px; border: 1px solid #bdc3c7; border-radius: 6px; outline: none; font-size: 13px; cursor: pointer;}
.line-chart-container { flex: 1; width: 100%; }

/* 🌟 新增：停机占位符的炫酷样式 */
.offline-display-box { flex: 1; display: flex; align-items: center; justify-content: center; background: #fafafa; border-radius: 8px; border: 1px dashed #cbd5e1; }
.offline-content { text-align: center; color: #94a3b8; }
.icon-pulse-wrapper { display: inline-flex; align-items: center; justify-content: center; width: 60px; height: 60px; border-radius: 50%; background: #fee2e2; color: #ef4444; font-size: 24px; margin-bottom: 15px; animation: gentle-pulse 2s infinite; }
@keyframes gentle-pulse { 0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); } 70% { box-shadow: 0 0 0 15px rgba(239, 68, 68, 0); } 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); } }
.offline-content h4 { margin: 0 0 8px 0; color: #ef4444; font-size: 18px; }
.offline-content p { margin: 0; font-size: 13px; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }

.modal-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 10000; }
.modal-content { background: white; padding: 30px; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); }
.modal-content h3 { margin-top: 0; color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; margin-bottom: 20px;}
.form-group-row { display: flex; gap: 15px; margin-bottom: 15px; }
.form-group { flex: 1; display: flex; flex-direction: column; }
.form-group label { font-size: 13px; margin-bottom: 6px; font-weight: bold; color: #34495e; }
.form-group input, .form-group select { padding: 8px 10px; border: 1px solid #bdc3c7; border-radius: 6px; outline: none; font-size: 14px; }
.custom-textarea { padding: 10px; border: 1px solid #bdc3c7; border-radius: 6px; outline: none; font-size: 14px; resize: vertical; }
.modal-actions { margin-top: 25px; display: flex; justify-content: flex-end; gap: 15px; }
.btn { padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; font-weight: bold; transition: 0.2s; }
.btn-primary { background: var(--primary-color, #3498db); color: white; }
.btn-secondary { background: #95a5a6; color: white; }
</style>