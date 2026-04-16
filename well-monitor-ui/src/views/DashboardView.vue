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
      <div class="card card-danger">
        <div class="card-icon"><i class="fa-solid fa-triangle-exclamation"></i></div>
        <div class="card-info"><p>单井异常未处理</p><h3>{{ unhandledAnomalies }} <span class="unit">条</span></h3></div>
      </div>
      <div class="card card-warning">
        <div class="card-icon"><i class="fa-solid fa-network-wired"></i></div>
        <div class="card-info"><p>井组异常未处理</p><h3>{{ unhandledGroups }} <span class="unit">条</span></h3></div>
      </div>
    </div>

    <div class="bottom-layout-rows">
      <div class="row-wrapper">
        <div class="monitor-section" style="flex: 1;">
          <div class="monitor-header">
            <h3><i class="fa-solid fa-satellite-dish" style="color:#e74c3c;"></i> 单井遥测分析</h3>
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

        <div class="monitor-section" style="flex: 1;">
          <div class="monitor-header">
            <h3><i class="fa-solid fa-network-wired" style="color:#3498db;"></i> 井组遥测分析</h3>
            <div class="well-selector">
              <label>监控井组：</label>
              <select v-model="selectedGroupId" @change="switchGroup">
                <option v-for="group in groupList" :key="group.centerWaterWell" :value="group.centerWaterWell">
                  {{ group.groupName }} ({{ group.centerWaterWell }})
                </option>
                <option v-if="groupList.length === 0" value="">暂无井组数据</option>
              </select>
            </div>
          </div>

          <div class="group-stats-bar" v-if="currentGroupData">
            <div class="stat-item">
              <span>注水总量</span><b>{{ currentGroupData.totalInjectVol || 0 }} <small>m³/d</small></b>
            </div>
            <div class="stat-item">
              <span>产液总量</span><b>{{ (currentGroupData.totalLiquidVol || 0).toFixed(1) }} <small>t/d</small></b>
            </div>
            <div class="stat-item highlight">
              <span>综合注采比</span><strong>{{ currentGroupData.injectionProductionRatio || '0.00' }}</strong>
            </div>
          </div>

          <div class="chart-box line-chart-container" ref="groupChartRef" v-show="groupList.length > 0"></div>
          <div class="offline-display-box" v-show="groupList.length === 0">
            <div class="offline-content">
              <i class="fa-solid fa-circle-nodes" style="font-size: 40px; color:#cbd5e1; margin-bottom:10px;"></i>
              <p>请先在"油水井台账"中建立井组连通关系</p>
            </div>
          </div>
        </div>
      </div>

      <div class="row-wrapper">
        <div class="global-alert-panel" style="flex: 1;">
          <div class="alert-header">
            <h3><i class="fa-solid fa-clipboard-list"></i> 单井专家诊断日志 </h3>
            <span class="alert-count">{{ singleWellAlerts.length }} 条记录</span>
          </div>
          <div class="alert-list">
            <div v-if="singleWellAlerts.length === 0" class="empty-alert">
              <i class="fa-regular fa-circle-check" style="font-size: 30px; color: #27ae60; margin-bottom: 10px;"></i>
              <p>当前单井生产平稳，无异常报警</p>
            </div>
            <div v-for="item in singleWellAlerts" :key="item.id" :class="['alert-item', getAlertCardClass(item.type)]">
              <div class="alert-main">
                <div class="alert-meta">
                  <span class="alert-time">{{ item.time }}</span>
                  <span :class="getStatusClass(item.status)">{{ item.status }}</span>
                  <span class="level-tag" v-if="item.level">级别: {{ item.level }}</span>
                  <span :class="getTypeTagClass(item.type)">
                     <i :class="getTypeIcon(item.type)"></i> {{ getTypeName(item.type) }}
                  </span>
                </div>
                <div class="alert-content" v-html="formatAlertMessage(item.wellId, item.message, item.type)"></div>
              </div>
              <div class="alert-action">
                <button class="btn-tiny" @click="openAlertEdit(item)"><i class="fa-solid fa-pen"></i> 处置</button>
              </div>
            </div>
          </div>
        </div>

        <div class="global-alert-panel" style="flex: 1;">
          <div class="alert-header" style="background: #fdf4ff; border-bottom: 1px solid #f3e8ff;">
            <h3 style="color: #7e22ce;"><i class="fa-solid fa-network-wired"></i> 井组系统预警日志 </h3>
            <span class="alert-count" style="background: #7e22ce;">{{ groupWellAlerts.length }} 条记录</span>
          </div>
          <div class="alert-list">
            <div v-if="groupWellAlerts.length === 0" class="empty-alert">
              <i class="fa-solid fa-shield-halved" style="font-size: 30px; color: #8e44ad; margin-bottom: 10px;"></i>
              <p>当前各井组拓扑连通运行正常</p>
            </div>
            <div v-for="item in groupWellAlerts" :key="item.id" :class="['alert-item', getAlertCardClass(item.type)]">
              <div class="alert-main">
                <div class="alert-meta">
                  <span class="alert-time">{{ item.time }}</span>
                  <span :class="getStatusClass(item.status)">{{ item.status }}</span>
                  <span class="level-tag" v-if="item.level">级别: {{ item.level }}</span>
                  <span :class="getTypeTagClass(item.type)">
                     <i :class="getTypeIcon(item.type)"></i> {{ getTypeName(item.type) }}
                  </span>
                </div>
                <div class="alert-content" v-html="formatAlertMessage(item.wellId, item.message, item.type)"></div>
              </div>
              <div class="alert-action">
                <button class="btn-tiny" style="background: #8e44ad;" @click="openAlertEdit(item)"><i class="fa-solid fa-pen"></i> 处置</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <Transition name="fade">
      <div class="modal-overlay" v-if="showAlertDialog">
        <div class="modal-content" style="width: 500px;">
          <h3><i class="fa-solid fa-pen-to-square"></i> 编辑异常处理状态</h3>
          <div class="form-group-row">
            <div class="form-group"><label>关联对象</label><input type="text" v-model="alertForm.wellId"></div>
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
            <div class="form-group"><label>处理意见/工单备注</label><textarea v-model="alertForm.warningContent" rows="3" class="custom-textarea"></textarea></div>
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
import { ref, onMounted, onUnmounted, nextTick, watch, computed } from 'vue'
import request from '../utils/request'
import * as echarts from 'echarts'
import { globalRealtimeData, startGlobalEngine } from '../utils/realtimeStore'

