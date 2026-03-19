import { reactive } from 'vue'
import request from './request'

export const globalRealtimeData = reactive({})
let globalTimer = null
const MAX_POINTS = 30

export const startGlobalEngine = (wellList) => {
    // 🌟 1. 动态扩容：不管从哪个页面传进来的井，统统注册进全局字典
    wellList.forEach(well => {
        if (!globalRealtimeData[well.wellId]) {
            globalRealtimeData[well.wellId] = {
                wellType: well.wellType, // 把井别存下来，供内部循环使用
                liquid: '0.0', pressure: '0.00', waterCut: '0.0', load: '0',
                injectRate: '0.0', injectPress: '0.00',
                isOnline: false,
                lastRecordStr: '',
                lastReceiveTime: 0,
                fetchCount: 0, // 🌟 记录请求次数，用来消灭第一次加载的误判
                history: { time: [], pressure: [], flow: [] }
            }
        }
    })

    // 如果定时器已经在跑了，加完新井就可以直接撤了，不用开多个定时器
    if (globalTimer) return

    const refreshFromDatabase = async () => {
        try {
            const nowMs = Date.now();
            // 🌟 2. 遍历全局字典里的所有井，而不是写死在闭包里的 wellList
            const activeWellIds = Object.keys(globalRealtimeData);

            for (const wellId of activeWellIds) {
                const data = globalRealtimeData[wellId]
                data.fetchCount += 1

                // 🌟 3. 终极缓存杀手：加一个 _t 时间戳，强迫浏览器每次必须去后端拿最新数据！
                const res = await request.get('/api/well/prod/history', {
                    params: { wellId: wellId, _t: nowMs }
                })

                const historyList = res.data || []
                const isOil = data.wellType === '油井'

                if (historyList.length > 0) {
                    const latest = historyList[historyList.length - 1]
                    const currentStr = JSON.stringify(latest)

                    // 🌟 4. 智能在线判定逻辑
                    if (data.fetchCount === 1) {
                        // 第一次请求：不确定传感器到底开没开，先按规矩显示【等待信号接入】
                        data.lastRecordStr = currentStr
                        data.lastReceiveTime = 0
                        data.isOnline = false
                    } else {
                        if (currentStr !== data.lastRecordStr) {
                            // 只要数据因为随机扰动发生了变化，说明传感器绝对【在线】！
                            data.lastRecordStr = currentStr
                            data.lastReceiveTime = nowMs
                            data.isOnline = true
                        } else {
                            // 数据没变，如果超过15秒没变，说明传感器【断开】了
                            if (nowMs - data.lastReceiveTime > 15000) {
                                data.isOnline = false
                            }
                        }
                    }

                    // 更新面板上显示的实时数字
                    data.pressure = (latest.pressure || 0).toFixed(2)
                    data.liquid = (latest.liquidVol || 0).toFixed(2)
                    data.injectRate = (latest.injectVol || 0).toFixed(2)
                    data.injectPress = (latest.pressure || 0).toFixed(2)
                    data.waterCut = (latest.waterCut || 0).toFixed(1)
                } else {
                    data.isOnline = false
                }

                // 🌟 5. 不管在线还是离线，时间轴必须坚挺地走下去！
                const last30 = historyList.slice(-MAX_POINTS)
                const timeArray = []
                const pressArray = []
                const flowArray = []

                for (let i = 0; i < MAX_POINTS; i++) {
                    const dt = new Date(nowMs - (MAX_POINTS - 1 - i) * 5000);
                    timeArray.push(`${dt.getHours().toString().padStart(2,'0')}:${dt.getMinutes().toString().padStart(2,'0')}:${dt.getSeconds().toString().padStart(2,'0')}`)

                    // 只有【在线】且【有数据】才画折线
                    if (data.isOnline && i >= MAX_POINTS - last30.length) {
                        const d = last30[i - (MAX_POINTS - last30.length)]
                        pressArray.push((d.pressure || 0).toFixed(2))
                        flowArray.push(isOil ? (d.liquidVol || 0).toFixed(2) : (d.injectVol || 0).toFixed(2))
                    } else {
                        // 离线时，塞入 null，图表就会被清空
                        pressArray.push(null)
                        flowArray.push(null)
                    }
                }

                data.history.time = timeArray
                data.history.pressure = pressArray
                data.history.flow = flowArray
            }
        } catch (e) {
            console.error("同步传感器数据失败:", e)
        }
    }

    refreshFromDatabase()
    globalTimer = setInterval(refreshFromDatabase, 5000)
}