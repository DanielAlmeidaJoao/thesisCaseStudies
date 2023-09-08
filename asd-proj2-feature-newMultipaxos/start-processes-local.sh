#!/bin/bash

usage() { echo "Usage: $(basename $0) [-p <num_processes>] [-d]"; }

while getopts 'p:d' opt; do
  case "$opt" in
  p)
    processes="$OPTARG"
    ;;
  d)
    base_debug_port=5005
    ;;
  ?)
    usage
    exit 1
    ;;
  esac
done

if [ -z $processes ] || [ $processes -lt 1 ]; then
  echo "please indicate a number of processes of at least one"
  usage
  exit 1
fi

i=0
base_p2p_port=34000
base_server_port=35000

membership="localhost:${base_p2p_port}"

read -p "------------- Press enter start. After starting, press enter to kill all servers --------------------"

i=1
while [ $i -lt $processes ]; do
  membership="${membership},localhost:$((base_p2p_port + i))"
  i=$((i + 1))
done

i=0
while [ $i -lt $processes ]; do
  java ${base_debug_port:+"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$((base_debug_port + i))"} -DlogFilename=logs/node$((base_p2p_port + i)) -cp target/asdProj2.jar Main -conf config.properties address=localhost p2p_port=$((base_p2p_port + i)) server_port=$((base_server_port + i)) initial_membership=$membership 2>&1 | sed "s/^/[$((base_p2p_port + i))] /" &
  echo "launched process on p2p port $((base_p2p_port + i)), server port $((base_server_port + i))"
  sleep 1
  i=$((i + 1))
done

sleep 2
read -p "------------- Press enter to kill servers. --------------------"

kill $(ps aux | grep 'asdProj2.jar' | awk '{print $2}')

echo "All processes done!"