const wellList = ref([])
const groupList = ref([])
const totalWells = ref(0)
const oilWells = ref(0)
const waterWells = ref(0)

const unhandledAnomalies = ref(0)
const unhandledGroups = ref(0) // 🌟 变更为井组异常统计

const lineChartRef = ref(null)
const groupChartRef = ref(null)
const toastList = ref([])

const globalAlerts = ref([])
// 🌟 清理了无用的 type 1，只保留 0(单井) 和 2(井组) 🌟
const singleWellAlerts = computed(() => globalAlerts.value.filter(alert => alert.type === 0))
const groupWellAlerts = computed(() => globalAlerts.value.filter(alert => alert.type === 2))

let lineChart = null
let groupChart = null
let alertTimer = null

const selectedWellId = ref('')
const selectedWellType = ref('')
const selectedWellStatus = ref('正常')

const selectedGroupId = ref('')
const currentGroupData = ref(null)

const showAlertDialog = ref(false)
const alertForm = ref({
  id: null, wellId: '', warningLevel: '一般', status: '未处理', warningContent: ''
})

// 🌟 清理样式映射函数，移除对 type 1 的判断 🌟
const getAlertCardClass = (type) => type === 2 ? 'alert-type-group' : 'alert-type-anomaly'
const getTypeTagClass = (type) => type === 2 ? 'type-tag tag-group' : 'type-tag tag-anomaly'
const getTypeName = (type) => type === 2 ? '井组系统预警' : '单井专家诊断'
const getTypeIcon = (type) => type === 2 ? 'fa-solid fa-network-wired' : 'fa-solid fa-triangle-exclamation'
const formatAlertMessage = (wellId, msg, type) => {
  let color = type === 2 ? '#9b59b6' : '#e53e3e'
  return `<strong style="color: ${color};">[${wellId}]</strong> ${msg}`
}
const getStatusClass = (status) => {
  if (status === '已处理') return 'status-tag bg-success'
  if (status === '处理中') return 'status-tag bg-warning'
  return 'status-tag bg-danger'
}

