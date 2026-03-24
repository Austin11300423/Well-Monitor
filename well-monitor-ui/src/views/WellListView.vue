<template>
  <div class="panel">
    <div class="header-actions">
      <h2 class="panel-title"><i class="fa-solid fa-list-ul"></i> 油水井台账与动态管理</h2>
      <div class="action-buttons">
        <button class="btn btn-secondary" @click="downloadTemplate"><i class="fa-solid fa-download"></i> 下载模板</button>
        <button class="btn btn-secondary" @click="triggerUpload"><i class="fa-solid fa-upload"></i> 导入Excel</button>
        <input type="file" ref="fileInput" @change="handleFileUpload" style="display: none" accept=".xlsx, .xls">
        <button class="btn btn-primary" @click="openAddDialog"><i class="fa-solid fa-plus"></i> 新增井位</button>
      </div>
    </div>

    <table class="data-table">
      <thead>
      <tr>
        <th>井号</th><th>井别</th><th>状态</th><th>经度</th><th>纬度</th><th>今日实时均值 (传感器)</th><th>操作</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="item in wellDataList" :key="item.info.wellId">
        <td><strong>{{ item.info.wellId }}</strong></td>
        <td><span :class="item.info.wellType === '油井' ? 'tag tag-oil' : 'tag tag-water'">{{ item.info.wellType }}</span></td>
        <td><span :class="item.info.status === '正常' ? 'text-success' : 'text-danger'">{{ item.info.status }}</span></td>
        <td>{{ item.info.longitude }}</td>
        <td>{{ item.info.latitude }}</td>
        <td>
          <div v-if="item.info.status === '正常'">
            <div v-if="globalRealtimeData[item.info.wellId]?.isOnline" class="prod-info-mini">
              <div class="realtime-tag"><i class="fa-solid fa-circle-dot pulse"></i> 实时同步中</div>
              <template v-if="item.info.wellType === '油井'">
                产液: <b>{{ globalRealtimeData[item.info.wellId].liquid }}</b> /
                产油: <b>{{ (parseFloat(globalRealtimeData[item.info.wellId].liquid) * (1 - parseFloat(globalRealtimeData[item.info.wellId].waterCut) / 100)).toFixed(1) }}</b> /
                含水率: <b>{{ globalRealtimeData[item.info.wellId].waterCut }}%</b> <br/>
                压力: <b>{{ globalRealtimeData[item.info.wellId].pressure }} MPa</b>
              </template>
              <template v-else>
                注水: <b>{{ globalRealtimeData[item.info.wellId].injectRate }}</b> /
                压力: <b>{{ globalRealtimeData[item.info.wellId].injectPress }} MPa</b>
              </template>
            </div>
            <div v-else class="text-muted" style="font-size: 12px;">
              <i class="fa-solid fa-spinner fa-spin"></i> 等待信号接入...
            </div>
          </div>
          <div v-else class="text-danger" style="font-size: 12px; font-weight: bold;">
            <i class="fa-solid fa-power-off"></i> 设备已停机，数据采集关闭
          </div>
        </td>
        <td>
          <button class="btn btn-sm btn-edit" @click="openEditDialog(item)"><i class="fa-solid fa-pen"></i> 编辑台账</button>
          <button class="btn btn-sm btn-delete" @click="deleteWell(item.info.wellId)"><i class="fa-solid fa-trash"></i> 删除</button>
        </td>
      </tr>
      </tbody>
    </table>

    <div class="divider">
      <div class="divider-line"></div>
      <div class="divider-text"><i class="fa-solid fa-link"></i> 井组连通关系与注水拓扑感知 <i class="fa-solid fa-link"></i></div>
      <div class="divider-line"></div>
    </div>

    <div class="group-section">
      <div class="group-sidebar">
        <div class="sidebar-header" style="display: flex; justify-content: space-between; align-items: center;">
          <h3 style="margin: 0;"><i class="fa-solid fa-network-wired"></i> 全区井组列表</h3>
          <button class="btn-tiny" @click="openGroupDialog"><i class="fa-solid fa-pen-to-square"></i> 编辑井组</button>
        </div>
        <div class="group-list">
          <div v-for="group in groupList" :key="group.centerWaterWell"
               :class="['group-item', { active: selectedGroupId === group.centerWaterWell }]"
               @click="selectGroup(group)">
            <div class="group-icon"><i class="fa-solid fa-layer-group"></i></div>
            <div class="group-info">
              <h4>{{ group.groupName }}</h4>
              <p>中心井: {{ group.centerWaterWell }} | 关联: {{ group.connectedOilWells ? group.connectedOilWells.length : 0 }} 口</p>
            </div>
            <i class="fa-solid fa-chevron-right arrow-icon"></i>
          </div>
          <div v-if="groupList.length === 0" class="empty-group">
            <p>暂无井组连通关系，请点击上方按钮创建</p>
          </div>
        </div>
      </div>

      <div class="group-display">
        <div class="empty-display" v-if="!currentGroup">
          <div class="empty-icon"><i class="fa-solid fa-satellite-dish"></i></div>
          <h4>暂未选择展示井组</h4>
          <p>请在左侧列表点击选中井组，或点击"编辑井组"新建拓扑关系</p>
        </div>

        <div class="display-header" v-if="currentGroup">
          <div class="header-title">
            <h3>{{ currentGroup.groupName }} <span class="badge">动态拓扑图</span></h3>
            <p class="sub-desc">地下水驱动力流向与物理连通关系映射</p>
          </div>
          <div class="header-stats">
            <div class="stat-box">
              <label>注水总量 (m³/d)</label>
              <span>{{ currentGroup.totalInjectVol }}</span>
            </div>
            <div class="stat-box">
              <label>产液总量 (t/d)</label>
              <span>{{ currentGroup.totalLiquidVol.toFixed(1) }}</span>
            </div>
            <div class="stat-box highlight-box">
              <label>综合注采比</label>
              <strong>{{ currentGroup.injectionProductionRatio }}</strong>
            </div>
          </div>
        </div>

        <div class="topology-chart" ref="topologyChartRef" v-show="currentGroup"></div>
      </div>
    </div>

    <Transition name="fade">
      <div class="modal-overlay" v-if="showDialog">
        <div class="modal-content">
          <h3>{{ isEdit ? '编辑井位台账' : '新增井位台账' }}</h3>

          <div class="section-title"><i class="fa-solid fa-info-circle"></i> 1. 基础台账信息</div>
          <div class="form-group-row">
            <div class="form-group">
              <label>井号 (唯一ID)</label>
              <input type="text" v-model="formData.wellId" :disabled="isEdit" placeholder="例如: X-001">
            </div>
            <div class="form-group">
              <label>井别</label>
              <select v-model="formData.wellType" @change="handleWellTypeChange">
                <option value="油井">油井</option><option value="水井">水井</option>
              </select>
            </div>
            <div class="form-group">
              <label>运行状态</label>
              <select v-model="formData.status"><option value="正常">正常</option><option value="停机">停机</option></select>
            </div>
          </div>
          <div class="form-group-row">
            <div class="form-group"><label>经度</label><input type="text" v-model="formData.longitude"></div>
            <div class="form-group"><label>纬度</label><input type="text" v-model="formData.latitude"></div>
          </div>

          <div class="section-title"><i class="fa-solid fa-gauge-high"></i> 2. 生产动态概览</div>
          <div class="prod-readonly-panel">
            <div class="readonly-msg"><i class="fa-solid fa-robot"></i> 生产数据由后端模拟器实时注入，手动编辑已禁用</div>

            <div v-if="formData.status === '正常'">
              <div v-if="globalRealtimeData[formData.wellId]?.isOnline" class="prod-grid">
                <div class="grid-item"><span>压力</span><b>{{ globalRealtimeData[formData.wellId].pressure }} MPa</b></div>
                <div v-if="formData.wellType === '油井'" class="grid-item"><span>产液</span><b>{{ globalRealtimeData[formData.wellId].liquid }} t/d</b></div>
                <div v-if="formData.wellType === '油井'" class="grid-item"><span>含水</span><b>{{ globalRealtimeData[formData.wellId].waterCut }} %</b></div>
                <div v-if="formData.wellType === '水井'" class="grid-item"><span>注水</span><b>{{ globalRealtimeData[formData.wellId].injectRate }} m³/d</b></div>
              </div>
              <div v-else class="no-data-msg">当前无实时生产信号</div>
            </div>
            <div v-else style="text-align: center; color: #e74c3c; padding: 10px;">
              <i class="fa-solid fa-ban"></i> 井位已停机，传感器上报已关闭
            </div>
          </div>

          <div class="modal-actions">
            <button class="btn btn-secondary" @click="showDialog = false">取消</button>
            <button class="btn btn-primary" @click="saveWellData">更新台账</button>
          </div>
        </div>
      </div>
    </Transition>

    <Transition name="fade">
      <div class="modal-overlay" v-if="showGroupDialog">
        <div class="modal-content" style="width: 550px;">
          <h3><i class="fa-solid fa-diagram-project"></i> 统一编辑井组注采关系</h3>
          <p class="readonly-msg" style="margin-top: -10px;">以水井为中心建立拓扑，系统将自动计算综合注采比</p>

          <div class="form-group" style="margin-bottom: 15px;">
            <label>1. 请选择中心注水井</label>
            <select v-model="groupForm.waterWellId" @change="loadGroupDetails" style="width: 100%; padding: 8px; border-radius: 6px; border: 1px solid #bdc3c7;">
              <option value="" disabled>-- 请选择水井 --</option>
              <option v-for="w in waterWells" :key="w.wellId" :value="w.wellId">{{ w.wellId }}</option>
            </select>
          </div>

          <div class="form-group" style="margin-bottom: 15px;" v-if="groupForm.waterWellId">
            <label>2. 井组名称 (起个好听的名字)</label>
            <input type="text" v-model="groupForm.groupName" placeholder="例如: 东区高产井组 (不填则系统自动命名)" style="width: 100%; padding: 8px; border-radius: 6px; border: 1px solid #bdc3c7;">
          </div>

          <div class="form-group relation-group" v-if="groupForm.waterWellId">
            <label>3. 请勾选需要绑定的采油井</label>
            <div class="checkbox-list">
              <div v-if="oilWells.length === 0" class="no-data-msg">暂无可绑定的油井</div>
              <label v-for="oil in oilWells" :key="oil.wellId" class="checkbox-item">
                <input type="checkbox" :value="oil.wellId" v-model="groupForm.targetOilIds">
                <span class="well-name">{{ oil.wellId }}</span>
              </label>
            </div>
          </div>

          <div class="modal-actions">
            <button class="btn btn-secondary" @click="showGroupDialog = false">取消</button>
            <button class="btn btn-primary" @click="saveGroupData" :disabled="!groupForm.waterWellId">应用关系同步</button>
          </div>
        </div>
      </div>
    </Transition>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import request from '../utils/request'
