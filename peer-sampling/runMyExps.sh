#!/bin/bash

processes=$1

#sleep 1m
mkdir results
echo "Starting exps!"
for time in 3; do
  for run in 1 2; do
    echo "TIME ${time} Run $run"
    for faults in 12 24 52; do
      echo "Faults $faults"
      ./runOnClusters.sh $processes
      fileName="sleep${run}.txt"
      #arg="run:${run}_faults_${faults}"

      sleep 1m
      java InfoFileReader results/$fileName $faults $processes &
      sleep 1m
      echo "GOING TO REMOVE FILES"
      if [ $faults -gt 0 ]; then
        ./killProcesses.sh $faults
      fi
      echo "GOING TO SLEEP 2"
      sleep ${time}m
      ./stopAll.sh

      sleep 1
      #java InfoFileReader results/$fileName $arg
    done
  done
done

echo "EXPERIMENTS FINISHED!!!"