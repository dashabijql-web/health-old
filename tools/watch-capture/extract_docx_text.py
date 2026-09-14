import argparse
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path


NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}


def extract(path):
    with zipfile.ZipFile(path) as zf:
        xml = zf.read("word/document.xml")
    root = ET.fromstring(xml)
    lines = []
    for block in root.findall(".//w:body/*", NS):
        tag = block.tag.rsplit("}", 1)[-1]
        if tag == "p":
            text = "".join(t.text or "" for t in block.findall(".//w:t", NS)).strip()
            if text:
                lines.append(text)
        elif tag == "tbl":
            for tr in block.findall(".//w:tr", NS):
                cells = []
                for tc in tr.findall(".//w:tc", NS):
                    text = "".join(t.text or "" for t in tc.findall(".//w:t", NS)).strip()
                    cells.append(text)
                if any(cells):
                    lines.append(" | ".join(cells))
    return lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("docx")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    lines = extract(Path(args.docx))
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"WROTE {output} lines={len(lines)}")


if __name__ == "__main__":
    main()