import * as echarts from 'echarts'

import { globalRealtimeData, startGlobalEngine } from '../utils/realtimeStore'

const wellDataList = ref([])
const relationList = ref([])
const showDialog = ref(false)
const isEdit = ref(false)
const fileInput = ref(null)

const formData = ref({
  wellId: '', wellType: '油井', status: '正常', longitude: '', latitude: ''
})

const groupList = ref([])
const selectedGroupId = ref('')
const currentGroup = ref(null)
const topologyChartRef = ref(null)
let topologyChart = null

const showGroupDialog = ref(false)
const groupForm = ref({
  waterWellId: '',
  groupName: '',
  targetOilIds: []
})

const waterWells = computed(() => wellDataList.value.map(w => w.info).filter(w => w.wellType === '水井'))
const oilWells = computed(() => wellDataList.value.map(w => w.info).filter(w => w.wellType === '油井'))

const handleWellTypeChange = () => { }

const fetchData = async () => {
  try {
    const res = await request.get('/api/well/list-with-data')
    wellDataList.value = res.data

    const cleanList = wellDataList.value.map(item => item.info ? item.info : item)
    startGlobalEngine(cleanList)

    const resRels = await request.get('/api/well/relations')
    relationList.value = resRels.data

    fetchGroups()
  } catch (error) {
    console.error("数据加载失败", error)
  }
}

