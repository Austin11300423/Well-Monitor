<template>
  <div class="panel map-panel">
    <h2 class="panel-title"><i class="fa-solid fa-earth-asia"></i> 油藏区块实时监测图</h2>

    <div class="main-content">
      <div class="map-wrapper" ref="mapContainer">

        <div class="search-box">
          <div class="search-input-wrapper">
            <i class="fa-solid fa-magnifying-glass search-icon"></i>
            <input
                type="text"
                v-model="searchQuery"
                placeholder="请输入井号搜索..."
                @focus="showDropdown = true"
                @blur="hideDropdownDelay"
            >
          </div>
          <ul v-if="showDropdown && filteredWells.length > 0" class="search-results">
            <li v-for="w in filteredWells" :key="w.wellId" @click="selectWellFromSearch(w)">
              <span class="result-id">{{ w.wellId }}</span>
              <span :class="w.wellType === '油井' ? 'tag-oil-mini' : 'tag-water-mini'">{{ w.wellType }}</span>
            </li>
          </ul>
          <div v-else-if="showDropdown && searchQuery && filteredWells.length === 0" class="search-results empty-result">
            未找到相关井位
          </div>
        </div>

        <div class="map-legend">
          <h4>图例</h4>
          <div class="legend-item"><span class="legend-color bg-oil"></span> 正常油井</div>
          <div class="legend-item"><span class="legend-color bg-water"></span> 正常水井</div>
          <div class="legend-item"><span class="legend-color bg-danger"></span> 异常/停机井</div>
          <div class="legend-item"><span class="legend-line"></span> 注采连通线</div>
        </div>

        <div class="map-main"></div>
      </div>

      <transition name="slide-fade">
        <div class="info-panel" v-if="selectedWell">
          <div class="panel-header">
            <h3>
              <i :class="selectedWell.wellType === '油井' ? 'fa-solid fa-oil-well text-oil' : 'fa-solid fa-droplet text-water'"></i>
              [{{ selectedWell.wellId }}] 实时动态
            </h3>
            <button class="close-btn" @click="closePanel"><i class="fa-solid fa-xmark"></i></button>
          </div>

          <div class="panel-body">
            <div class="status-badge" :class="selectedWell.status === '正常' ? 'bg-success' : 'bg-danger'">
              状态: {{ selectedWell.status }}
            </div>

            <div v-if="selectedWell.status === '停机'" class="offline-warning">
              <i class="fa-solid fa-link-slash"></i> 设备已停机，传感器链路断开，当前显示归零数据。
            </div>

            <div v-if="selectedWell.wellType === '油井'" class="data-grid" :class="{'disabled-grid': selectedWell.status === '停机'}">
              <div class="data-box">
                <span class="label">当前产液量</span>
                <span class="value oil-val">{{ realTimeData.liquid || '--' }} <small>t/d</small></span>
              </div>
              <div class="data-box">
                <span class="label">井口压力</span>
                <span class="value">{{ realTimeData.pressure || '--' }} <small>MPa</small></span>
              </div>
              <div class="data-box">
                <span class="label">实时含水率</span>
                <span class="value water-val">{{ realTimeData.waterCut || '--' }} <small>%</small></span>
              </div>
              <div class="data-box">
                <span class="label">载荷极值</span>
                <span class="value">{{ realTimeData.load || '--' }} <small>kN</small></span>
              </div>
            </div>

            <div v-if="selectedWell.wellType === '水井'" class="data-grid" :class="{'disabled-grid': selectedWell.status === '停机'}">
              <div class="data-box">
                <span class="label">瞬时注水量</span>
                <span class="value water-val">{{ realTimeData.injectRate || '--' }} <small>m³/h</small></span>
              </div>
              <div class="data-box">
                <span class="label">注水压力</span>
                <span class="value danger-val">{{ realTimeData.injectPress || '--' }} <small>MPa</small></span>
              </div>
              <div class="data-box full-width">
                <span class="label"><i class="fa-solid fa-link"></i> 对应受效油井</span>
                <div class="target-wells">
                  <span v-for="t in selectedWell.targets" :key="t.wellId" class="target-tag">
                    {{ t.wellId }}
                  </span>
                  <span v-if="!selectedWell.targets || selectedWell.targets.length === 0" class="target-tag text-muted">暂无绑定关系</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, nextTick, computed } from 'vue'
