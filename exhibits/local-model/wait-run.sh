#!/bin/bash
# waits for MODEL to be pulled, then asks it the four turns
cd "$(dirname "$0")"
M=$1
until ~/.local/ollama/ollama list 2>/dev/null | grep -q "^$M"; do sleep 15; done
./mk.sh $M
for k in repo merge comment plan; do ./ask.sh $M $k; done
