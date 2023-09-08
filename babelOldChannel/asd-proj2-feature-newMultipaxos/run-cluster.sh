#!/bin/bash

docker network create -d bridge --attachable --subnet 172.10.0.0/16 asdproj

processes=$1
base_p2p_port=32000
base_server_port=35000

ii=1
i=1
membership="172.10.10.1:$((base_p2p_port))"

while [ $i -lt $processes ]; do
  ii=$(($i + 1))
  membership="${membership},172.10.10.$ii:$((base_p2p_port))"
  i=$((i + 1))
done

read -p "------------- Press enter start. After starting, press enter to kill all servers --------------------"
echo "membership: $membership"
docker_ids=''
i=0
while [ $i -lt "$processes" ]; do
{
    ii=$((i + 1))
    id=$(docker run -d -it \
    --privileged --cap-add=ALL \
    --net asdproj --ip 172.10.10.$ii -h node-"$i" --name node-"$i" \
    -p $((base_server_port + ii)):$((base_server_port)) \
    asd -DlogFilename=logs/node$((base_p2p_port + ii)) -cp asdProj2.jar Main -conf config.properties interface=eth0 address=172.10.10.$((base_p2p_port)) p2p_port=$((base_p2p_port)) server_port=$((base_server_port)) initial_membership=$membership)

    echo "node-$i 172.10.10.$ii port $((base_p2p_port)), server port $((base_server_port))"
    docker_ids="$docker_ids $id"
    i=$((i + 1))
}
done

read -p "------------- Press enter to kill servers. --------------------"
ids=($docker_ids)
for i in "${ids[@]}"; do
{
    docker kill "$i"
}
done
docker system prune -f
echo "All processes done!"
