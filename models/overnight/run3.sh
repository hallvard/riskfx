#!/bin/bash
# Batch 3: resume the two 4-player runs that froze (poisoned gradient buffers, now fixed), from their final weights.
cd "$(dirname "$0")/../.."
DIR=models/overnight
CP="$DIR/classes3:$(cat $DIR/cp.txt)"
JAVA="java --enable-native-access=ALL-UNNAMED"
COMMON="players=4 iterations=300 games=8 evalEvery=25 evalGames=100 finalEvalGames=300 lr=1e-4 entropy=0.003 botSeats=0.25"
echo "batch 3 started $(date)" > $DIR/progress3.txt
$JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer reward=default seed=2 $COMMON \
    load=$DIR/ppo4-from-imitation.bin.last out=$DIR/ppo4-resumed-imitation.bin > $DIR/ppo4-resumed-imitation.log 2>&1 &
$JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer reward=advantage seed=2 $COMMON \
    load=$DIR/ppo4-from-2p-advantage.bin.last out=$DIR/ppo4-resumed-advantage.bin > $DIR/ppo4-resumed-advantage.log 2>&1 &
wait
echo "batch 3 done $(date)" >> $DIR/progress3.txt
