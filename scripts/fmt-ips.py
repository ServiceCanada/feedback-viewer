#!/usr/bin/env python3
import re
import sys

LINE_WIDTH = 120
INDENT = "      "
FILE = "kubernetes/feedback-viewer-ingress.yml"

with open(FILE) as f:
    content = f.read()

pattern = r'(nginx\.ingress\.kubernetes\.io/whitelist-source-range:\s*")(.*?)(")'
match = re.search(pattern, content, re.DOTALL)
if not match:
    print("Could not find whitelist-source-range annotation")
    sys.exit(1)

ips = [ip.strip() for ip in match.group(2).split(",") if ip.strip()]

lines = []
current = []
current_len = 0

for ip in ips:
    seg = (", " if current else "") + ip
    if current and current_len + len(seg) > LINE_WIDTH:
        lines.append(INDENT + ", ".join(current))
        current = [ip]
        current_len = len(ip)
    else:
        current.append(ip)
        current_len += len(seg)

if current:
    lines.append(INDENT + ", ".join(current))

formatted = "\n" + ",\n".join(lines) + '"'

new_content = content[: match.start(2)] + formatted + content[match.end(3) :]

with open(FILE, "w") as f:
    f.write(new_content)

print(f"Formatted {len(ips)} IPs across {len(lines)} lines")
