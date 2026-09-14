import argparse
import json
import re
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path


FRAME_RE_STAR = re.compile(r"^IW\*([A-Z]{2}[A-Z0-9]{2})\*(.*)#$")
FRAME_RE_LEGACY = re.compile(r"^IW([A-Z]{2}[A-Z0-9]{2})(.*)#$")


KNOWN_PARAM_COUNTS = {
    "AP00": (1, 1),
    "AP01": (5, None),
    "AP02": (8, None),
    "AP03": (3, 3),
    "AP05": (1, 1),
    "AP07": (5, None),
    "AP10": (9, None),
    "AP49": (1, 1),
    "AP50": (2, 2),
    "AP51": (0, 0),
    "APHT": (3, 3),
    "APHP": (6, None),
    "APTM": (4, 4),
    "APWT": (0, 0),
    "AP94": (2, 2),
    "APHD": (9, None),
    "AP97": (3, 3),
    "APRR": (3, 3),
}


RESPONSES = {
    "AP00": "BP00",
    "AP01": "BP01",
    "AP02": "BP02",
    "AP03": "BP03",
    "AP05": "BP05",
    "AP07": "BP07",
    "AP10": "BP10",
    "AP49": "BP49",
    "AP50": "BP50",
    "AP51": "BP51",
    "APHT": "BPHT",
    "APHP": "BPHP",
    "APTM": "BPTM",
    "APWT": "BPWT",
    "AP94": "BP94",
    "APHD": "BPHD",
    "AP97": "BP97",
    "APRR": "BPRR",
}


def load_protocol_codes(protocol_text):
    text = Path(protocol_text).read_text(encoding="utf-8", errors="ignore")
    codes = set(re.findall(r"\b[AB]P[A-Z0-9]{2,}\b", text))
    return codes, text


def parse_frame(frame):
    frame = frame.strip()
    match = FRAME_RE_STAR.match(frame)
    style = "star"
    if not match:
        match = FRAME_RE_LEGACY.match(frame)
        style = "legacy"
    if not match:
        return {
            "valid": False,
            "style": None,
            "protocol": None,
            "params": [],
            "issues": ["frame does not match IW*CODE*...# or IWCODE,...#"],
        }
    protocol = match.group(1)
    rest = match.group(2)
    if style == "legacy":
        rest = rest[1:] if rest.startswith(",") else rest
    params = [] if rest == "" else rest.split(",")
    issues = []
    if not frame.startswith("IW"):
        issues.append("missing IW prefix")
    if not frame.endswith("#"):
        issues.append("missing # suffix")
    if not re.match(r"^[AB]P[A-Z0-9]+$", protocol):
        issues.append("invalid protocol code shape")
    return {
        "valid": len(issues) == 0,
        "style": style,
        "protocol": protocol,
        "params": params,
        "issues": issues,
    }


def validate_by_protocol(parsed, known_codes):
    issues = []
    protocol = parsed["protocol"]
    params = parsed["params"]
    if protocol not in known_codes:
        issues.append("protocol code not found in DOCX text")
    expected = KNOWN_PARAM_COUNTS.get(protocol)
    if expected:
        min_count, max_count = expected
        count = len(params)
        if count < min_count:
            issues.append(f"too few params: {count} < {min_count}")
        if max_count is not None and count > max_count:
            issues.append(f"too many params: {count} > {max_count}")
    if protocol == "AP00":
        imei = params[0] if params else ""
        if not re.fullmatch(r"\d{15}", imei):
            issues.append("AP00 IMEI is not 15 digits")
    if protocol == "AP03" and len(params) >= 3:
        if not re.fullmatch(r"\d{14}", params[0]):
            issues.append("AP03 status field is not 14 digits")
        if not re.fullmatch(r"\d+", params[1]):
            issues.append("AP03 step count is not numeric")
        if not re.fullmatch(r"\d+", params[2]):
            issues.append("AP03 roll count is not numeric")
    if protocol == "AP49" and params:
        if not re.fullmatch(r"\d{1,3}", params[0]):
            issues.append("AP49 heart rate is not 1-3 digit number")
    if protocol == "AP50" and len(params) >= 2:
        if not re.fullmatch(r"\d{1,2}(?:\.\d+)?", params[0]):
            issues.append("AP50 temperature is not numeric")
        if not re.fullmatch(r"\d{1,3}", params[1]):
            issues.append("AP50 battery is not numeric")
    if protocol == "APHT" and len(params) >= 3:
        for label, value in zip(("heart_rate", "systolic", "diastolic"), params[:3]):
            if not re.fullmatch(r"\d{1,3}", value):
                issues.append(f"APHT {label} is not numeric")
    if protocol == "APHP" and len(params) >= 6:
        for idx, value in enumerate(params[:6]):
            if value and not re.fullmatch(r"\d{1,3}(?:\.\d+)?", value):
                issues.append(f"APHP param[{idx}] is not numeric/blank")
    return issues