import request from '../utils/request'
import L from 'leaflet'

import { globalRealtimeData, startGlobalEngine } from '../utils/realtimeStore'

const wellList = ref([])
const relationList = ref([])

let myMap = null
const mapContainer = ref(null)
let resizeObserver = null

const selectedWell = ref(null)
let dynamicLines = []

const realTimeData = computed(() => {
  if (!selectedWell.value) return {}
  return globalRealtimeData[selectedWell.value.wellId] || {}
})

const searchQuery = ref('')
const showDropdown = ref(false)

const filteredWells = computed(() => {
  if (!searchQuery.value) return []
  const keyword = searchQuery.value.toLowerCase()
  return wellList.value.filter(w => w.wellId.toLowerCase().includes(keyword))
})

const hideDropdownDelay = () => { setTimeout(() => { showDropdown.value = false }, 200) }

const selectWellFromSearch = (well) => {
  searchQuery.value = well.wellId
  showDropdown.value = false

  // 🌟 核心修复：把字符串强制转成带小数的数字，再做数学加法
  const lat = parseFloat(well.latitude)
  const lng = parseFloat(well.longitude)

  // 将地图中心平滑移动过去（经度 + 0.01 完美避开右侧面板遮挡）
  myMap.setView([lat, lng ], 16, { animate: true })

  openWellPanel(well)
}

const initMap = () => {
  if (myMap) myMap.remove()
  myMap = L.map(mapContainer.value.querySelector('.map-main')).setView([38.515, 116.495], 13)

  L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
    subdomains: ["1", "2", "3", "4"],
    attribution: '&copy; 高德地图'
  }).addTo(myMap)

  L.control.scale({ maxWidth: 150, metric: true, imperial: false, position: 'bottomleft' }).addTo(myMap)

  resizeObserver = new ResizeObserver(() => { if (myMap) myMap.invalidateSize() })
  if (mapContainer.value) resizeObserver.observe(mapContainer.value)
}

const renderMarkers = () => {
  wellList.value.forEach(well => {
    let bgColorClass = well.status !== '正常' ? 'marker-danger' : (well.wellType === '油井' ? 'marker-oil' : 'marker-water')
    const iconHtml = `<div class="marker-icon-wrapper ${bgColorClass}"><i class="fa-solid ${well.wellType === '油井' ? 'fa-oil-well' : 'fa-droplet'}"></i></div>`
    const customIcon = L.divIcon({ className: 'custom-div-icon', html: iconHtml, iconSize: [30, 30], iconAnchor: [15, 15] })

    // 🌟 在这里加一句：强制转换用于初始渲染
    const initLat = parseFloat(well.latitude)
    const initLng = parseFloat(well.longitude)

    const marker = L.marker([initLat, initLng], { icon: customIcon }).addTo(myMap)
    marker.bindTooltip(`${well.wellId}`, {direction: 'top', offset: [0, -15]})

    marker.on('click', () => {
      openWellPanel(well)
      // 🌟 在点击事件里：强制转换并偏移
      const clickLat = parseFloat(well.latitude)
      const clickLng = parseFloat(well.longitude)
      myMap.setView([clickLat, clickLng + 0.01], 14, { animate: true })
    })
  })
}

