from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt

here = Path(__file__).parent
data = defaultdict(lambda: defaultdict(dict))
stage = label = t = None

for line in (here / "res").read_text().splitlines():
    if line.startswith("# ----"):
        stage, label = line.strip("# -"), "collector"
    elif line.startswith("##"):
        label = "empty lock" if "Empty" in line else None
    elif line.startswith("T = "):
        t = int(line.split("=")[1])
    elif line.startswith("median:") and label:
        data[stage][label][t] = float(line.split()[1]) / 1e6

fig, axes = plt.subplots(1, len(data), figsize=(4 * len(data), 4))
for ax, (stage, series) in zip(axes, data.items()):
    for label, points in series.items():
        ax.plot(list(points), list(points.values()), marker="o", label=label)
    ax.set_title(stage)
    ax.set_xlabel("threads")
    ax.set_ylabel("Mops/sec")
    ax.set_xticks(list(points))
    ax.grid(True)
    ax.legend()

fig.tight_layout()
fig.savefig(here / "plots.png")
