#!/bin/bash
processes=$1

if [ -z $processes ] || [ $processes -lt 1 ]; then
  echo "please indicate a number of processes of at least one"
  exit 0
fi
p_folder="peer-sampling"
n_nodes=$(uniq $OAR_FILE_NODES | wc -l)
local_ip=$(hostname -I | cut -d ' ' -f 1)
contact="$local_ip:5000"
i=0
n_proc=$(($processes / $n_nodes))

rm ./logs/*
sleep 0.5
./start1.sh $local_ip
sleep 0.5
./start2.sh $(($n_proc-1)) $local_ip $contact

#BUILDING MEMBERSHIP
for node in $(uniq $OAR_FILE_NODES); do
	host=$(oarsh $node "hostname -I | cut -d ' ' -f 1")
  if [ $local_ip != $host ]; then
	  oarsh $node -n "cd work2/$p_folder && ./start2.sh $n_proc $host $contact" &
  fi
done

echo "STARTED ${processes} PROCESSES ON ${n_nodes} NODES!"