def load_capture(path):
    events = []
    with Path(path).open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return events


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--capture", required=True)
    parser.add_argument("--protocol-text", required=True)
    parser.add_argument("--target-ip", default="10.8.138.180")
    parser.add_argument("--target-imei", default="861265062579435")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    known_codes, _ = load_protocol_codes(args.protocol_text)
    events = load_capture(args.capture)
    frames = [
        e for e in events
        if e.get("event") == "frame"
        and e.get("direction") == "client_to_server"
        and args.target_ip in str(e.get("peer", ""))
    ]
    all_frames = [e for e in events if e.get("event") == "frame" and e.get("direction") == "client_to_server"]
    target_imei_frames = [e for e in all_frames if args.target_imei in e.get("ascii", "")]
    relevant = frames or target_imei_frames

    parsed_rows = []
    issue_counter = Counter()
    protocol_counter = Counter()
    style_counter = Counter()
    for event in relevant:
        frame = event.get("ascii", "")
        parsed = parse_frame(frame)
        issues = list(parsed["issues"])
        if parsed["valid"]:
            issues.extend(validate_by_protocol(parsed, known_codes))
        protocol = parsed["protocol"] or "UNKNOWN"
        protocol_counter[protocol] += 1
        style_counter[parsed["style"] or "invalid"] += 1
        for issue in issues:
            issue_counter[issue] += 1
        parsed_rows.append({
            "ts": event.get("ts"),
            "peer": event.get("peer"),
            "frame": frame,
            "protocol": protocol,
            "style": parsed["style"],
            "param_count": len(parsed["params"]),
            "issues": issues,
        })

    response_frames = [
        e for e in events
        if e.get("event") == "frame"
        and e.get("direction") == "server_to_client"
        and (args.target_ip in str(e.get("peer", "")) or any(str(e.get("conn_id")) == str(r.get("conn_id")) for r in frames))
    ]

    lines = []
    lines.append("# 10.8.138.180 手表 TCP 数据与协议比对报告")
    lines.append("")
    lines.append(f"- 生成时间: {datetime.now().astimezone().isoformat(timespec='seconds')}")
    lines.append(f"- 抓包文件: `{args.capture}`")
    lines.append(f"- 协议文本: `{args.protocol_text}`")
    lines.append(f"- 目标 IP: `{args.target_ip}`")
    lines.append(f"- 目标 IMEI: `{args.target_imei}`")
    lines.append(f"- 抓到目标相关上行帧: `{len(relevant)}`")
    lines.append(f"- 抓到目标相关下行帧: `{len(response_frames)}`")
    lines.append("")

    if not relevant:
        lines.append("## 结论")
        lines.append("")
        lines.append("3 小时观察窗口内没有抓到目标 IP 或目标 IMEI 的完整上行帧，无法判断业务字段是否符合协议。")
        lines.append("")
    else:
        invalid = sum(1 for row in parsed_rows if row["issues"])
        lines.append("## 结论")
        lines.append("")
        if invalid == 0:
            lines.append("抓到的目标上行完整帧在基础格式、协议号和已知字段数量/类型规则上未发现不一致。")
        else:
            lines.append(f"抓到的目标上行完整帧中有 `{invalid}` 条存在协议疑点，详见下方问题统计和样本。")
        lines.append("")

    lines.append("## 协议号统计")
    lines.append("")
    if protocol_counter:
        for protocol, count in protocol_counter.most_common():
            lines.append(f"- `{protocol}`: {count}")
    else:
        lines.append("- 无")
    lines.append("")

    lines.append("## 帧格式统计")
    lines.append("")
    if style_counter:
        for style, count in style_counter.most_common():
            lines.append(f"- `{style}`: {count}")
    else:
        lines.append("- 无")
    lines.append("")

    lines.append("## 问题统计")
    lines.append("")
    if issue_counter:
        for issue, count in issue_counter.most_common():
            lines.append(f"- {issue}: {count}")
    else:
        lines.append("- 无")
    lines.append("")

    lines.append("## 上行样本")
    lines.append("")
    if parsed_rows:
        for row in parsed_rows[:50]:
            issue_text = "; ".join(row["issues"]) if row["issues"] else "OK"
            lines.append(f"- `{row['ts']}` `{row['peer']}` `{row['protocol']}` style=`{row['style']}` params=`{row['param_count']}` result=`{issue_text}`")
            lines.append(f"  `{row['frame']}`")
    else:
        lines.append("- 无")
    lines.append("")

    lines.append("## 下行样本")
    lines.append("")
    if response_frames:
        for event in response_frames[:50]:
            lines.append(f"- `{event.get('ts')}` `{event.get('peer')}` `{event.get('ascii')}`")
    else:
        lines.append("- 无")
    lines.append("")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")
    print(f"WROTE {output}")


if __name__ == "__main__":
    main()
