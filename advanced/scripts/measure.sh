#!/usr/bin/env bash
# Prints the memory (RSS) of the running lesson's three services and the total.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ ! -f "$ROOT/.run/pids" ]; then
  echo "No lesson is running." >&2
  exit 1
fi

total=0
printf '%-10s %8s  %s\n' "PID" "RSS(MB)" "COMMAND"
while read -r pid; do
  rss_kb="$(ps -o rss= -p "$pid" | tr -d ' ')"
  [ -z "$rss_kb" ] && continue
  cmd="$(ps -o args= -p "$pid" | awk '{ print $NF }')"
  printf '%-10s %8s  %s\n' "$pid" "$((rss_kb / 1024))" "$(basename "$cmd")"
  total=$((total + rss_kb))
done < "$ROOT/.run/pids"
printf '%-10s %8s\n' "TOTAL" "$((total / 1024))"
