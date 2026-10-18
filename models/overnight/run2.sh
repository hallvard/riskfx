#!/bin/bash
# Overnight batch 2: four players.
#   stage A: how do the 2-player networks do against 3 greedy bots, untrained?  And imitation of greedy in 4-player games.
#   stage B: PPO in 4-player games from three starting points, and a long 2-player continuation.
cd "$(dirname "$0")/../.."
DIR=models/overnight
CP="$DIR/classes2:$(cat $DIR/cp.txt)"
JAVA="java --enable-native-access=ALL-UNNAMED"

echo "batch 2, stage A started $(date)" > $DIR/progress2.txt
for net in default-1 default-2 advantage-1 advantage-2; do
  reward=${net%-*}
  $JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer players=4 iterations=0 evalGames=300 reward=$reward \
      load=$DIR/ppo-$net.bin.last out=$DIR/unused.bin > $DIR/transfer-$net.log 2>&1 &
done
$JAVA -Dai.djl.pytorch.num_threads=4 -cp "$CP" no.hal.risk.ai.Imitation players=4 reward=default trainGames=100 testGames=20 epochs=12 matchGames=200 \
    out=$DIR/imitation-4p.bin > $DIR/imitation-4p.log 2>&1 &
wait
echo "stage A done $(date)" >> $DIR/progress2.txt

COMMON="evalEvery=25 evalGames=100 finalEvalGames=300 lr=1e-4 entropy=0.003 botSeats=0.25"
$JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer players=4 reward=default seed=1 iterations=300 games=8 $COMMON \
    load=$DIR/ppo-default-2.bin.last out=$DIR/ppo4-from-2p-default.bin > $DIR/ppo4-from-2p-default.log 2>&1 &
$JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer players=4 reward=advantage seed=1 iterations=300 games=8 $COMMON \
    load=$DIR/ppo-advantage-1.bin.last out=$DIR/ppo4-from-2p-advantage.bin > $DIR/ppo4-from-2p-advantage.log 2>&1 &
$JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer players=4 reward=default seed=1 iterations=300 games=8 $COMMON \
    load=$DIR/imitation-4p.bin out=$DIR/ppo4-from-imitation.bin > $DIR/ppo4-from-imitation.log 2>&1 &
$JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer players=2 reward=default seed=3 iterations=600 games=8 \
    evalEvery=50 evalGames=100 finalEvalGames=400 lr=1e-4 entropy=0.003 botSeats=0.25 \
    load=$DIR/ppo-default-2.bin.last out=$DIR/ppo-default-2-long.bin > $DIR/ppo-default-2-long.log 2>&1 &
wait
echo "batch 2 done $(date)" >> $DIR/progress2.txt
