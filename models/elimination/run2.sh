#!/bin/bash
# Controls: the same continuation, with the old reward (no reward for eliminations), to see what the elimination reward adds.
cd "$(dirname "$0")/../.."
DIR=models/elimination
CP="$DIR/classes:$(cat $DIR/cp.txt)"
START=models/overnight/ppo4-resumed2-imitation.bin.last
COMMON="reward=default iterations=250 games=8 evalEvery=25 evalGames=100 finalEvalGames=300 lr=1e-4 entropy=0.003 botSeats=0.25 load=$START"
echo "controls started $(date)" > $DIR/progress2.txt
for players in 4 3; do
  java --enable-native-access=ALL-UNNAMED -Dai.djl.pytorch.num_threads=3 -cp "$CP" no.hal.risk.ai.Trainer players=$players seed=$((40+players)) $COMMON \
      out=$DIR/control-${players}p.bin > $DIR/control-${players}p.log 2>&1 &
done
wait
echo "controls done $(date)" >> $DIR/progress2.txt
