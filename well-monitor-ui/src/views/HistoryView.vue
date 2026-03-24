<template>
  <div class="panel">
    <h2 class="panel-title"><i class="fa-solid fa-chart-line"></i> 历史生产递减与多算法产量预测</h2>

    <div class="analysis-layout">
      <div class="config-panel">
        <div class="config-section">
          <h3><i class="fa-solid fa-filter"></i> 1. 分析对象</h3>
          <select v-model="selectedWellId" @change="handleWellChange" class="full-select">
            <option v-for="well in wellList" :key="well.wellId" :value="well.wellId">
              {{ well.wellId }} ({{ well.wellType }})
            </option>
          </select>
        </div>

        <div class="config-section">
          <h3><i class="fa-solid fa-calendar-days"></i> 2. 历史数据区间</h3>
          <div class="param-item">
            <label>开始日期</label>
            <input type="date" v-model="startDate" class="date-input">
          </div>
          <div class="param-item">
            <label>结束日期</label>
            <input type="date" v-model="endDate" class="date-input">
          </div>
          <div class="btn-group">
            <button class="query-btn" @click="handleWellChange"><i class="fa-solid fa-magnifying-glass"></i> 区间查询</button>
            <button class="mock-btn" @click="generateMockData"><i class="fa-solid fa-bolt"></i> 生成90天模拟</button>
          </div>
        </div>

        <div v-if="selectedWellType === '油井'" class="config-section arps-config">
          <h3><i class="fa-solid fa-microchip"></i> 3. 智能预测引擎</h3>

          <div class="param-item">
            <label>预测算法</label>
            <select v-model="selectedAlgorithm" @change="calculatePrediction" class="full-select" style="width: 150px;">
              <option value="arps">Arps 递减机理模型</option>
              <option value="gm11">GM(1,1) 灰色预测</option>
              <option value="lstm">LSTM 深度神经网络</option>
            </select>
          </div>

          <div class="param-item">
            <label>预测未来时长 (天)</label>
            <input type="number" v-model.number="predictDays" min="1" max="10000" @change="calculatePrediction" class="num-input">
          </div>

          <div v-if="selectedAlgorithm === 'arps'">
            <div class="param-item slider-item">
              <div class="label-row">
                <label>初始日递减率 Di</label>
                <span class="value">{{ (arpsDi * 100).toFixed(2) }} %</span>
              </div>
              <input type="range" v-model.number="arpsDi" min="0.0001" max="0.05" step="0.0001" @input="calculatePrediction">
            </div>
            <div class="param-item slider-item">
              <div class="label-row">
                <label>递减指数 b</label>
                <span class="value">{{ arpsB.toFixed(2) }}</span>
              </div>
              <input type="range" v-model.number="arpsB" min="0" max="1" step="0.01" @input="calculatePrediction">
            </div>
            <div class="arps-help-box">
              <h4><i class="fa-solid fa-lightbulb"></i> Arps 参数调校</h4>
              <ul>
                <li><strong>Di</strong>：控制初期下降速度。前期降得慢请调大。</li>
                <li><strong>b</strong>：控制中后期曲率。后期跌太深请调大。</li>
              </ul>
            </div>
          </div>

          <div v-if="selectedAlgorithm === 'gm11'">
            <div class="arps-help-box" style="background: #f0fdf4; border-color: #bbf7d0;">
              <h4 style="color: #166534;"><i class="fa-solid fa-circle-nodes"></i> GM(1,1) 真实引擎已接入</h4>
              <ul>
                <li>基于历史真实数据矩阵进行最小二乘解算。</li>
                <li><strong>已接入后端物理机理约束拦截层，保障数据合理性。</strong></li>
              </ul>
            </div>
          </div>

          <div v-if="selectedAlgorithm === 'lstm'">
            <div class="param-item">
              <label>Time Steps (时间步)</label>
              <input type="number" value="7" disabled class="num-input" style="background: #f1f5f9;">
            </div>
            <div class="param-item">
              <label>Epochs (训练轮数)</label>
              <input type="number" value="200" disabled class="num-input" style="background: #f1f5f9;">
            </div>
            <div class="arps-help-box" style="background: #f5f3ff; border-color: #ddd6fe;">
              <h4 style="color: #5b21b6;"><i class="fa-solid fa-network-wired"></i> LSTM 深度学习</h4>
              <ul>
                <li>当前为前端系统模拟演算，后续计划接入 Python 服务。</li>
              </ul>
            </div>
          </div>

        </div>
      </div>

      <div class="right-content">
        <div class="chart-area">
          <button class="btn-reset-view" @click="resetDateRange" title="清除时间筛选与鼠标缩放，恢复完整视图">
            <i class="fa-solid fa-expand"></i> 恢复完整视图
          </button>
          <div class="chart-container" ref="historyChartRef"></div>
        </div>

        <div class="data-table-area" v-if="selectedWellType === '油井' && forecastTableData.length > 0">
          <div class="table-header">
            <h3><i class="fa-solid fa-table-list"></i> 预测数据明细表
              <span class="subtitle">(为保障 Web 渲染性能，表格最多展示前 100 天明细)</span>
            </h3>
            <div class="eur-card">
              <span class="eur-label">未来 {{ predictDays }} 天预估增产油量：</span>
              <span class="eur-value">{{ totalPredictedOil.toFixed(1) }} <small>吨</small></span>
            </div>
          </div>

          <div class="table-wrapper">
            <table class="forecast-table">
              <thead>
              <tr>
                <th>预测日期</th>
                <th>预估产液量 (t/d)</th>
                <th>预估产油量 (t/d)</th>
                <th>预估含水率 (%)</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="(row, index) in forecastTableData.slice(0, 100)" :key="index">
                <td><strong>{{ row.date }}</strong></td>
                <td class="text-liquid">{{ row.predLiquid }}</td>
                <td class="text-oil"><strong>{{ row.predOil }}</strong></td>
                <td class="text-water">{{ row.waterCut }}%</td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import request from '../utils/request'