const openAlertEdit = (item) => {
  alertForm.value = { id: item.id, wellId: item.wellId, warningLevel: item.level || '一般', status: item.status || '未处理', warningContent: item.message }
  showAlertDialog.value = true
}

const saveAlertData = async () => {
  try {
    await request.put('/api/warning/update', alertForm.value)
    showAlertDialog.value = false
    fetchRealAlerts()
  } catch (error) { alert("保存失败，请检查网络或后端接口！") }
}

const fetchRealAlerts = async () => {
  try {
    const res = await request.get('/api/warning/list')
    const rawList = res.data || []

    // 🌟 更新顶部卡片的数据源 🌟
    unhandledAnomalies.value = rawList.filter(l => l.warningType === 0 && l.status === '未处理').length
    unhandledGroups.value = rawList.filter(l => l.warningType === 2 && l.status === '未处理').length

    globalAlerts.value = rawList.slice(0, 80).map(log => {
      const d = new Date(log.createTime)
      const timeStr = [d.getHours(), d.getMinutes(), d.getSeconds()].map(n => n.toString().padStart(2, '0')).join(':')
      return { id: log.id, time: timeStr, wellId: log.wellId, message: log.warningContent, level: log.warningLevel || '一般', status: log.status || '未处理', type: log.warningType || 0 }
    })
  } catch (error) { console.error("获取日志失败", error) }
}

const fetchGroupData = async () => {
  try {
    const res = await request.get('/api/group/analysis')
    let dataList = res.code === 200 ? res.data : (res.data?.data || res || [])
    groupList.value = dataList || []

    if (groupList.value.length > 0 && !selectedGroupId.value) {
      selectedGroupId.value = groupList.value[0].centerWaterWell
      switchGroup()
    } else if (selectedGroupId.value) {
      currentGroupData.value = groupList.value.find(g => g.centerWaterWell === selectedGroupId.value)
      if (groupChart) renderGroupTopology() // 保持图表与数据同步刷新
    }
  } catch (error) { console.error("获取井组数据失败:", error) }
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

    if (wellList.value.length > 0) {
      selectedWellId.value = wellList.value[0].wellId
      selectedWellType.value = wellList.value[0].wellType
      selectedWellStatus.value = wellList.value[0].status
    }

    fetchRealAlerts()
    fetchGroupData()

    await nextTick()
    if (selectedWellStatus.value === '正常') initLineChart()
    if (groupList.value.length > 0) initGroupChart()
  } catch (error) { console.error('获取数据失败:', error) }
}

const switchWell = () => {
  const targetWell = wellList.value.find(w => w.wellId === selectedWellId.value)
  if (targetWell) {
    selectedWellType.value = targetWell.wellType
    selectedWellStatus.value = targetWell.status
    if (targetWell.status === '正常') {
      nextTick(() => {
        if (!lineChart) initLineChart()
        else { lineChart.resize(); updateChartOption() }
      })
    }
  }
}

const switchGroup = () => {
  currentGroupData.value = groupList.value.find(g => g.centerWaterWell === selectedGroupId.value)
  if (currentGroupData.value) {
    nextTick(() => {
      if (!groupChart) initGroupChart()
      else { groupChart.resize(); renderGroupTopology() }
    })
  }
}

