#!/usr/bin/env python3
"""
智能手表 TCP 客户端模拟器
模拟 1000 个手表向服务器发送健康数据，每批最多 100 个并发连接。

配置说明：
  TOTAL_WATCHES    : 手表总数（覆盖 EMP0001~EMP1000）
  MAX_CONCURRENT   : 最大并发连接数（信号量控制，默认 100）
  INTERVAL_MIN/MAX : 每台手表每轮等待间隔（秒）
  ANOMALY_RATE     : 各健康指标异常概率（默认约 0.0035%）
  SOS/FALL_RATE    : 设备 SOS/跌倒报警概率（每轮）
"""

import socket
import time
import random
import threading
from datetime import datetime, date
import sys
import signal
import os


def env_int(name, default):
    value = os.getenv(name)
    return int(value) if value not in (None, '') else default


def env_float(name, default):
    value = os.getenv(name)
    return float(value) if value not in (None, '') else default

# ─── 全局配置 ─────────────────────────────────────────────────────────────────
SERVER_HOST    = os.getenv('HEALTH_SIM_SERVER_HOST', '127.0.0.1')
SERVER_PORT    = env_int('HEALTH_SIM_SERVER_PORT', 9000)
START_ID       = env_int('HEALTH_SIM_START_ID', 1)        # 起始设备ID
TOTAL_WATCHES  = env_int('HEALTH_SIM_TOTAL_WATCHES', 1000)     # 手表总数（对应 EMP0001~EMP1000）
MAX_CONCURRENT = env_int('HEALTH_SIM_MAX_CONCURRENT', 100)      # 最大并发连接数（每批）
DURATION       = env_int('HEALTH_SIM_DURATION', 0)        # 运行时长(秒)，0=无限运行直到 Ctrl+C
INTERVAL_MIN   = env_float('HEALTH_SIM_INTERVAL_MIN', 300)      # 每轮发送后最短等待(秒)
INTERVAL_MAX   = env_float('HEALTH_SIM_INTERVAL_MAX', 600)      # 每轮发送后最长等待(秒)
# 默认按 1000 台、每 7.5 分钟一轮校准，使所有异常合计约 1 次/小时。
ANOMALY_RATE   = env_float('HEALTH_SIM_ANOMALY_RATE', 0.000035) # 各健康指标异常概率约 0.0035%
SOS_RATE       = env_float('HEALTH_SIM_SOS_RATE', 0.00001)     # 每轮 SOS 概率约 0.001%
FALL_RATE      = env_float('HEALTH_SIM_FALL_RATE', 0.00003)    # 每轮跌倒概率约 0.003%

# ─── 全局控制 ─────────────────────────────────────────────────────────────────
stop_event   = threading.Event()
conn_sema    = threading.Semaphore(MAX_CONCURRENT)  # 并发连接信号量
print_lock   = threading.Lock()
active_count = 0
active_lock  = threading.Lock()


def log(msg):
    with print_lock:
        print(msg)


