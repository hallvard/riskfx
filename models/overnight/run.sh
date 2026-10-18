#!/bin/bash
# Overnight experiment: does the attack-advantage term in the reward help, when learning starts from imitating greedy?
#   stage 1: imitate greedy (policy and value, under each reward)    2 runs in parallel
#   stage 2: PPO from there, 2 seeds for each reward                  4 runs in parallel
#   stage 3: summary
cd "$(dirname "$0")/../.."
DIR=models/overnight
CP="$DIR/classes:$(cat $DIR/cp.txt)"
JAVA="java --enable-native-access=ALL-UNNAMED"

echo "stage 1 (imitation) started $(date)" > $DIR/progress.txt
for reward in default advantage; do
  $JAVA -Dai.djl.pytorch.num_threads=4 -cp "$CP" no.hal.risk.ai.Imitation reward=$reward trainGames=300 testGames=40 epochs=12 matchGames=400 \
      out=$DIR/imitation-$reward.bin > $DIR/imitation-$reward.log 2>&1 &
done
wait
echo "stage 1 done $(date)" >> $DIR/progress.txt

echo "stage 2 (PPO) started $(date)" >> $DIR/progress.txt
for reward in default advantage; do
  for seed in 1 2; do
    $JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer reward=$reward seed=$seed iterations=400 games=8 \
        evalEvery=20 evalGames=100 finalEvalGames=400 lr=1e-4 entropy=0.003 botSeats=0.25 \
        load=$DIR/imitation-$reward.bin out=$DIR/ppo-$reward-$seed.bin > $DIR/ppo-$reward-$seed.log 2>&1 &
  done
done
wait
echo "stage 2 done $(date)" >> $DIR/progress.txt

{
  echo "== imitation"
  for reward in default advantage; do
    echo "-- $reward"; grep "playing greedy\|explains" $DIR/imitation-$reward.log | tail -2
  done
  echo "== PPO, final results on new boards (400 games vs greedy)"
  for f in $DIR/ppo-*.log; do echo "-- $f"; grep "FINAL\|start " $f; done
} > $DIR/summary.txt 2>&1
echo "all done $(date)" >> $DIR/progress.txt