// =====================================
// 单井折线图逻辑
// =====================================
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
    grid: { left: '8%', right: '8%', bottom: '5%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: data.time },
    yAxis: [
      { type: 'value', name: '压力(MPa)', position: 'left', min: 0, max: 25, splitLine: { lineStyle: { type: 'dashed' } } },
      { type: 'value', name: flowName, position: 'right', min: 0, max: 150, splitLine: { show: false } }
    ],
    graphic: wellData.isOnline ? [] : [{
      type: 'text', left: 'center', top: 'center', z: 100,
      style: { fill: '#94a3b8', text: '⚠ 等待传感器信号接入...', font: 'bold 20px sans-serif' }
    }],
    series: [
      { name: '压力', type: 'line', yAxisIndex: 0, smooth: false, symbol: 'circle', symbolSize: 6, itemStyle: { color: '#e74c3c' }, data: data.pressure, connectNulls: false },
      { name: flowName, type: 'line', yAxisIndex: 1, smooth: false, symbol: 'rect', symbolSize: 6, itemStyle: { color: flowColor }, lineStyle: { type: 'dashed' }, data: data.flow, connectNulls: false }
    ]
  })
}

// =====================================
// 井组拓扑图逻辑 (流动虚线 + Tooltip)
// =====================================
const initGroupChart = () => {
  if (!groupChartRef.value) return
  groupChart = echarts.init(groupChartRef.value)
  renderGroupTopology()
}

const renderGroupTopology = () => {
  if (!groupChart || !currentGroupData.value) return
  const group = currentGroupData.value

  const centerCoord = [50, 50]
  const radius = 35
  const scatterData = []
  const linesData = []

  // 中心水井
  scatterData.push({
    name: group.centerWaterWell,
    value: centerCoord,
    symbolSize: 65,
    itemStyle: { color: '#3498db', shadowBlur: 15, shadowColor: 'rgba(52,152,219,0.6)' },
    label: { show: true, position: 'bottom', formatter: '{b}\n(水井)', color: '#2c3e50', fontWeight: 'bold' }
  })

  const connectedWells = group.connectedOilWells || []
  const oilCount = connectedWells.length

  if (oilCount > 0) {
    const angleStep = (2 * Math.PI) / oilCount
    connectedWells.forEach((oilWellId, index) => {
      const angle = index * angleStep
      const x = 50 + radius * Math.cos(angle)
      const y = 50 + radius * Math.sin(angle)
      const targetCoord = [x, y]

      scatterData.push({
        name: oilWellId,
        value: targetCoord,
        symbolSize: 45,
        itemStyle: { color: '#e67e22', shadowBlur: 8, shadowColor: 'rgba(230,126,34,0.5)' },
        label: { show: true, position: 'bottom', formatter: '{b}\n(油井)', color: '#555' }
      })

      // 虚线连线坐标
      linesData.push({
        coords: [centerCoord, targetCoord]
      })
    })
  }

  groupChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#cbd5e1',
      textStyle: { color: '#2c3e50' },
      formatter: function (params) {
        if(params.seriesType !== 'scatter') return '';
        const wellId = params.name;
        const rt = globalRealtimeData[wellId];

        let html = `<div style="font-weight:bold; border-bottom:1px solid #e2e8f0; padding-bottom:5px; margin-bottom:5px;">井号: ${wellId}</div>`;
        if (!rt) return html + `<span style="color:#e74c3c;">暂无台账数据</span>`;
        if (!rt.isOnline) return html + `<span style="color:#e74c3c;">🔴 传感器离线/停机</span>`;

        if (rt.wellType === '水井') {
          html += `瞬时注水量: <b style="color:#3498db">${rt.injectRate}</b> m³/d<br/>`;
          html += `当前压力: <b style="color:#e74c3c">${rt.injectPress}</b> MPa`;
        } else {
          let oil = parseFloat(rt.liquid) * (1 - parseFloat(rt.waterCut) / 100);
          html += `产液量: <b style="color:#e67e22">${rt.liquid}</b> t/d<br/>`;
          html += `产油量: <b style="color:#27ae60">${oil.toFixed(2)}</b> t/d<br/>`;
          html += `含水率: <b style="color:#3498db">${rt.waterCut}</b> %<br/>`;
          html += `井口压力: <b style="color:#e74c3c">${rt.pressure}</b> MPa`;
        }
        return html;
      }
    },
    xAxis: { show: false, min: 0, max: 100 },
    yAxis: { show: false, min: 0, max: 100 },
    series: [
      {
        type: 'scatter',
        coordinateSystem: 'cartesian2d',
        data: scatterData,
        zlevel: 1
      },
      {
        type: 'lines',
        coordinateSystem: 'cartesian2d',
        data: linesData,
        zlevel: 2,
        effect: {
          show: true,
          period: 4,
          trailLength: 0.2,
          color: '#00d2d3',
          symbolSize: 8,
          symbol: 'arrow'
        },
        lineStyle: {
          color: '#3498db',
          width: 2,
          type: 'dashed',
          curveness: 0.15,
          opacity: 0.5
        }
      }
    ]
  })
}