class WatchSimulator:
    def __init__(self, watch_id):
        self.watch_id = watch_id
        self.imei     = f"3594567800{watch_id:05d}"
        self.sock     = None

        # ── 累计步数（模拟真实手表：当日累计，子夜重置）──
        self._steps_today   = random.randint(0, 2000)   # 初始随机起点
        self._steps_date    = date.today()
        self._steps_lock    = threading.Lock()

    # ──── 步数管理（累计，不随机跳变）─────────────────────────────────────────

    def _get_steps(self):
        with self._steps_lock:
            today = date.today()
            if today != self._steps_date:           # 日期变了，重置
                self._steps_today = 0
                self._steps_date  = today
            increment = random.randint(80, 300)     # 每次发包新增步数
            self._steps_today = min(self._steps_today + increment, 25000)
            return self._steps_today

    # ──── 连接管理 ─────────────────────────────────────────────────────────────

    def connect(self):
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.settimeout(10)
            self.sock.connect((SERVER_HOST, SERVER_PORT))
            return True
        except Exception:
            self.sock = None
            return False

    def send_packet(self, packet):
        try:
            self.sock.sendall(packet.encode('utf-8'))
            self.sock.settimeout(1)
            try:
                self.sock.recv(1024)
            except socket.timeout:
                pass
            return True
        except Exception:
            return False

    def close(self):
        if self.sock:
            try:
                self.sock.shutdown(socket.SHUT_RDWR)
                self.sock.close()
            except Exception:
                pass
            self.sock = None

    # ──── 协议包 ─────────────────────────────────────────────────────────────

    def login(self):
        return self.send_packet(f"IW*AP00*{self.imei}#")

    def send_heartbeat(self):
        """AP03 心跳包：当日累计步数（非随机跳变），卡路里按步数估算"""
        steps    = self._get_steps()
        rollovers = random.randint(5, 30)
        calories = int(steps * random.uniform(0.04, 0.05))
        return self.send_packet(f"IW*AP03*1,{steps},{rollovers},{calories}#")

    def send_health_data_apht(self):
        """APHT：心率,收缩压,舒张压"""
        if random.random() < ANOMALY_RATE:
            hr        = random.choice([random.randint(45, 59), random.randint(101, 130)])
            systolic  = random.randint(141, 165)
            diastolic = random.randint(91, 105)
        else:
            hr        = random.randint(60, 100)
            systolic  = random.randint(110, 135)
            diastolic = random.randint(70, 85)
        return self.send_packet(f"IW*APHT*{hr},{systolic},{diastolic}#")

    def send_health_data_aphp(self):
        """APHP：心率,收缩压,舒张压,血氧,血糖,体温"""
        if random.random() < ANOMALY_RATE:
            hr        = random.choice([random.randint(45, 59), random.randint(101, 130)])
            systolic  = random.randint(141, 165)
            diastolic = random.randint(91, 105)
            oxygen    = random.randint(85, 94)
            temp      = random.choice([
                round(random.uniform(35.5, 35.9), 1),
                round(random.uniform(37.6, 38.5), 1)
            ])
        else:
            hr        = random.randint(60, 100)
            systolic  = random.randint(110, 135)
            diastolic = random.randint(70, 85)
            oxygen    = random.randint(95, 99)
            temp      = round(random.uniform(36.0, 37.5), 1)
        glucose = round(random.uniform(4.0, 6.5), 1)
        return self.send_packet(
            f"IW*APHP*{hr},{systolic},{diastolic},{oxygen},{glucose},{temp},,,,,,,#"
        )

    def send_temperature(self):
        """AP50：体温,电量"""
        if random.random() < ANOMALY_RATE:
            temp = random.choice([
                round(random.uniform(35.5, 35.9), 1),
                round(random.uniform(37.6, 38.5), 1)
            ])
        else:
            temp = round(random.uniform(36.0, 37.5), 1)
        battery = random.randint(20, 100)
        return self.send_packet(f"IW*AP50*{temp},{battery}#")

    def send_sleep_data(self):
        """AP97：深睡(分钟),浅睡(分钟)"""
        if random.random() < ANOMALY_RATE:
            total       = random.randint(200, 359)
            deep_pct    = random.uniform(0.3, 0.5)
            deep_sleep  = int(total * deep_pct)
            light_sleep = total - deep_sleep
        else:
            deep_sleep  = random.randint(120, 200)
            light_sleep = random.randint(240, 340)
        return self.send_packet(f"IW*AP97*{deep_sleep},{light_sleep}#")

    def send_gps_data(self):
        """AP01：GPS 定位（矿区坐标范围）"""
        lat_deg  = 37; lat_min  = round(random.uniform(30.0, 40.0), 4)
        lon_deg  = 112; lon_min = round(random.uniform(0.0,  15.0), 4)
        date_str = datetime.now().strftime('%y%m%d')
        time_str = datetime.now().strftime('%H%M%S')
        latitude  = f"{lat_deg:02d}{lat_min:07.4f}N"
        longitude = f"{lon_deg:03d}{lon_min:07.4f}E"
        speed     = f"{random.randint(0, 10):03d}.{random.randint(0, 9)}"
        direction = f"{random.randint(0, 359):03d}.{random.randint(0, 99):02d}"
        gsm  = f"{random.randint(10, 31):02d}"
        sats = f"{random.randint(4, 12):03d}"
        batt = f"{random.randint(20, 100):03d}"
        status_info = f"{gsm}{sats}{batt}001002"
        mcc = "460"; mnc = "0"
        lac = random.randint(9000, 9999); cid = random.randint(3000, 4000)
        lbs  = f"{mcc},{mnc},{lac},{cid}"
        def rand_mac():
            return ':'.join(f'{random.randint(0,255):02X}' for _ in range(6))
        wifi = f"Mine{random.randint(1,9)}|{rand_mac()}|{random.randint(50,99)}"
        packet = (f"IW*AP01*{date_str}A{latitude}{longitude}{speed}"
                  f"{time_str}{direction}{status_info},{lbs},{wifi}#")
        return self.send_packet(packet)

    # ──── 单轮发送（connect → login → 发 3~5 包 → disconnect）────────────────

    def send_alert(self, alert_code='05'):
        """AP10：报警事件（01=SOS, 05/06=跌倒, 03=脱落）"""
        now = datetime.now()
        date_str = now.strftime('%y%m%d')
        time_str = now.strftime('%H%M%S')
        # 格式：IW*AP10*date,time,lat,lon,speed,dir,alertCode#
        lat = f"3736.{random.randint(1000,9999)}N"
        lon = f"11208.{random.randint(1000,9999)}E"
        return self.send_packet(
            f"IW*AP10*{date_str},{time_str},{lat},{lon},000,000,{alert_code}#"
        )

    def run_one_cycle(self):
        if not self.connect():
            return False
        try:
            if not self.login():
                return False
            time.sleep(2)   # 等服务器完成注册

            # 极低概率触发报警事件；默认与健康指标异常合计约 1 次/小时（1000 台）
            rnd = random.random()
            if rnd < SOS_RATE:
                self.send_alert('01')  # SOS
                time.sleep(random.uniform(0.5, 1.0))
            elif rnd < SOS_RATE + FALL_RATE:
                self.send_alert(random.choice(['05', '06']))  # 跌倒
                time.sleep(random.uniform(0.5, 1.0))

            # 每次连接发 3~5 个不同类型的包
            choices = ['apht', 'aphp', 'temp', 'heartbeat', 'sleep', 'gps']
            for data_type in random.sample(choices, k=random.randint(3, 5)):
                if stop_event.is_set():
                    break
                dispatch = {
                    'apht':      self.send_health_data_apht,
                    'aphp':      self.send_health_data_aphp,
                    'temp':      self.send_temperature,
                    'heartbeat': self.send_heartbeat,
                    'sleep':     self.send_sleep_data,
                    'gps':       self.send_gps_data,
                }
                dispatch[data_type]()
                time.sleep(random.uniform(0.5, 1.5))  # 包间隔
            return True
        finally:
            self.close()


