import requests
import time
import random
import datetime

# --- 配置区 ---
BASE_URL = "http://localhost:8080/api/well"
UPLOAD_URL = f"{BASE_URL}/sensor/upload"
LIST_URL = f"{BASE_URL}/list-with-data"
FREQUENCY = 5

# 🌟 核心配置：异常触发概率 (0.005 = 0.5% 的概率)
ANOMALY_RATE = 0.005 

def start_smart_simulation():
    print("==================================================")
    print("🚀 智能传感器网关已启动，开始动态监听井位台账...")
    print(f"🎲 自动随机故障注入已开启 (故障率: {ANOMALY_RATE * 100}%)...\n")
    print("==================================================")
    
    while True:
        try:
            response = requests.get(LIST_URL, timeout=5)
            well_list = response.json() 
        except Exception as e:
            print(f"⚠️ 无法连接服务器，5秒后重试... ({e})")
            time.sleep(5)
            continue

        timestamp = datetime.datetime.now().strftime('%H:%M:%S')
        print(f"\n--- [{timestamp}] 实时状态扫描与上报 ---")
        
        for item in well_list:
            well = item.get('info', item) 
            well_id = well.get('wellId')
            
            if well.get('status') != '正常':
                print(f"⏸️  井位 [{well_id}] 处于【{well.get('status')}】状态，已休眠。")
                continue

            is_oil = well_id.startswith('Y')
            payload = {"wellId": well_id}
            
            # 基础波动系数：模拟真实传感器 ±2% 的正常物理抖动
            flip = random.uniform(0.98, 1.02)
            is_anomaly = random.random() < ANOMALY_RATE
            
            if is_oil:
                if is_anomaly:
                    # 💣 异常数据 (油井)
                    liq = round(random.uniform(0.0, 5.0), 2)
                    oil = round(random.uniform(0.0, liq), 2)
                    wc = 100.0
                    press = round(random.uniform(25.0, 32.0), 2)
                    print(f"🚨🚨 [概率触发] 油井 {well_id} 发生严重异常 (憋压/停喷)！")
                else:
                    # ✅ 正常数据：产液 46 左右，产油 23 左右
                    liq = round(46.0 * flip, 2)
                    oil = round(23.0 * random.uniform(0.98, 1.02), 2)
                    
                    if oil > liq:
                        oil = liq
                        
                    wc = round(((liq - oil) / liq) * 100.0, 1)
                    # 🌟 核心修改：油井正常压力设为 12.5 左右
                    press = round(12.5 * flip, 2)
                    
                payload.update({
                    "liquidVol": liq,
                    "waterCut": wc,
                    "pressure": press,
                    "oilVol": oil,
                    # 防弹补丁：给油井也补上水井专属字段，防止数据库报错
                    "injectVol": 0.0  
                })
            else:
                if is_anomaly:
                    # 💣 异常数据 (水井)
                    inj = round(random.uniform(0.0, 10.0), 2)
                    press = round(random.uniform(0.0, 5.0), 2)
                    print(f"🚨🚨 [概率触发] 水井 {well_id} 发生严重异常 (管线失压)！")
                else:
                    # ✅ 正常数据：注水 124 左右，压力 18.5 左右
                    inj = round(124.0 * flip, 2)
                    press = round(18.5 * flip, 2)
                    
                payload.update({
                    "injectVol": inj,
                    "pressure": press,
                    # 🌟 防弹补丁：给水井强行塞入产液和产油字段，骗过数据库的非空校验！
                    "liquidVol": 0.0,  
                    "oilVol": 0.0,
                    "waterCut": 0.0
                })

            try:
                res = requests.post(UPLOAD_URL, json=payload, timeout=3)
                
                # 为了控制台看着直观，打印一下关键数据
                if is_oil:
                    desc = f"产液:{payload['liquidVol']}t 产油:{payload['oilVol']}t 含水:{payload['waterCut']}%"
                else:
                    desc = f"注水:{payload['injectVol']}m³"
                    
                if is_anomaly:
                    print(f"❌ [{well_id}] 异常上报 | 压力: {payload.get('pressure')} MPa | {desc}")
                else:
                    print(f"✅ [{well_id}] 正常上报 | 压力: {payload.get('pressure')} MPa | {desc}")
            except:
                print(f"⚠️ [{well_id}] 连接中断")
        
        time.sleep(FREQUENCY)

if __name__ == "__main__":
    start_smart_simulation()