const openAddDialog = () => {
  isEdit.value = false
  formData.value = { wellId: '', wellType: '油井', status: '正常', longitude: '116.495', latitude: '38.515' }
  showDialog.value = true
}

const openEditDialog = (item) => {
  isEdit.value = true
  formData.value = { ...item.info }
  showDialog.value = true
}

const saveWellData = async () => {
  try {
    if (!isEdit.value) await request.post('/api/well/add', formData.value)
    else await request.put('/api/well/update', formData.value)
    alert('台账信息更新成功！')
    showDialog.value = false
    fetchData()
  } catch (error) {
    alert('操作失败，请确认井号是否唯一且格式正确。')
  }
}

const deleteWell = async (id) => {
  if (confirm(`确定要删除 [${id}] 吗？这会同步删除其历史生产记录！`)) {
    try {
      await request.delete(`/api/well/delete/${id}`)
      fetchData()
    } catch (error) { alert("删除失败") }
  }
}

const downloadTemplate = () => { window.open('http://localhost:8080/api/well/template') }
const triggerUpload = () => { fileInput.value.click() }
const handleFileUpload = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  const uploadData = new FormData()
  uploadData.append('file', file)
  try {
    await request.post('/api/well/import', uploadData)
    alert('导入成功！')
    fetchData()
  } catch (error) { alert('导入失败') } finally { event.target.value = '' }
}