# ─── 每台手表的线程主循环 ─────────────────────────────────────────────────────

def watch_thread(watch_id):
    global active_count
    sim        = WatchSimulator(watch_id)
    start_time = time.time()

    # 分散启动时间：按 watch_id 错开，避免 1000 个线程同时争抢信号量
    time.sleep(random.uniform(0, INTERVAL_MIN))

    while not stop_event.is_set():
        if DURATION > 0 and time.time() - start_time >= DURATION:
            break

        conn_sema.acquire()         # 等待获取并发槽（最多 MAX_CONCURRENT 个）
        if stop_event.is_set():
            conn_sema.release()
            break

        with active_lock:
            active_count += 1

        try:
            sim.run_one_cycle()
        except Exception:
            pass
        finally:
            with active_lock:
                active_count -= 1
            conn_sema.release()

        # 等待下一轮，每 0.5s 检查停止信号
        wait = random.uniform(INTERVAL_MIN, INTERVAL_MAX)
        waited = 0.0
        while waited < wait and not stop_event.is_set():
            time.sleep(0.5)
            waited += 0.5


# ─── 状态打印线程 ─────────────────────────────────────────────────────────────

def status_thread():
    while not stop_event.is_set():
        time.sleep(30)
        if not stop_event.is_set():
            with active_lock:
                cnt = active_count
            log(f"  [状态] 当前并发连接: {cnt}/{MAX_CONCURRENT}  "
                f"总手表: {TOTAL_WATCHES}  时间: {datetime.now().strftime('%H:%M:%S')}")