import * as echarts from 'echarts'

const wellList = ref([])
const selectedWellId = ref('')
const selectedWellType = ref('油井')
const startDate = ref('')
const endDate = ref('')
const historyChartRef = ref(null)
let myChart = null

const selectedAlgorithm = ref('arps')
const algorithmNameMap = {
  arps: 'Arps 递减模型',
  gm11: 'GM(1,1) 灰色预测',
  lstm: 'LSTM 神经网络'
}

const predictDays = ref(30)
const arpsDi = ref(0.005)
const arpsB = ref(0.3)

let baseMonths = [], baseLiquid = [], baseOil = [], baseWaterCut = [], baseInject = [], basePressure = []
let chartMonths = [], chartHistLiquid = [], chartPredLiquid = [], chartHistOil = [], chartPredOil = [], chartWaterCut = []

const forecastTableData = ref([])

const totalPredictedOil = computed(() => {
  return forecastTableData.value.reduce((sum, row) => sum + row.predOil, 0)
})

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const fetchWells = async () => {
  try {
    const res = await request.get('/api/well/list-with-data')
    const rawData = res.data || []
    wellList.value = rawData.map(item => item.info ? item.info : item)
    if (wellList.value.length > 0) {
      selectedWellId.value = wellList.value[0].wellId
      selectedWellType.value = wellList.value[0].wellType
      await nextTick()
      handleWellChange()
    }
  } catch (error) {
    console.error('获取井列表失败:', error)
  }
}

const generateMockData = async () => {
  try {
    if(!confirm("确定要为全区所有井生成过去 90 天的历史衰减数据吗？")) return
    const res = await request.post('/api/well/prod/generate-mock')
    alert(res.data)
    startDate.value = ''; endDate.value = ''; handleWellChange()
  } catch (error) { alert("生成失败") }
}

const resetDateRange = () => {
  startDate.value = ''
  endDate.value = ''
  if (myChart) {
    myChart.dispatchAction({ type: 'dataZoom', start: 0, end: 100 })
    myChart.dispatchAction({ type: 'restore' })
  }
  handleWellChange()
}

const handleWellChange = async () => {
  if (!selectedWellId.value) return

  const targetWell = wellList.value.find(w => w.wellId === selectedWellId.value)
  if (targetWell) selectedWellType.value = targetWell.wellType

  baseMonths = []; baseLiquid = []; baseOil = []; baseWaterCut = []; baseInject = []; basePressure = []

  try {
    const params = { wellId: selectedWellId.value }
    if (startDate.value) params.startDate = startDate.value
    if (endDate.value) params.endDate = endDate.value

    const res = await request.get('/api/well/prod/daily', { params })
    const data = res.data || []

    data.forEach(d => {
      baseMonths.push(formatDate(d.recordDate))
      if (selectedWellType.value === '油井') {
        baseLiquid.push(parseFloat((d.liquidVol || 0).toFixed(1)))
        baseOil.push(parseFloat((d.oilVol || 0).toFixed(1)))
        baseWaterCut.push(parseFloat((d.waterCut || 0).toFixed(1)))
      } else {
        baseInject.push(parseFloat((d.injectVol || 0).toFixed(1)))
        basePressure.push(parseFloat((d.pressure || 0).toFixed(1)))
      }
    })

    calculatePrediction()
  } catch (error) {
    console.error("查询历史数据失败", error)
  }
}