// 🌟🌟 核心修复区 1：多层防弹脱壳解析数据 🌟🌟
const fetchGroups = async () => {
  try {
    const res = await request.get('/api/group/analysis')

    // 强制扒开 Axios 可能包在外面的各种拦截器外衣，直到拿到真实的 List 数组
    let dataList = []
    if (res.code === 200) {
      dataList = res.data
    } else if (res.data && res.data.code === 200) {
      dataList = res.data.data
    } else if (Array.isArray(res)) {
      dataList = res
    } else if (Array.isArray(res.data)) {
      dataList = res.data
    }

    groupList.value = dataList || []

    if (groupList.value.length > 0 && !selectedGroupId.value) {
      selectGroup(groupList.value[0])
    } else if (currentGroup.value) {
      const updatedGroup = groupList.value.find(g => g.centerWaterWell === selectedGroupId.value)
      if (updatedGroup) {
        selectGroup(updatedGroup)
      } else {
        currentGroup.value = null
        topologyChart?.clear()
      }
    } else {
      currentGroup.value = null
      topologyChart?.clear()
    }
  } catch (error) {
    console.error('获取井组拓扑失败:', error)
  }
}

const selectGroup = async (group) => {
  selectedGroupId.value = group.centerWaterWell
  currentGroup.value = group
  await nextTick()
  renderTopologyAnimation(group)
}

const openGroupDialog = () => {
  groupForm.value = { waterWellId: '', groupName: '', targetOilIds: [] }
  showGroupDialog.value = true
}

const loadGroupDetails = () => {
  if (!groupForm.value.waterWellId) return
  const existGroup = groupList.value.find(g => g.centerWaterWell === groupForm.value.waterWellId)
  if (existGroup) {
    groupForm.value.groupName = existGroup.groupName
    groupForm.value.targetOilIds = existGroup.connectedOilWells || []
  } else {
    groupForm.value.groupName = ''
    groupForm.value.targetOilIds = []
  }
}

const saveGroupData = async () => {
  if (!groupForm.value.waterWellId) return
  try {
    const payload = {
      waterWellId: groupForm.value.waterWellId,
      groupName: groupForm.value.groupName || (groupForm.value.waterWellId + ' 注采井组'),
      oilWellIds: groupForm.value.targetOilIds
    }

    await request.post('/api/group/save', payload)
    alert('全区井组连通关系与名称同步成功！')
    showGroupDialog.value = false

    selectedGroupId.value = groupForm.value.waterWellId
    fetchData()
  } catch (error) {
    alert('同步失败，请检查网络')
  }
}

// 🌟🌟 核心修复区 2：兜底孤岛水井，防止除以零卡死渲染 🌟🌟
const renderTopologyAnimation = (group) => {
  if (!topologyChartRef.value) return
  if (!topologyChart) {
    topologyChart = echarts.init(topologyChartRef.value)
  }
  topologyChart.clear()

  const centerCoord = [50, 50]
  const radius = 35
  const scatterData = []
  const linesData = []

  // 1. 无论如何，先把中心水井画出来
  scatterData.push({
    name: group.centerWaterWell,
    value: centerCoord,
    symbolSize: 70,
    itemStyle: { color: '#3498db', shadowBlur: 20, shadowColor: 'rgba(52, 152, 219, 0.6)' },
    label: { show: true, position: 'bottom', formatter: '{b}\n(中心水井)', color: '#2c3e50', fontWeight: 'bold' },
    z: 10
  })

  // 2. 只有当确实关联了油井时，才去执行三角函数计算，否则跳过
  const oilCount = group.connectedOilWells ? group.connectedOilWells.length : 0
  if (oilCount > 0) {
    const angleStep = (2 * Math.PI) / oilCount

    group.connectedOilWells.forEach((oilWellId, index) => {
      const angle = index * angleStep
      const x = 50 + radius * Math.cos(angle)
      const y = 50 + radius * Math.sin(angle)
      const targetCoord = [x, y]

      scatterData.push({
        name: oilWellId,
        value: targetCoord,
        symbolSize: 50,
        itemStyle: { color: '#e67e22', shadowBlur: 10, shadowColor: 'rgba(230, 126, 34, 0.4)' },
        label: { show: true, position: 'bottom', formatter: '{b}\n(采油井)', color: '#555' },
        z: 10
      })

      linesData.push({
        coords: [centerCoord, targetCoord],
        lineStyle: { color: '#3498db', width: 2, curveness: 0.1, opacity: 0.4 }
      })
    })
  }

  const option = {
    backgroundColor: 'transparent',
    tooltip: { formatter: '{b}' },
    xAxis: { show: false, min: 0, max: 100 },
    yAxis: { show: false, min: 0, max: 100 },
    series: [
      { type: 'scatter', symbol: 'circle', data: scatterData, animationDurationUpdate: 1000 },
      {
        type: 'lines', data: linesData,
        effect: { show: true, period: 3, trailLength: 0.4, color: '#00d2d3', symbolSize: 6, symbol: 'arrow' },
        lineStyle: { color: '#bdc3c7', width: 2, type: 'dashed', curveness: 0.2 },
        z: 1
      }
    ]
  }
  topologyChart.setOption(option)
}