const openWellPanel = (well) => {
  selectedWell.value = well
  clearLines()

  if (well.wellType === '水井') {
    const realRelations = relationList.value.filter(r => r.waterWellId === well.wellId)
    well.targets = realRelations.map(rel => wellList.value.find(w => w.wellId === rel.oilWellId)).filter(w => w !== undefined)

    well.targets.forEach(target => {
      const line = L.polyline([[well.latitude, well.longitude], [target.latitude, target.longitude]], {
        color: '#00a8ff', weight: 3, dashArray: '10, 15', className: 'flowing-line'
      }).addTo(myMap)
      dynamicLines.push(line)
    })
  }
}

const clearLines = () => {
  dynamicLines.forEach(line => myMap.removeLayer(line))
  dynamicLines = []
}

const closePanel = () => {
  selectedWell.value = null
  clearLines()
}

onMounted(async () => {
  await nextTick()
  initMap()
  try {
    // 🌟 核心修复：同样使用智能解构，剥离出纯净的 info
    const resWells = await request.get('/api/well/list-with-data')
    wellList.value = resWells.data.map(item => item.info ? item.info : item)

    const resRels = await request.get('/api/well/relations')
    relationList.value = resRels.data

    renderMarkers()
    startGlobalEngine(wellList.value)

  } catch (error) {
    console.error('获取地图或关系数据失败:', error)
  }
})

