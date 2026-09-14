import argparse
import json
import re
from pathlib import Path


FRAME_RE = re.compile(r"(IW\*?[AB]P[A-Z0-9]{2}.*?#)")
TS_RE = re.compile(r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend-log", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with Path(args.backend_log).open("r", encoding="utf-8", errors="ignore") as src, output.open("w", encoding="utf-8") as dst:
        for line in src:
            match = FRAME_RE.search(line)
            if not match:
                continue
            frame = match.group(1).strip()
            ts_match = TS_RE.match(line)
            event = {
                "event": "frame",
                "direction": "client_to_server",
                "peer": "backend-log",
                "ts": ts_match.group(1) if ts_match else None,
                "byte_count": len(frame.encode("ascii", errors="ignore")),
                "ascii": frame,
                "hex": frame.encode("ascii", errors="ignore").hex(),
            }
            dst.write(json.dumps(event, ensure_ascii=False, separators=(",", ":")) + "\n")
            count += 1
    print(f"WROTE {output} frames={count}")


if __name__ == "__main__":
    main()
