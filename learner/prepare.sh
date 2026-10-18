#!/bin/bash
# Builds the Java side and writes the class path of the actor to learner/work/classpath.txt, which the learner reads.
set -e
cd "$(dirname "$0")/.."
mkdir -p learner/work
mvn -q -o -pl training -am compile dependency:build-classpath -Dmdep.outputFile="$(pwd)/learner/work/deps.txt"
echo "$(pwd)/training/target/classes:$(cat learner/work/deps.txt)" > learner/work/classpath.txt
rm learner/work/deps.txt
echo "wrote learner/work/classpath.txt"
