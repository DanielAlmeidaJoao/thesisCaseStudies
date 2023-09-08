#!/bin/bash

usage() { echo "Usage: $(basename $0) [-l local] [-c docker] [-s swarm] [-n nodes] [-p processes] [-t testers]"; }

generateMembership() { 
    localhost=$1
    p2p_port=$2
    procs=$3
    ip=$4

    if [ "$localhost" -eq "1" ]; then
        membership="localhost:$p2p_port"

        i=1
        while [ $i -lt "$procs" ]; do
            membership="${membership},localhost:$((p2p_port + i))"
            i=$((i + 1))
        done
        echo "$membership"
    else
        membership="$ip".1:"$p2p_port"
        procs=$((procs + 1))
        i=2
        while [ $i -lt "$procs" ]; do
            membership="${membership},$ip".$i:"$p2p_port"
            i=$((i + 1))
        done
        echo "$membership"
    fi
}

while getopts 'lcsn: p: t:' opt; do
  case "$opt" in
  n)
    nodes="$OPTARG"
    ;;
  t)
    testers="$OPTARG"
    ;;
  p)
    processes="$OPTARG"
    ;;
  l)
    local=1
    ;;
  c)
    cluster=1
    ;;
  s)
    swarm=1
    ;;
  ?)
    usage
    exit 1
    ;;
  esac
done

## Check flags

if [ -z "$testers" ]; then
        echo 'Missing -t <testers>'
        exit 1
fi

if [ -z "$local" ] && [ -z "$cluster" ] && [ -z "$swarm" ]; then
        echo 'Missing -l -c or -s'
        exit 1
fi

if [ -n "$local" ] || [ -n "$cluster" ]; then
    if [ -z "$processes" ]; then
        echo 'Missing -p <processes>'
        exit 1
    fi
fi

if [ -n "$swarm" ]; then
    if [ -z "$nodes" ]; then
        echo 'Missing -n <nodes>'
        exit 1
    fi
fi


date=$(date "+%T")

base_p2p_port=32000
base_server_port=35000


if [ -n "$local" ]; then

    membership=$(generateMembership 1 32000 "$processes" localhost)
    echo "Current Membership: $membership"
    i=0
    while [ $i -lt "$processes" ]; do
        java -DlogFilename=logs/node$((base_p2p_port + i)) -cp target/asdProj2.jar Main -conf config.properties address=localhost p2p_port=$((base_p2p_port + i)) server_port=$((base_server_port + i)) initial_membership=$membership 2>&1 | sed "s/^/[$((base_p2p_port + i))] /" > /dev/null &
        echo "launched process on p2p port $((base_p2p_port + i)), server port $((base_server_port + i))"
        sleep 1
        i=$((i + 1))
    done
    membership=$(generateMembership 1 35000 "$processes" localhost)
    mkdir -p local-benchmark/"$date"
    cd client || echo "Failed cd client" || exit 1
    
    echo "Running tester with 1 client."
    i=1
    ./exec.sh 1 1024 "$membership" 50 50 | tee ../local-benchmark/"$date"/local-1-"$date".ycsb
    i=2
    while [ $i -lt "$testers" ]; do
    {
        clear
        sleep 2
        echo "Running tester with $i client."
        ./exec.sh $i 1024 "$membership" 50 50 | tee ../local-benchmark/"$date"/local-"$i"-"$date".ycsb
        i=$((i * 2))
    }
    done

    if [ $i -eq "$testers" ]; then
        ./exec.sh "$testers" 1024 "$membership" 50 50 | tee ../local-benchmark/"$date"/local-"$testers"-"$date".ycsb
    fi
    clear
    kill $(ps aux | grep 'asdProj2.jar' | awk '{print $2}')

    echo "All processes done!"

    echo "Generated the following benchmarks:"
    ls ../local-benchmark/"$date"/
fi


