import argparse
import asyncio
import base64
import json
import signal
from datetime import datetime, timezone
from pathlib import Path


def now_iso():
    return datetime.now().astimezone().isoformat(timespec="milliseconds")


def ascii_preview(data):
    return "".join(chr(b) if 32 <= b <= 126 else "." for b in data)


def frames_from_buffer(buffer):
    frames = []
    while True:
        marker = buffer.find(b"#")
        if marker < 0:
            break
        frame = bytes(buffer[: marker + 1])
        del buffer[: marker + 1]
        frames.append(frame)
    return frames


class JsonlWriter:
    def __init__(self, path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.file = self.path.open("a", encoding="utf-8", buffering=1)
        self.lock = asyncio.Lock()

    async def write(self, event):
        event.setdefault("ts", now_iso())
        line = json.dumps(event, ensure_ascii=False, separators=(",", ":"))
        async with self.lock:
            self.file.write(line + "\n")

    def close(self):
        self.file.close()


async def relay(reader, writer, event_writer, conn_id, direction, peer, frame_buffer):
    try:
        while True:
            data = await reader.read(4096)
            if not data:
                await event_writer.write({
                    "event": "eof",
                    "conn_id": conn_id,
                    "direction": direction,
                    "peer": peer,
                })
                break

            await event_writer.write({
                "event": "chunk",
                "conn_id": conn_id,
                "direction": direction,
                "peer": peer,
                "byte_count": len(data),
                "ascii": ascii_preview(data),
                "hex": data.hex(),
                "b64": base64.b64encode(data).decode("ascii"),
            })

            frame_buffer.extend(data)
            for frame in frames_from_buffer(frame_buffer):
                await event_writer.write({
                    "event": "frame",
                    "conn_id": conn_id,
                    "direction": direction,
                    "peer": peer,
                    "byte_count": len(frame),
                    "ascii": ascii_preview(frame),
                    "hex": frame.hex(),
                    "b64": base64.b64encode(frame).decode("ascii"),
                })

            writer.write(data)
            await writer.drain()
    except Exception as exc:
        await event_writer.write({
            "event": "relay_error",
            "conn_id": conn_id,
            "direction": direction,
            "peer": peer,
            "error": repr(exc),
        })
    finally:
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass


async def handle_client(client_reader, client_writer, args, event_writer, state):
    state["conn_seq"] += 1
    conn_id = state["conn_seq"]
    peer = client_writer.get_extra_info("peername")
    peer_text = f"{peer[0]}:{peer[1]}" if peer else "unknown"

    await event_writer.write({
        "event": "connect",
        "conn_id": conn_id,
        "peer": peer_text,
        "listen_host": args.listen_host,
        "listen_port": args.listen_port,
        "target_host": args.target_host,
        "target_port": args.target_port,
    })

    try:
        backend_reader, backend_writer = await asyncio.open_connection(args.target_host, args.target_port)
    except Exception as exc:
        await event_writer.write({
            "event": "backend_connect_error",
            "conn_id": conn_id,
            "peer": peer_text,
            "error": repr(exc),
        })
        client_writer.close()
        await client_writer.wait_closed()
        return

    c2s_buffer = bytearray()
    s2c_buffer = bytearray()
    tasks = [
        asyncio.create_task(relay(client_reader, backend_writer, event_writer, conn_id, "client_to_server", peer_text, c2s_buffer)),
        asyncio.create_task(relay(backend_reader, client_writer, event_writer, conn_id, "server_to_client", peer_text, s2c_buffer)),
    ]
    done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
    for task in pending:
        task.cancel()
    await asyncio.gather(*pending, return_exceptions=True)

    if c2s_buffer:
        await event_writer.write({
            "event": "partial_frame",
            "conn_id": conn_id,
            "direction": "client_to_server",
            "peer": peer_text,
            "byte_count": len(c2s_buffer),
            "ascii": ascii_preview(bytes(c2s_buffer)),
            "hex": bytes(c2s_buffer).hex(),
        })
    if s2c_buffer:
        await event_writer.write({
            "event": "partial_frame",
            "conn_id": conn_id,
            "direction": "server_to_client",
            "peer": peer_text,
            "byte_count": len(s2c_buffer),
            "ascii": ascii_preview(bytes(s2c_buffer)),
            "hex": bytes(s2c_buffer).hex(),
        })

    await event_writer.write({
        "event": "disconnect",
        "conn_id": conn_id,
        "peer": peer_text,
    })


async def main_async(args):
    writer = JsonlWriter(args.output)
    state = {"conn_seq": 0}
    stop_event = asyncio.Event()

    def stop():
        stop_event.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, stop)
        except NotImplementedError:
            pass

    await writer.write({
        "event": "proxy_start",
        "listen_host": args.listen_host,
        "listen_port": args.listen_port,
        "target_host": args.target_host,
        "target_port": args.target_port,
        "duration_seconds": args.duration_seconds,
    })

    server = await asyncio.start_server(
        lambda r, w: handle_client(r, w, args, writer, state),
        args.listen_host,
        args.listen_port,
    )

    try:
        async with server:
            try:
                await asyncio.wait_for(stop_event.wait(), timeout=args.duration_seconds)
            except asyncio.TimeoutError:
                pass
    finally:
        server.close()
        await server.wait_closed()
        await writer.write({"event": "proxy_stop", "connections": state["conn_seq"]})
        writer.close()


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--listen-host", default="0.0.0.0")
    parser.add_argument("--listen-port", type=int, default=9000)
    parser.add_argument("--target-host", default="127.0.0.1")
    parser.add_argument("--target-port", type=int, default=19000)
    parser.add_argument("--duration-seconds", type=int, default=10800)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


if __name__ == "__main__":
    asyncio.run(main_async(parse_args()))