// 核心计算逻辑
const calculatePrediction = async () => {
  chartMonths = [...baseMonths]
  // 性能优化：临时数组脱离 Vue 的响应式劫持
  let tempTableData = []

  if (selectedWellType.value === '水井' || baseLiquid.length === 0) {
    forecastTableData.value = []
    renderChart()
    return
  }

  chartHistLiquid = [...baseLiquid]; chartHistOil = [...baseOil]; chartWaterCut = [...baseWaterCut]
  chartPredLiquid = []; chartPredOil = []

  for (let i = 0; i < baseLiquid.length - 1; i++) {
    chartPredLiquid.push(null); chartPredOil.push(null)
  }

  const lastIdx = baseLiquid.length - 1
  chartPredLiquid.push(baseLiquid[lastIdx]); chartPredOil.push(baseOil[lastIdx])

  const Qi_liq = baseLiquid[lastIdx], Qi_oil = baseOil[lastIdx]
  let lastRealDate = new Date(baseMonths[lastIdx] || new Date())

  // === 分支 1：接入完全由后端约束的真实 GM(1,1) API ===
  if (selectedAlgorithm.value === 'gm11') {
    try {
      const res = await request.get('/api/well/predict/gm11', {
        params: { wellId: selectedWellId.value, days: predictDays.value }
      })

      const serverData = res.data || res;

      if (serverData.code === 200) {
        const predLiqArray = serverData.data.predLiquid;
        const predOilArray = serverData.data.predOil;

        for (let t = 0; t < predictDays.value; t++) {
          let nextDate = new Date(lastRealDate.getTime() + (t + 1) * 24 * 60 * 60 * 1000)
          let formattedDate = formatDate(nextDate)
          chartMonths.push(formattedDate)

          let rawL = Number(predLiqArray[t])
          let rawO = Number(predOilArray[t])

          // 前端只负责格式化渲染，信任后端的物理机理约束结果
          let predL = parseFloat((isNaN(rawL) ? 0 : rawL).toFixed(1))
          let predO = parseFloat((isNaN(rawO) ? 0 : rawO).toFixed(1))
          let wc = parseFloat((predL > 0 ? ((predL - predO) / predL) * 100 : 0).toFixed(1))

          chartPredLiquid.push(predL)
          chartPredOil.push(predO)
          chartWaterCut.push(wc)

          tempTableData.push({ date: formattedDate, predLiquid: predL, predOil: predO, waterCut: wc })
        }
        // 性能优化：一次性给 Vue 的响应式变量赋值，避免频繁 DOM 重绘
        forecastTableData.value = tempTableData;
        renderChart()
      }
    } catch (error) {
      console.error("GM11 后端预测请求失败", error)
    }
    return
  }

  // === 分支 2 & 3：保留本地算力计算 (Arps 与 LSTM 模拟) ===
  for (let t = 1; t <= predictDays.value; t++) {
    let nextDate = new Date(lastRealDate.getTime() + t * 24 * 60 * 60 * 1000)
    let formattedDate = formatDate(nextDate)
    chartMonths.push(formattedDate)

    let Qt_liq = 0, Qt_oil = 0

    if (selectedAlgorithm.value === 'arps') {
      const Di = arpsDi.value, b = arpsB.value
      Qt_liq = b === 0 ? Qi_liq * Math.exp(-Di * t) : Qi_liq * Math.pow((1 + b * Di * t), -1 / b)
      Qt_oil = b === 0 ? Qi_oil * Math.exp(-Di * 1.5 * t) : Qi_oil * Math.pow((1 + b * Di * 1.5 * t), -1 / b)
    }
    else if (selectedAlgorithm.value === 'lstm') {
      const baseDecay = 0.004
      const nonLinearNoiseLiq = Math.sin(t / 3) * 0.5 + Math.cos(t / 7) * 0.3
      const nonLinearNoiseOil = Math.sin(t / 4) * 0.2 + Math.cos(t / 6) * 0.1
      Qt_liq = Qi_liq * Math.exp(-baseDecay * t) + nonLinearNoiseLiq
      Qt_oil = Qi_oil * Math.exp(-baseDecay * 1.3 * t) + nonLinearNoiseOil
      Qt_liq = Math.max(Qt_liq, Qi_liq * 0.1)
      Qt_oil = Math.max(Qt_oil, Qi_oil * 0.05)
    }

    let safeL = isNaN(Qt_liq) ? 0 : Qt_liq
    let safeO = isNaN(Qt_oil) ? 0 : Qt_oil

    let predL = parseFloat(safeL.toFixed(1))
    let predO = parseFloat(safeO.toFixed(1))
    let wc = parseFloat((((predL - predO) / predL) * 100).toFixed(1))
    wc = isNaN(wc) ? 0 : Math.min(100, Math.max(0, wc))

    chartPredLiquid.push(predL)
    chartPredOil.push(predO)
    chartWaterCut.push(wc)

    tempTableData.push({ date: formattedDate, predLiquid: predL, predOil: predO, waterCut: wc })
  }

  forecastTableData.value = tempTableData;
  renderChart()
}