if [ -n "$cluster" ]; then


    echo "Building image"
    docker build --rm -t asd .
    sleep 1
    clear
    echo "Creating docker network"
    docker kill $(docker ps -q)
    docker system prune -f
    docker network rm asdproj
    docker network create -d bridge --attachable --subnet 172.10.0.0/16 asdproj
    sleep 1
    clear

    docker_ids=''
    membership=$(generateMembership 0 32000 "$processes" 172.10.10)
    echo "Current membership: $membership"
    sleep 1
    i=0
    while [ "$i" -lt "$processes" ]; do
    {
        ii=$((i + 1))
        id=$(docker run -d -it \
        --privileged --cap-add=ALL \
        --net asdproj --ip 172.10.10.$ii -h node-"$i" --name node-"$i" \
        -p $((base_server_port + i)):$((base_server_port)) \
        asd -DlogFilename=logs/node$((base_p2p_port + ii)) -cp asdProj2.jar Main -conf config.properties interface=eth0 address=172.10.10.$((base_p2p_port)) p2p_port=$((base_p2p_port)) server_port=$((base_server_port)) initial_membership=$membership)

        echo "node-$i 172.10.10.$ii port $((base_p2p_port)), server port $((base_server_port))"
        docker_ids="$docker_ids $id"
        i=$((i + 1))
    }
    done

    mkdir -p cluster-benchmark/"$date"
    cd client || echo "Failed cd client" || exit 1
    membership=$(generateMembership 1 35000 "$processes" 172.10.10)
    sleep 5
    clear
    echo "Running tester with 1 client."
    i=1
    ./exec.sh 1 1024 "$membership" 50 50 | tee ../cluster-benchmark/"$date"/cluster-1-"$date".ycsb
    i=2
    while [ $i -lt "$testers" ]; do
    {
        sleep 2
        clear
        echo "Running tester with $i clients."
        ./exec.sh $i 1024 "$membership" 50 50 | tee ../cluster-benchmark/"$date"/cluster-"$i"-"$date".ycsb
        i=$((i * 2))
    }
    done
    sleep 2
    clear
    echo "Running tester with $testers clients."
    if [ $i -eq "$testers" ]; then
        ./exec.sh "$testers" 1024 "$membership" 50 50 | tee ../cluster-benchmark/"$date"/cluster-"$testers"-"$date".ycsb
    fi
    clear
    sleep 1
    
    docker kill $(docker ps -q)
    docker system prune -f
    cd ..
    echo "All processes done!"

    echo "Generated the following benchmarks:"
    ls ../cluster-benchmark/"$date"
fi

if [ -n "$swarm" ]; then

    echo "Building image"
    docker build --rm -t asd .
    sleep 1
    clear
    echo "Creating docker network"
    docker network rm asdproj
    docker network create -d bridge --attachable --subnet 172.10.0.0/16 asdproj
    sleep 1
    clear
    processes=$((nodes-1))
    membership=$(generateMembership 0 35000 "$processes" 172.10.10)
    sleep 1

    ./run-swarm.sh "$nodes"
    mkdir -p swarm-benchmark/"$date"
    clear

    echo "Building client"
    cd client || echo "cd client failed" || exit 1
    docker build --rm -t ycsb .
    cd ..
    sleep 5
    clear
    echo "Current membership: $membership"
    echo "Running tester with 1 client."
    i=1
    docker run \
        --rm --privileged --cap-add=ALL \
        --net asdproj -h ycsb --name ycsb \
        ycsb 1 1024 "$membership" 50 50 | tee swarm-benchmark/"$date"/swarm-1-"$date".ycsb

    i=2
    while [ $i -lt "$testers" ]; do
    {
        sleep 2
        clear
        echo "Running tester with $i clients."
        docker run \
            --rm --privileged --cap-add=ALL \
            --net asdproj -h ycsb --name ycsb \
            ycsb "$i" 1024 "$membership" 50 50 | tee swarm-benchmark/"$date"/swarm-"$i"-"$date".ycsb
            i=$((i * 2))
    }
    done
    sleep 2
    clear
    echo "Running tester with $testers clients."
    if [ $i -eq "$testers" ]; then
        docker run \
            --rm --privileged --cap-add=ALL \
            --net asdproj -h ycsb --name ycsb \
            ycsb "$testers" 1024 "$membership" 50 50 | tee swarm-benchmark/"$date"/swarm-"$testers"-"$date".ycsb
    fi
    clear
    sleep 1

    echo "Killing everything"
    docker network rm asdproj
    docker kill "$(docker ps -q)"

    for node in $(oarprint host); do
        oarsh $node 'docker kill $(docker ps -q); docker system prune -f' &
    done
    sleep 5
    clear
    # docker system prune -f
    echo "All processes done!"

    echo "Generated the following benchmarks:"
    ls swarm-benchmark/"$date"
fi