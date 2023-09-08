#!/bin/bash
faults=$1
n_nodes=$(uniq $OAR_FILE_NODES | wc -l)
p_folder="peer-sampling"

toKill=$(($faults / $n_nodes))

for node in $(uniq $OAR_FILE_NODES); do
    oarsh $node -n "cd work2/$p_folder && ./killMain.sh $toKill" &
done