const renderChart = async () => {
  await nextTick()
  if (!historyChartRef.value) return

  if (!myChart) {
    myChart = echarts.init(historyChartRef.value)
  }
  myChart.clear()

  const seriesData = []
  let yAxisConfig = []

  if (selectedWellType.value === '油井') {
    yAxisConfig = [
      { type: 'value', name: '产量 (t/d)', position: 'left', splitLine: { lineStyle: { type: 'dashed', color: '#edf2f7' } } },
      { type: 'value', name: '含水率 (%)', position: 'right', min: 0, max: 100, splitLine: { show: false } }
    ]

    // 动态感知节点规模，当点数大于 100 时关闭 Symbol 圆点，并依赖后方 sampling 的降采样算法
    const shouldShowSymbol = (predictDays.value + baseLiquid.length) <= 100;

    seriesData.push(
        { name: '历史产液量', type: 'bar', yAxisIndex: 0, barWidth: '40%', itemStyle: { color: '#00cec9', borderRadius: [4, 4, 0, 0] }, data: chartHistLiquid },
        { name: '预测产液量', type: 'line', yAxisIndex: 0, smooth: false, showSymbol: shouldShowSymbol, sampling: 'lttb', itemStyle: { color: '#00cec9' }, lineStyle: { width: 3, type: 'dashed' }, data: chartPredLiquid },
        { name: '历史产油量', type: 'line', yAxisIndex: 0, smooth: true, showSymbol: shouldShowSymbol, sampling: 'lttb', itemStyle: { color: '#e17055' }, lineStyle: { width: 3 }, data: chartHistOil },
        { name: '预测产油量', type: 'line', yAxisIndex: 0, smooth: false, showSymbol: shouldShowSymbol, sampling: 'lttb', itemStyle: { color: '#e17055' }, lineStyle: { width: 3, type: 'dashed' }, data: chartPredOil },
        { name: '综合含水率', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: shouldShowSymbol, sampling: 'lttb', itemStyle: { color: '#3498db' }, lineStyle: { width: 2, type: 'dotted' }, data: chartWaterCut }
    )
  } else {
    yAxisConfig = [
      { type: 'value', name: '注水量 (m³/d)', position: 'left', splitLine: { lineStyle: { type: 'dashed', color: '#edf2f7' } } },
      { type: 'value', name: '注水压力 (MPa)', position: 'right', splitLine: { show: false } }
    ]
    seriesData.push(
        { name: '历史注水量', type: 'bar', yAxisIndex: 0, barWidth: '40%', itemStyle: { color: '#3498db', borderRadius: [4, 4, 0, 0] }, data: baseInject },
        { name: '历史注水压力', type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#e74c3c' }, lineStyle: { width: 3 }, data: basePressure }
    )
  }

  myChart.setOption({
    title: { text: `[${selectedWellId.value || '未选择井号'}] ${algorithmNameMap[selectedAlgorithm.value]} 趋势分析`, left: 'center', top: 10 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 50 },
    toolbox: { feature: { dataZoom: { yAxisIndex: 'none' }, restore: {} } },
    dataZoom: [{ type: 'inside', start: 0, end: 100 }, { start: 0, end: 100 }],
    grid: { left: '3%', right: '3%', bottom: '10%', top: 100, containLabel: true },
    xAxis: { type: 'category', boundaryGap: true, data: chartMonths },
    yAxis: yAxisConfig,
    series: seriesData
  })
}

onMounted(() => {
  fetchWells()
  window.addEventListener('resize', () => { myChart?.resize() })
})
</script>

<style scoped>
.panel { background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.04); padding: 24px; min-height: calc(100vh - 112px); box-sizing: border-box; }
.panel-title { margin-top: 0; margin-bottom: 24px; font-size: 20px; color: var(--primary-color); border-bottom: 2px solid #f0f4f8; padding-bottom: 12px; }
.analysis-layout { display: flex; gap: 24px;}
.config-panel { flex: 0 0 320px; display: flex; flex-direction: column; gap: 20px;}
.config-section { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 18px;}
.config-section h3 { margin-top: 0; margin-bottom: 16px; font-size: 15px; color: var(--primary-color); display: flex; align-items: center; gap: 8px;}
.full-select { width: 100%; padding: 10px; border: 1px solid #bdc3c7; border-radius: 6px; font-size: 14px; outline: none;}
.num-input { width: 80px; padding: 8px; border: 1px solid #bdc3c7; border-radius: 6px;}
.param-item { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center; }
.param-item label { font-size: 13px; color: #555; }
.date-input { width: 135px; padding: 6px; border: 1px solid #bdc3c7; border-radius: 6px; font-size: 13px; }

.btn-group { display: flex; gap: 10px; margin-top: 20px; }
.query-btn, .mock-btn { flex: 1; padding: 10px 0; border: none; border-radius: 6px; color: white; cursor: pointer; font-size: 13px; font-weight: bold; transition: 0.2s;}
.query-btn { background: var(--primary-color, #3498db); }
.query-btn:hover { background: #2980b9; }
.mock-btn { background: #e67e22; }
.mock-btn:hover { background: #d35400; }

.slider-item { flex-direction: column; align-items: flex-start;}
.label-row { display: flex; justify-content: space-between; width: 100%; margin-bottom: 6px;}
.slider-item .value { font-size: 13px; color: #e67e22; font-weight: bold;}
.slider-item input[type="range"] { width: 100%; cursor: pointer; accent-color: var(--primary-color); }

.arps-help-box { margin-top: 20px; background: #e0f2fe; padding: 12px; border-radius: 6px; border: 1px solid #bae6fd;}
.arps-help-box h4 { margin: 0 0 8px 0; font-size: 13px; color: #0284c7; display: flex; align-items: center; gap: 6px; }
.arps-help-box ul { margin: 0; padding-left: 20px; font-size: 12px; color: #334155; line-height: 1.6; }
.arps-help-box b { color: #0ea5e9; }

.right-content { flex: 1; display: flex; flex-direction: column; gap: 20px; min-width: 0; }
.chart-area { height: 450px; background: white; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; position: relative; flex-shrink: 0;}
.chart-container { height: 100%; width: 100%; }

.btn-reset-view { position: absolute; top: 15px; right: 20px; z-index: 10; background: white; border: 1px solid #e2e8f0; padding: 6px 12px; border-radius: 20px; font-size: 12px; font-weight: bold; color: #64748b; cursor: pointer; box-shadow: 0 2px 6px rgba(0,0,0,0.05); transition: all 0.2s; display: flex; align-items: center; gap: 6px; }
.btn-reset-view:hover { background: #f1f5f9; color: #3498db; border-color: #3498db; box-shadow: 0 4px 10px rgba(52, 152, 219, 0.15); transform: translateY(-1px); }

.data-table-area { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; flex: 1; display: flex; flex-direction: column;}
.table-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; border-bottom: 2px dashed #cbd5e1; padding-bottom: 15px;}
.table-header h3 { margin: 0; font-size: 16px; color: #2c3e50; }
.subtitle { font-size: 12px; color: #e74c3c; font-weight: normal; margin-left: 8px;}
.eur-card { background: #fff5f5; border: 1px solid #fecaca; padding: 8px 16px; border-radius: 30px; display: flex; align-items: center; gap: 10px; box-shadow: 0 2px 8px rgba(239, 68, 68, 0.1);}
.eur-label { font-size: 13px; color: #ef4444; font-weight: bold;}
.eur-value { font-size: 20px; font-weight: 900; color: #b91c1c;}
.eur-value small { font-size: 12px; font-weight: normal;}

.table-wrapper { flex: 1; overflow-y: auto; max-height: 250px; background: white; border: 1px solid #e2e8f0; border-radius: 6px; }
.table-wrapper::-webkit-scrollbar { width: 6px; }
.table-wrapper::-webkit-scrollbar-thumb { background-color: #cbd5e1; border-radius: 10px; }

.forecast-table { width: 100%; border-collapse: collapse; text-align: center; font-size: 13px;}
.forecast-table th { background: #f1f5f9; color: #475569; padding: 10px; position: sticky; top: 0; z-index: 1; border-bottom: 1px solid #cbd5e1; font-weight: bold;}
.forecast-table td { padding: 10px; border-bottom: 1px solid #f1f5f9; color: #334155; }
.forecast-table tr:hover { background-color: #f8fafc; }

.text-liquid { color: #00cec9; }
.text-oil { color: #e17055; font-size: 14px;}
.text-water { color: #3498db; }
</style>