#!/bin/bash

processes=$1
host=$2
contact=$3

echo "ARGS: $processes $host $contact"

if [ -z $processes ] || [ $processes -lt 1 ]; then
  echo "please indicate a number of processes of at least one"
  exit 0
fi

i=0
base_port=5001

while [ $i -lt $processes ]; do
  p=$(($base_port + $i))
  logN="$host-$p"
  java -Xmx500m -DlogFilename=logs/$logN -cp peer-sampling.jar Main \
    -conf config.properties address=$host port=$p logFile=$logN \
    contact=${contact} 2>&1 | sed "s/^/[$logN] /" &
  #echo "launched process on port $p"
  sleep 0.3
  #contact="localhost:$((base_port + $i))"
  i=$(($i + 1))
done

#echo "All $processes processes Started!"
