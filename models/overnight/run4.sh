#!/bin/bash
# Batch 4: the same runs as batch 3, which died of an out-of-memory error in the first version of clearGradients.
cd "$(dirname "$0")/../.."
DIR=models/overnight
CP="$DIR/classes4:$(cat $DIR/cp.txt)"
JAVA="java --enable-native-access=ALL-UNNAMED"
COMMON="players=4 iterations=300 games=8 evalEvery=25 evalGames=100 finalEvalGames=300 lr=1e-4 entropy=0.003 botSeats=0.25"
echo "batch 4 started $(date)" > $DIR/progress4.txt
$JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer reward=default seed=3 $COMMON \
    load=$DIR/ppo4-from-imitation.bin.last out=$DIR/ppo4-resumed2-imitation.bin > $DIR/ppo4-resumed2-imitation.log 2>&1 &
$JAVA -Dai.djl.pytorch.num_threads=2 -cp "$CP" no.hal.risk.ai.Trainer reward=advantage seed=3 $COMMON \
    load=$DIR/ppo4-from-2p-advantage.bin.last out=$DIR/ppo4-resumed2-advantage.bin > $DIR/ppo4-resumed2-advantage.log 2>&1 &
wait
echo "batch 4 done $(date)" >> $DIR/progress4.txt
