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
          <button class="btn btn-sm btn-edit" @click="openEditDialog(item)"><i class="fa-solid fa-pen"></i> 编辑台账/关系</button>
          <button class="btn btn-sm btn-delete" @click="deleteWell(item.info.wellId)"><i class="fa-solid fa-trash"></i> 删除</button>
        </td>
      </tr>
      </tbody>
    </table>

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

          <div class="section-title"><i class="fa-solid fa-link"></i> 2. 井组注采关系</div>
          <div class="form-group relation-group">
            <label>
              <small v-if="formData.wellType === '油井'">请勾选向这口油井注水的水井：</small>
              <small v-else>请勾选这口水井注向的油井：</small>
            </label>
            <div class="checkbox-list">
              <div v-if="availableTargetWells.length === 0" class="no-data-msg">暂无可绑定的对象</div>
              <label v-for="target in availableTargetWells" :key="target.wellId" class="checkbox-item">
                <input type="checkbox" :value="target.wellId" v-model="formData.targetWellIds">
                <span class="well-name">{{ target.wellId }}</span>
                <span class="well-type-mini">({{ target.wellType }})</span>
              </label>
            </div>
          </div>

          <div class="section-title"><i class="fa-solid fa-gauge-high"></i> 3. 生产动态概览</div>
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
            <button class="btn btn-primary" @click="saveWellData">更新台账与关系</button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '../utils/request'

// 🌟 引入全局心脏，彻底抛弃死板的 item.today
import { globalRealtimeData, startGlobalEngine } from '../utils/realtimeStore'

const wellDataList = ref([])
const relationList = ref([])
const showDialog = ref(false)
const isEdit = ref(false)
const fileInput = ref(null)

const formData = ref({
  wellId: '', wellType: '油井', status: '正常', longitude: '', latitude: '',
  targetWellIds: []
})

const availableTargetWells = computed(() => {
  return wellDataList.value
      .map(item => item.info)
      .filter(w => w.wellType !== formData.value.wellType)
})

const handleWellTypeChange = () => {
  formData.value.targetWellIds = []
}

const fetchData = async () => {
  try {
    const res = await request.get('/api/well/list-with-data')
    wellDataList.value = res.data

    // 🌟 将纯净的台账列表送入全局引擎
    const cleanList = wellDataList.value.map(item => item.info ? item.info : item)
    startGlobalEngine(cleanList)

    const resRels = await request.get('/api/well/relations')
    relationList.value = resRels.data
  } catch (error) {
    console.error("数据加载失败", error)
  }
}

const openAddDialog = () => {
  isEdit.value = false
  formData.value = {
    wellId: '', wellType: '油井', status: '正常', longitude: '116.495', latitude: '38.515',
    targetWellIds: []
  }
  showDialog.value = true
}

const openEditDialog = (item) => {
  isEdit.value = true
  const well = item.info
  let bindedIds = []
  if (well.wellType === '水井') {
    bindedIds = relationList.value.filter(r => r.waterWellId === well.wellId).map(r => r.oilWellId)
  } else {
    bindedIds = relationList.value.filter(r => r.oilWellId === well.wellId).map(r => r.waterWellId)
  }
  formData.value = { ...well, targetWellIds: bindedIds }
  showDialog.value = true
}

const saveWellData = async () => {
  try {
    if (!isEdit.value) await request.post('/api/well/add', formData.value)
    else await request.put('/api/well/update', formData.value)
    await request.post(`/api/well/bindRelations?wellId=${formData.value.wellId}&wellType=${formData.value.wellType}`, formData.value.targetWellIds)
    alert('台账及注采关系更新成功！')
    showDialog.value = false
    fetchData()
  } catch (error) {
    alert('操作失败，请确认井号是否唯一且格式正确。')
  }
}

const deleteWell = async (id) => {
  if (confirm(`确定要删除 [${id}] 吗？这会同步删除其所有注采关系和历史生产记录！`)) {
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

onMounted(() => {
  fetchData()
  // 台账刷新间隔拉长到 10 秒（实时数据由全局心脏每5秒单独接管）
  setInterval(fetchData, 10000)
})
</script>

<style scoped>
/* 此处代码完全保留你要求的样式版本 */
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
@keyframes pulse-red {
  0% { opacity: 1; }
  50% { opacity: 0.3; }
  100% { opacity: 1; }
}

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
</style>