# ─── 信号处理 & 主函数 ───────────────────────────────────────────────────────

def signal_handler(sig, frame):
    print("\n\n停止信号，正在关闭所有连接...")
    stop_event.set()


def main():
    global START_ID, TOTAL_WATCHES
    signal.signal(signal.SIGINT, signal_handler)

    if len(sys.argv) >= 2:
        START_ID = int(sys.argv[1])
    if len(sys.argv) >= 3:
        TOTAL_WATCHES = int(sys.argv[2])

    dur_desc = f"{DURATION} 秒" if DURATION > 0 else "无限（Ctrl+C 停止）"
    print("=" * 70)
    print("智能手表 TCP 模拟器（批量版）".center(70))
    print("=" * 70)
    print(f"  服务器       : {SERVER_HOST}:{SERVER_PORT}")
    print(f"  手表总数     : {TOTAL_WATCHES} 台（{START_ID} ~ {START_ID + TOTAL_WATCHES - 1}）")
    print(f"  最大并发     : {MAX_CONCURRENT} 台（分批轮转）")
    print(f"  发送间隔     : {INTERVAL_MIN}~{INTERVAL_MAX} 秒/轮")
    print(f"  每轮包数     : 3~5 包")
    print(f"  健康异常概率 : {ANOMALY_RATE:.6%}/指标包")
    print(f"  SOS/跌倒概率 : {SOS_RATE:.6%}/{FALL_RATE:.6%}/轮")
    print(f"  运行时长     : {dur_desc}")
    print("=" * 70)
    print()

    threads = []

    # 启动状态监控线程
    st = threading.Thread(target=status_thread, daemon=True)
    st.start()

    # 启动 TOTAL_WATCHES 个手表线程，每 100 个打印一次进度
    for i in range(TOTAL_WATCHES):
        if stop_event.is_set():
            break
        t = threading.Thread(
            target=watch_thread,
            args=(START_ID + i,),
            daemon=False
        )
        t.start()
        threads.append(t)

        if (i + 1) % 100 == 0:
            print(f"  已创建 {i+1}/{TOTAL_WATCHES} 个手表线程...")
            time.sleep(0.5)   # 分批创建线程，每批间隔 0.5s

    if not stop_event.is_set():
        print(f"\n[OK] 全部 {TOTAL_WATCHES} 个手表线程已启动")
        print(f"     并发上限: {MAX_CONCURRENT}，轮转间隔: {INTERVAL_MIN}~{INTERVAL_MAX}s\n")

    while threads:
        threads = [t for t in threads if t.is_alive()]
        if stop_event.is_set() or not threads:
            break
        time.sleep(1)

    if stop_event.is_set():
        print("等待连接关闭（最多 8 秒）...")
        for t in threads:
            t.join(timeout=8)

    print("\n[OK] 所有手表已停止!")


if __name__ == '__main__':
    main()
