#!/usr/bin/env python3
import argparse
import socket
import time


def recv_frame(sock, timeout=1.0):
    sock.settimeout(timeout)
    data = bytearray()
    try:
        while True:
            chunk = sock.recv(1)
            if not chunk:
                break
            data.extend(chunk)
            if chunk == b"#":
                break
    except socket.timeout:
        pass
    return data.decode("ascii", errors="replace")


def send_frame(sock, frame):
    print(f"TX {frame}")
    sock.sendall(frame.encode("ascii"))
    response = recv_frame(sock)
    if response:
        print(f"RX {response}")
    else:
        print("RX <none>")


def main():
    parser = argparse.ArgumentParser(description="Send AP00/AP03/APHP probe frames to the Health TCP backend.")
    parser.add_argument("--host", required=True, help="Backend TCP host or IP")
    parser.add_argument("--port", type=int, default=9000, help="Backend TCP port")
    parser.add_argument("--imei", default="123456789012345", help="15-digit test IMEI")
    parser.add_argument("--heart-rate", type=int, default=78)
    parser.add_argument("--spo2", type=int, default=98)
    parser.add_argument("--steps", type=int, default=1280)
    args = parser.parse_args()

    frames = [
        f"IW*AP00*{args.imei}#",
        f"IW*AP03*1,{args.steps},0,{int(args.steps * 0.045)}#",
        f"IW*APHP*{args.heart_rate},0,0,{args.spo2},0,0#",
    ]

    with socket.create_connection((args.host, args.port), timeout=5.0) as sock:
        for frame in frames:
            send_frame(sock, frame)
            time.sleep(0.3)


if __name__ == "__main__":
    main()