onUnmounted(() => {
  if (myMap) { myMap.remove(); myMap = null }
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
/* 样式保留你的原汁原味 */
.map-panel { display: flex; flex-direction: column; height: calc(100vh - 112px); background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.04); padding: 24px; box-sizing: border-box; }
.panel-title { flex-shrink: 0; margin-top: 0; margin-bottom: 16px; font-size: 20px; color: var(--primary-color); border-bottom: 2px solid #f0f4f8; padding-bottom: 12px; }
.main-content { display: flex; flex: 1; min-height: 0; gap: 16px; position: relative; }
.map-wrapper { flex: 1; height: 100%; position: relative; border-radius: 8px; overflow: hidden; border: 1px solid var(--border-color); z-index: 1;}
.map-main { height: 100%; width: 100%; }

.search-box { position: absolute; top: 15px; left: 60px; z-index: 1000; width: 280px; }
.search-input-wrapper { display: flex; align-items: center; background: white; border-radius: 6px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden; border: 1px solid #e2e8f0; }
.search-icon { color: #94a3b8; padding: 0 12px; font-size: 14px;}
.search-input-wrapper input { flex: 1; border: none; padding: 10px 10px 10px 0; outline: none; font-size: 14px; color: #334155; }
.search-results { position: absolute; top: 45px; left: 0; right: 0; background: white; margin: 0; padding: 0; list-style: none; border-radius: 6px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); border: 1px solid #e2e8f0; max-height: 250px; overflow-y: auto; }
.search-results li { padding: 10px 15px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; border-bottom: 1px solid #f1f5f9; transition: background 0.2s; }
.search-results li:hover { background: #f8fafc; }
.result-id { font-weight: bold; color: #2c3e50; font-size: 14px;}
.tag-oil-mini { background: #d1fae5; color: #059669; font-size: 11px; padding: 2px 6px; border-radius: 4px; }
.tag-water-mini { background: #e0f2fe; color: #0284c7; font-size: 11px; padding: 2px 6px; border-radius: 4px; }
.empty-result { padding: 12px 15px; text-align: center; color: #94a3b8; font-size: 13px; }

.map-legend { position: absolute; bottom: 20px; right: 20px; z-index: 1000; background: rgba(255, 255, 255, 0.9); padding: 15px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); border: 1px solid #e2e8f0; backdrop-filter: blur(5px); }
.map-legend h4 { margin: 0 0 10px 0; font-size: 14px; color: #2c3e50; border-bottom: 1px solid #e2e8f0; padding-bottom: 5px; text-align: center;}
.legend-item { display: flex; align-items: center; font-size: 12px; color: #475569; margin-bottom: 8px; }
.legend-item:last-child { margin-bottom: 0; }
.legend-color { width: 14px; height: 14px; border-radius: 50%; display: inline-block; margin-right: 8px; border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.2); }
.bg-oil { background: #27ae60; }
.bg-water { background: #3498db; }
.bg-danger { background: #e74c3c; }
.legend-line { width: 14px; height: 0; border-top: 2px dashed #00a8ff; display: inline-block; margin-right: 8px; }

.info-panel { width: 300px; flex-shrink: 0; background: #f8fafc; border-radius: 8px; border: 1px solid #e2e8f0; display: flex; flex-direction: column; overflow: hidden; box-shadow: -4px 0 15px rgba(0,0,0,0.05); }
.panel-header { display: flex; justify-content: space-between; align-items: center; padding: 15px 20px; background: white; border-bottom: 1px solid #e2e8f0; }
.panel-header h3 { margin: 0; font-size: 16px; color: #2c3e50; }
.text-oil { color: #27ae60; }
.text-water { color: #3498db; }
.close-btn { background: none; border: none; font-size: 18px; color: #95a5a6; cursor: pointer; transition: 0.2s;}
.close-btn:hover { color: #e74c3c; transform: scale(1.1); }
.panel-body { padding: 20px; flex: 1; overflow-y: auto; }
.status-badge { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 12px; color: white; margin-bottom: 15px; font-weight: bold;}
.bg-success { background: #27ae60; }
.bg-danger { background: #e74c3c; }

.offline-warning { background-color: #fef2f2; border: 1px dashed #f87171; color: #ef4444; padding: 10px; border-radius: 6px; font-size: 12px; margin-bottom: 15px; font-weight: bold; line-height: 1.5; }
.offline-warning i { margin-right: 5px; }

.data-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; transition: 0.3s; }
.disabled-grid { opacity: 0.5; filter: grayscale(100%); pointer-events: none; }
.data-box { background: white; border: 1px solid #e2e8f0; border-radius: 6px; padding: 12px; display: flex; flex-direction: column; align-items: center; text-align: center; box-shadow: 0 2px 4px rgba(0,0,0,0.02);}
.full-width { grid-column: span 2; align-items: flex-start; text-align: left; }
.data-box .label { font-size: 12px; color: #7f8c8d; margin-bottom: 6px; }
.data-box .value { font-size: 20px; font-weight: bold; color: #2c3e50; }
.data-box .value small { font-size: 12px; color: #95a5a6; font-weight: normal;}

.oil-val { color: #d35400 !important; }
.water-val { color: #2980b9 !important; }
.danger-val { color: #c0392b !important; }

.target-wells { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 8px;}
.target-tag { background: #e0f2fe; color: #0284c7; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; border: 1px solid #bae6fd;}
.text-muted { background: #f1f5f9; color: #94a3b8; border-color: #e2e8f0; }

.slide-fade-enter-active, .slide-fade-leave-active { transition: all 0.3s ease; }
.slide-fade-enter-from, .slide-fade-leave-to { transform: translateX(50px); opacity: 0; }

:deep(.flowing-line) { animation: flowDash 1s linear infinite; }
@keyframes flowDash { from { stroke-dashoffset: 25; } to { stroke-dashoffset: 0; } }

:deep(.custom-div-icon) { background: transparent; border: none; }
:deep(.marker-icon-wrapper) { width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: white; font-size: 14px; box-shadow: 0 4px 10px rgba(0,0,0,0.3); border: 2px solid white; transition: transform 0.2s; cursor: pointer;}
:deep(.marker-icon-wrapper:hover) { transform: scale(1.2); z-index: 1000;}
:deep(.marker-oil) { background: #27ae60; }
:deep(.marker-water) { background: #3498db; }
:deep(.marker-danger) { background: #e74c3c !important; animation: pulse 1.5s infinite; }
@keyframes pulse { 0% { box-shadow: 0 0 0 0 rgba(231, 76, 60, 0.7); } 70% { box-shadow: 0 0 0 10px rgba(231, 76, 60, 0); } 100% { box-shadow: 0 0 0 0 rgba(231, 76, 60, 0); } }
</style>