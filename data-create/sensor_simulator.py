import requests
import time
import random

# --- 配置区 ---
BASE_URL = "http://localhost:8080/api/well"
UPLOAD_URL = f"{BASE_URL}/sensor/upload"
LIST_URL = f"{BASE_URL}/list-with-data"
FREQUENCY = 5

# 🌟 核心配置：异常触发概率 (0.01 = 1% 的概率)
# 如果你想频繁看到异常，可以改成 0.05 (5%) 或者 0.1 (10%)
ANOMALY_RATE = 0.005 

def start_smart_simulation():
    print("🚀 智能传感器网关已启动，开始动态监听井位台账...")
    print(f"🎲 自动随机故障注入已开启 (故障率: {ANOMALY_RATE * 100}%)...\n")
    
    while True:
        try:
            response = requests.get(LIST_URL, timeout=5)
            well_list = response.json() 
        except Exception as e:
            print(f"⚠️ 无法连接服务器，5秒后重试... ({e})")
            time.sleep(5)
            continue

        timestamp = time.strftime('%H:%M:%S')
        print(f"\n--- [{timestamp}] 实时状态扫描与上报 ---")
        
        for item in well_list:
            well = item.get('info', item) 
            well_id = well.get('wellId')
            
            if well.get('status') != '正常':
                print(f"⏸️  井位 [{well_id}] 处于【{well.get('status')}】状态，已休眠。")
                continue

            is_oil = well_id.startswith('Y')
            payload = {"wellId": well_id}
            flip = random.uniform(0.98, 1.02)
            
            # 🎲 核心逻辑：掷骰子！生成 0.0 到 1.0 之间的随机数
            is_anomaly = random.random() < ANOMALY_RATE
            
            if is_oil:
                if is_anomaly:
                    # 💣 异常数据 (油井)：油嘴堵塞/管线憋压 -> 产量断崖下降，压力飙升
                    liq = round(random.uniform(0.0, 5.0), 2)
                    wc = 100.0
                    press = round(random.uniform(25.0, 32.0), 2)
                    print(f"🚨🚨 [概率触发] 油井 {well_id} 发生严重异常 (憋压/停喷)！")
                else:
                    # ✅ 正常数据
                    liq = round(45.0 * flip, 2)
                    wc = round(75.0 * random.uniform(0.99, 1.01), 1)
                    press = round(12.0 * flip, 2)
                    
                payload.update({
                    "liquidVol": liq,
                    "waterCut": wc,
                    "pressure": press,
                    "oilVol": round(liq * (1 - wc/100), 2)
                })
            else:
                if is_anomaly:
                    # 💣 异常数据 (水井)：管线破裂/穿孔 -> 注水压力骤降，注水量异常
                    inj = round(random.uniform(0.0, 10.0), 2)
                    press = round(random.uniform(0.0, 5.0), 2)
                    print(f"🚨🚨 [概率触发] 水井 {well_id} 发生严重异常 (管线失压)！")
                else:
                    # ✅ 正常数据
                    inj = round(100.0 * flip, 2)
                    press = round(18.0 * flip, 2)
                    
                payload.update({
                    "injectVol": inj,
                    "pressure": press
                })

            try:
                res = requests.post(UPLOAD_URL, json=payload, timeout=3)
                val = payload.get('liquidVol') if is_oil else payload.get('injectVol')
                
                if is_anomaly:
                    print(f"❌ [{well_id}] 异常上报成功 | 压力: {payload.get('pressure')} MPa | 产出/注水: {val}")
                else:
                    print(f"✅ [{well_id}] 正常上报成功 | 压力: {payload.get('pressure')} MPa")
            except:
                print(f"⚠️ [{well_id}] 连接中断")
        
        time.sleep(FREQUENCY)

if __name__ == "__main__":
    start_smart_simulation()