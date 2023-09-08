#!/bin/bash
nNodes=$1
shift

n_nodes=$(uniq $OAR_FILE_NODES | wc -l)

function nextnode {
  local idx=$(($1 % n_nodes))
  local i=0
  for host in $(uniq $OAR_FILE_NODES); do
    if [ $i -eq $idx ]; then
      echo $host
      break;
    fi
    i=$(($i +1))
  done
}

echo "Killing everything"
docker network rm asdproj
docker kill "$(docker ps -q)"

for node in $(oarprint host); do
    oarsh $node 'docker kill $(docker ps -q); docker system prune -f' &
done

wait

for node in $(oarprint host); do
    oarsh $node "docker swarm leave -f" 
done

docker swarm init
JOIN_TOKEN=$(docker swarm join-token manager -q)

host=$(hostname)
for node in $(oarprint host); do
  if [ $node != $host ]; then
    oarsh $node "docker swarm join --token $JOIN_TOKEN $host:2377"
  fi
done

echo "Rebuilding image"
for node in $(oarprint host); do
	oarsh $node "cd $(pwd);  docker build --rm -t asd ." &
done

wait

echo "Creating network"
docker network create -d overlay --attachable --subnet 172.10.0.0/16 asdproj



base_p2p_port=32000
base_server_port=35000
processes=$(($nNodes - 1))
ii=1
i=1
membership="172.10.10.1:$((base_p2p_port))"
ycsb_membership="172.10.10.1:$((base_server_port))"

while [ $i -lt $processes ]; do {
  ii=$((i + 1))
  membership="${membership},172.10.10.$ii:$((base_p2p_port))"
  ycsb_membership="${ycsb_membership},172.10.10.$ii:$((base_server_port))"
  i=$((i + 1))
}
done

echo "Launching containers"
for i in $(seq 01 $(($nNodes - 1))); do
  node=$(nextnode $i)
    ii=$i
    cmd="docker run -d -it \
    --privileged --cap-add=ALL \
    --net asdproj --ip 172.10.10.$ii -h node-$i --name node-$i \
    asd -DlogFilename=logs/node$((base_p2p_port + ii)) -cp asdProj2.jar Main -conf config.properties interface=eth0 address=172.10.10.$((base_p2p_port)) p2p_port=$((base_p2p_port)) server_port=$((base_server_port)) initial_membership=$membership"

    echo "$node node-$i 172.10.10.$ii port $((base_p2p_port)), server port $((base_server_port))"
    oarsh -n $node $cmd
done

sleep 1

# read -p "------------- Press enter start Benchmark --------------------"


 echo "membership: $membership"


 cd client || exit
 docker build --rm -t ycsb .
 cd ..
 echo "Executing Benchmark"

 docker run -it \
     --privileged --cap-add=ALL \
     --net asdproj --ip 172.10.9.1 -h ycsb --name ycsb \
     ycsb 32 1024 $ycsb_membership 50 50


 read -p "------------- Press enter to kill servers. --------------------"

 docker kill "$(docker ps -q)"

 for node in $(oarprint host); do
     oarsh $node 'docker kill $(docker ps -q); docker system prune -f' &
 done

 wait

echo "All processes done!"
