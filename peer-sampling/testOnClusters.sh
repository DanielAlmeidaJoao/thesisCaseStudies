#!/bin/bash
n_tests=$1
processes=$2

if [ -z $n_tests ] || [ $n_tests -lt 1 ]; then
  echo "please indicate a number of tests of at least one"
  exit 0
fi

if [ -z $processes ] || [ $processes -lt 1 ]; then
  echo "please indicate a number of processes of at least one"
  exit 0
fi

i=0
while [ $i -lt $n_tests ]; do
    echo "STARTING TEST $i"
    ./runOnClusters.sh $processes
    echo "SLEEPING BEFORE NEXT TEST $i"
    sleep 3m
    ./stopAll.sh
    sleep 1m
    java InfoFileReader
    i=$(($i+1))
done