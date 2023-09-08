#!/bin/bash
host=$1

echo "HOST - $host"

i=0
base_port=5000
p=$(($base_port + $i))
logN="$host-$p"
java -Xmx500m -DlogFilename=logs/$logN -cp peer-sampling.jar Main \
    -conf config.properties address=$host port=$p logFile=$logN \
    2>&1 | sed "s/^/[$logN] /" &
#echo "launched process on port $((base_port + $i))"
sleep 0.3