onMounted(() => {
  fetchData()
  setInterval(fetchData, 10000)
  window.addEventListener('resize', () => { topologyChart?.resize() })
})
</script>

<style scoped>
/* ========================================== */
/* 🟢 绝对原汁原味保留：你原本的所有 CSS 样式 */
/* ========================================== */
.panel { background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.04); padding: 24px; min-height: calc(100vh - 112px); box-sizing: border-box; }
.header-actions { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #f0f4f8; padding-bottom: 12px; margin-bottom: 20px;}
.panel-title { margin: 0; font-size: 20px; color: var(--primary-color); }
.action-buttons { display: flex; gap: 10px; }
.btn { padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; font-weight: bold; transition: 0.2s; }
.btn-primary { background: var(--primary-color); color: white; }
.btn-secondary { background: #95a5a6; color: white; }
.btn-sm { padding: 6px 12px; font-size: 12px; margin-right: 8px;}
.btn-edit { background: #3498db; color: white; }
.btn-delete { background: #e74c3c; color: white; }

.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { border: 1px solid #e2e8f0; padding: 12px; text-align: left; font-size: 14px; }
.data-table th { background: #f8fafc; color: #475569; }

.tag { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
.tag-oil { background: #d1fae5; color: #059669; }
.tag-water { background: #e0f2fe; color: #0284c7; }

.realtime-tag { color: #67c23a; font-size: 11px; font-weight: bold; margin-bottom: 4px; display: flex; align-items: center; gap: 4px; }
.prod-info-mini b { color: #2c3e50; }

.pulse { animation: pulse-red 2s infinite; color: #67c23a; }
@keyframes pulse-red { 0% { opacity: 1; } 50% { opacity: 0.3; } 100% { opacity: 1; } }

.prod-readonly-panel { background: #f1f5f9; border-radius: 8px; padding: 15px; border: 1px solid #e2e8f0; }
.readonly-msg { font-size: 12px; color: #64748b; margin-bottom: 10px; border-bottom: 1px solid #cbd5e1; padding-bottom: 8px; }
.prod-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; }
.grid-item { display: flex; flex-direction: column; }
.grid-item span { font-size: 11px; color: #94a3b8; }
.grid-item b { font-size: 16px; color: #1e293b; }

.modal-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-content { background: white; padding: 30px; border-radius: 12px; width: 620px; max-height: 90vh; overflow-y: auto; }
.section-title { font-size: 15px; font-weight: bold; color: var(--primary-color); margin: 20px 0 10px 0; border-left: 4px solid var(--primary-color); padding-left: 8px;}
.form-group-row { display: flex; gap: 15px; margin-bottom: 15px; }
.form-group { flex: 1; display: flex; flex-direction: column; }
.form-group label { font-size: 13px; margin-bottom: 6px; font-weight: bold; }
.form-group input, .form-group select { padding: 8px; border: 1px solid #bdc3c7; border-radius: 6px; }

.checkbox-list { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; background: white; padding: 10px; border: 1px solid #cbd5e1; border-radius: 6px; max-height: 120px; overflow-y: auto;}
.modal-actions { margin-top: 25px; display: flex; justify-content: flex-end; gap: 15px; }

/* ========================================== */
/* 🌟 下半部分：井组专属样式 */
/* ========================================== */
.divider { display: flex; align-items: center; margin: 40px 0 20px 0; }
.divider-line { flex: 1; height: 1px; background: linear-gradient(to right, transparent, #cbd5e1, transparent); }
.divider-text { margin: 0 20px; color: #64748b; font-weight: bold; font-size: 14px; display: flex; align-items: center; gap: 8px; }

.group-section { display: flex; gap: 24px; height: 450px; }

.group-sidebar { flex: 0 0 300px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; display: flex; flex-direction: column; overflow: hidden; }
.sidebar-header { background: #edf2f7; padding: 15px; border-bottom: 1px solid #e2e8f0; }
.sidebar-header h3 { font-size: 15px; color: #2d3748;}

.btn-tiny { background: #e67e22; color: white; border: none; padding: 5px 10px; font-size: 12px; border-radius: 4px; cursor: pointer; transition: 0.2s; display: flex; align-items: center; gap: 4px;}
.btn-tiny:hover { background: #d35400; transform: scale(1.02); }

.group-list { flex: 1; overflow-y: auto; }
.group-item { display: flex; align-items: center; padding: 15px; border-bottom: 1px solid #f1f5f9; cursor: pointer; transition: 0.2s; }
.group-item:hover { background: #f1f5f9; }
.group-item.active { background: #ebf5fb; border-left: 4px solid #3498db; }
.group-icon { width: 40px; height: 40px; border-radius: 8px; background: white; border: 1px solid #cbd5e1; display: flex; align-items: center; justify-content: center; font-size: 18px; color: #3498db; margin-right: 15px; }
.group-item.active .group-icon { background: #3498db; color: white; border-color: #3498db; }
.group-info { flex: 1; }
.group-info h4 { margin: 0 0 5px 0; font-size: 14px; color: #2c3e50; }
.group-info p { margin: 0; font-size: 12px; color: #7f8c8d; }
.arrow-icon { color: #bdc3c7; font-size: 12px; }
.group-item.active .arrow-icon { color: #3498db; }
.empty-group { padding: 30px; text-align: center; color: #94a3b8; font-size: 13px; }

.group-display { flex: 1; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; display: flex; flex-direction: column; position: relative; overflow: hidden; }

.empty-display { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #94a3b8; }
.empty-icon { font-size: 60px; color: #cbd5e1; margin-bottom: 15px; opacity: 0.5; }
.empty-display h4 { margin: 0 0 10px 0; font-size: 18px; color: #64748b;}
.empty-display p { margin: 0; font-size: 14px;}

.display-header { position: absolute; top: 20px; left: 20px; right: 20px; z-index: 10; display: flex; justify-content: space-between; align-items: flex-start; background: rgba(255,255,255,0.85); backdrop-filter: blur(5px); padding: 15px 20px; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); border: 1px solid rgba(226, 232, 240, 0.8); }
.header-title h3 { margin: 0 0 5px 0; font-size: 18px; color: #2c3e50; display: flex; align-items: center; gap: 10px; }
.badge { background: #e74c3c; color: white; font-size: 11px; padding: 2px 6px; border-radius: 4px; font-weight: normal; animation: pulse-red-badge 2s infinite; }
@keyframes pulse-red-badge { 0% { box-shadow: 0 0 0 0 rgba(231, 76, 60, 0.4); } 70% { box-shadow: 0 0 0 6px rgba(231, 76, 60, 0); } 100% { box-shadow: 0 0 0 0 rgba(231, 76, 60, 0); } }
.sub-desc { margin: 0; font-size: 12px; color: #7f8c8d; }

.header-stats { display: flex; gap: 20px; }
.stat-box { display: flex; flex-direction: column; align-items: flex-end; }
.stat-box label { font-size: 11px; color: #7f8c8d; text-transform: uppercase; margin-bottom: 2px; }
.stat-box span { font-size: 16px; font-weight: bold; color: #2c3e50; }
.highlight-box { background: #fdf2e9; padding: 5px 15px; border-radius: 6px; border: 1px solid #fae5d3; }
.highlight-box label { color: #e67e22; }
.highlight-box strong { font-size: 20px; color: #d35400; }

.topology-chart { flex: 1; width: 100%; min-height: 400px; background: radial-gradient(circle at center, #ffffff 0%, #f1f5f9 100%); }
</style>