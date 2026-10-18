#!/bin/bash
# Elimination reward: continue from the best 4-player network, with a reward for eliminating opponents,
# in games of 4 and in games of 3 players. Low priority and few threads, so the user interface can be tested meanwhile.
cd "$(dirname "$0")/../.."
DIR=models/elimination
CP="$DIR/classes:$(cat $DIR/cp.txt)"
START=models/overnight/ppo4-resumed2-imitation.bin.last
COMMON="reward=elimination iterations=250 games=8 evalEvery=25 evalGames=100 finalEvalGames=300 lr=1e-4 entropy=0.003 botSeats=0.25 load=$START"
echo "started $(date)" > $DIR/progress.txt
for players in 4 3; do
  nice -n 10 java --enable-native-access=ALL-UNNAMED -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer players=$players seed=$((20+players)) $COMMON \
      out=$DIR/ppo-${players}p.bin > $DIR/ppo-${players}p.log 2>&1 &
done
wait
echo "done $(date)" >> $DIR/progress.txt
