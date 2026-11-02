#!/usr/bin/env python3
"""Compose stability check (stmaterial flavor): the compiler's reports against the baseline in the repo.

Fails when a composable gains an unstable parameter or a restartable composable can't be skipped, compared with
smarttubetv/compose-stability-baseline.txt. Run the build with reports first (see CLAUDE.md, "Compose stability check"):

  ./gradlew :smarttubetv:compileStmaterialReleaseKotlin -PcomposeReports --rerun-tasks
  python3 scripts/compose_stability.py            # check
  python3 scripts/compose_stability.py --update   # accept the current state as the baseline
"""
import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORTS = ROOT / "smarttubetv" / "build" / "compose-reports"
BASELINE = ROOT / "smarttubetv" / "compose-stability-baseline.txt"
FUN = re.compile(r"\bfun ([\w.`<>$]+)\(")
PARAM = re.compile(r"^\s+unstable ([\w`<>]+): (.+?)(?: = .*)?$")


def current() -> set[str]:
	texts = sorted(REPORTS.glob("*stmaterialRelease-composables.txt"))
	tables = sorted(REPORTS.glob("*stmaterialRelease-composables.csv"))
	if not texts or not tables:
		sys.exit(f"No reports in {REPORTS}: run ./gradlew :smarttubetv:compileStmaterialReleaseKotlin -PcomposeReports --rerun-tasks")
	entries = set()
	function = None
	for line in texts[0].read_text().splitlines():
		match = FUN.search(line)
		if match:
			function = match.group(1)
			continue
		param = PARAM.match(line)
		if param and function:
			entries.add(f"unstable param  {function}: {param.group(1)}: {param.group(2)}")
	with tables[0].open() as rows:
		for row in csv.DictReader(rows):
			if row["restartable"] == "1" and row["skippable"] == "0" and row["isLambda"] == "0":
				entries.add(f"not skippable   {row['package']}")
	return entries


def main() -> int:
	now = current()
	if "--update" in sys.argv:
		BASELINE.write_text("".join(f"{entry}\n" for entry in sorted(now)))
		print(f"Baseline updated: {len(now)} entries")
		return 0
	known = set(BASELINE.read_text().splitlines()) if BASELINE.exists() else set()
	new = sorted(now - known)
	gone = sorted(known - now)
	for entry in gone:
		print(f"fixed: {entry}")
	if new:
		print("Compose stability got worse:")
		for entry in new:
			print(f"  {entry}")
		print("Fix them, or accept with: python3 scripts/compose_stability.py --update")
		return 1
	print(f"Compose stability OK ({len(now)} known entries{', some fixed: run --update' if gone else ''})")
	return 0


if __name__ == "__main__":
	sys.exit(main())