watch(() => globalRealtimeData[selectedWellId.value]?.history?.time, () => {
  if (selectedWellStatus.value === '正常') updateChartOption()
}, { deep: true })

onMounted(() => {
  fetchDataAndRender()
  window.addEventListener('resize', () => {
    if(lineChart) lineChart.resize();
    if(groupChart) groupChart.resize();
  })
  alertTimer = setInterval(() => {
    fetchRealAlerts();
    fetchGroupData();
  }, 5000)
})

onUnmounted(() => {
  if (alertTimer) clearInterval(alertTimer)
})
</script>

<style scoped>
.relative-panel { position: relative; overflow-x: hidden;}
.panel { background-color: #f4f7f6; padding: 20px; min-height: calc(100vh - 112px); box-sizing: border-box; }
.panel-title { margin-top: 0; margin-bottom: 20px; font-size: 20px; color: var(--primary-color); padding-bottom: 12px; }

.alert-toast-container { position: absolute; top: 20px; right: 20px; z-index: 9999; display: flex; flex-direction: column; gap: 12px; pointer-events: none; }
.alert-toast { background: rgba(231, 76, 60, 0.95); color: white; padding: 14px 24px; border-radius: 8px; box-shadow: 0 6px 16px rgba(231, 76, 60, 0.3); font-size: 14px; font-weight: 500; animation: slideIn 0.4s forwards; }
@keyframes slideIn { 0% { transform: translateX(120%); opacity: 0; } 100% { transform: translateX(0); opacity: 1; } }

.data-cards { display: flex; gap: 20px; margin-bottom: 20px; flex-wrap: wrap; }
.card { flex: 1; min-width: 200px; display: flex; align-items: center; padding: 16px 20px; border-radius: 10px; color: white; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.card-icon { font-size: 32px; margin-right: 16px; opacity: 0.8; }
.card-info p { margin: 0 0 5px 0; font-size: 13px; opacity: 0.9; }
.card-info h3 { margin: 0; font-size: 24px; font-weight: bold; }
.unit { font-size: 12px; font-weight: normal; opacity: 0.8; }

.card-total { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.card-oil { background: linear-gradient(135deg, #2af598 0%, #009efd 100%); }
.card-danger { background: linear-gradient(135deg, #ff0844 0%, #ffb199 100%); }
/* 🌟 微调了卡片渐变色，使其稍微偏向紫橙色以匹配井组主题 */
.card-warning { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }

.bottom-layout-rows {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.row-wrapper {
  display: flex;
  gap: 20px;
  height: 420px;
}

.global-alert-panel {
  background: white;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.alert-header { background: #fff5f5; padding: 15px 20px; border-bottom: 1px solid #fed7d7; display: flex; justify-content: space-between; align-items: center;}
.alert-header h3 { margin: 0; font-size: 16px; color: #c53030; }
.alert-count { font-size: 12px; background: #c53030; color: white; padding: 2px 8px; border-radius: 10px;}
.alert-list { flex: 1; overflow-y: auto; padding: 10px; background: #fafafa; }
.alert-list::-webkit-scrollbar { width: 6px; }
.alert-list::-webkit-scrollbar-thumb { background-color: #cbd5e1; border-radius: 10px; }
.empty-alert { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #7f8c8d; font-size: 14px;}
.alert-item { position: relative; display: flex; justify-content: space-between; align-items: center; background: white; padding: 12px 15px; margin-bottom: 10px; border-radius: 4px; box-shadow: 0 2px 5px rgba(0,0,0,0.05); animation: fadeIn 0.5s; transition: 0.2s;}
.alert-item:hover { transform: translateX(2px); box-shadow: 0 4px 8px rgba(0,0,0,0.1); }
.alert-type-anomaly { border-left: 4px solid #e53e3e; }
.alert-type-risk { border-left: 4px solid #e67e22; background: #fffaf0;}
.alert-type-group { border-left: 4px solid #9b59b6; background: #fdf4ff;}
.alert-main { flex: 1; min-width: 0; }
.alert-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; flex-wrap: wrap;}
.alert-time { font-size: 12px; color: #a0aec0; }
.alert-content { font-size: 13px; color: #2d3748; line-height: 1.4; word-break: break-all;}
.status-tag, .level-tag { padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: bold; color: white; }
.bg-success { background-color: #27ae60; }
.bg-warning { background-color: #f39c12; color: #fff; }
.bg-danger { background-color: #e74c3c; }
.level-tag { background-color: #95a5a6; }
.type-tag { padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: bold; color: white; margin-left: auto; }
.tag-anomaly { background-color: #e53e3e; }
.tag-risk { background-color: #e67e22; }
.tag-group { background-color: #9b59b6; }
.alert-action { opacity: 0; transition: opacity 0.2s; padding-left: 10px; }
.alert-item:hover .alert-action { opacity: 1; }
.btn-tiny { background: #3498db; color: white; border: none; padding: 5px 10px; font-size: 12px; border-radius: 4px; cursor: pointer; display: flex; align-items: center; gap: 4px; transition: 0.2s; }
.btn-tiny:hover { background: #2980b9; transform: scale(1.05); }

.monitor-section {
  background: white;
  border-radius: 10px;
  padding: 20px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}
.monitor-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; padding-bottom: 10px; border-bottom: 2px dashed #cbd5e1;}
.monitor-header h3 { margin: 0; font-size: 16px; color: var(--primary-color);}
.well-selector { display: flex; align-items: center; font-size: 13px; font-weight: bold;}
.well-selector select { margin-left: 10px; padding: 6px 10px; border: 1px solid #bdc3c7; border-radius: 6px; outline: none; font-size: 13px; cursor: pointer;}
.line-chart-container { flex: 1; width: 100%; }

.offline-display-box { flex: 1; display: flex; align-items: center; justify-content: center; background: #fafafa; border-radius: 8px; border: 1px dashed #cbd5e1; }
.offline-content { text-align: center; color: #94a3b8; }
.icon-pulse-wrapper { display: inline-flex; align-items: center; justify-content: center; width: 60px; height: 60px; border-radius: 50%; background: #fee2e2; color: #ef4444; font-size: 24px; margin-bottom: 15px; animation: gentle-pulse 2s infinite; }
@keyframes gentle-pulse { 0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); } 70% { box-shadow: 0 0 0 15px rgba(239, 68, 68, 0); } 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); } }
.offline-content h4 { margin: 0 0 8px 0; color: #ef4444; font-size: 18px; }
.offline-content p { margin: 0; font-size: 13px; }

.group-stats-bar { display: flex; justify-content: space-around; align-items: center; background: #f8fafc; padding: 8px 15px; border-bottom: 1px solid #e2e8f0; margin-bottom: 10px; border-radius: 6px;}
.stat-item { display: flex; flex-direction: column; align-items: center; }
.stat-item span { font-size: 11px; color: #64748b; margin-bottom: 4px; font-weight: bold;}
.stat-item b { font-size: 14px; color: #2c3e50; }
.stat-item small { font-size: 11px; color: #94a3b8; font-weight: normal; }
.stat-item.highlight span { color: #e67e22; }
.stat-item.highlight strong { font-size: 16px; color: #d35400; font-weight: 900; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }

/* --- 弹窗样式 --- */
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