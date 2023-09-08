#!/bin/sh
nthreads=$1
p_folder="andre_paxos/bena-paxos-testing"
client_c="./exec.sh $nthreads 1024 "
n_nodes=$(uniq $OAR_FILE_NODES | wc -l)
processes=$(($n_nodes-1))
i=0
b_p2p_port=34000
b_server_port=35000
local_ip=$(hostname -I | cut -d ' ' -f 1)
name=$(hostname)
membership=""
i=0
#BUILDING MEMBERSHIP
for node in $(uniq $OAR_FILE_NODES); do
	ip=$(oarsh $node "hostname -I | cut -d ' ' -f 1")
	if [ $name != $node ]; then
		b_p2p_port=$(($b_p2p_port + 1))
		b_server_port=$(($b_server_port+1))
		membership="${membership}$ip:$b_p2p_port"
		client_c="${client_c}$ip:$b_server_port"
		i=$(($i+1))
	fi
	if [ $name != $node ] && [ $i -lt $processes ]; then
		membership="$membership,"
		client_c="$client_c,"
	fi
done
client_c="$client_c 50 50"
#echo $membership
#echo $client_c
b_p2p_port=34000
b_server_port=35000
#LAUCHING THE PROCESSES
for node in $(uniq $OAR_FILE_NODES); do
	if [ $name != $node ]; then
		b_p2p_port=$(($b_p2p_port+1))
		b_server_port=$(($b_server_port+1))
		##oarsh $node -n 
		cmd="cd $p_folder/newJava && ./jdk-17/bin/java -cp asdProj2.jar Main p2p_port=$b_p2p_port server_port=$b_server_port initial_membership=$membership" &
		echo $cmd
		#oarsh $node -n $cmd
	fi
done

#EXECUTE CLIENT
sleep 6
echo $client_c
#oarsh $name -n "cd $p_folder/client && $client_c"

read -p "------- PRESS ENTER TO KILL ALL SERVERS -------- "

for node in $(uniq $OAR_FILE_NODES); do
	oarsh $node -n "killall java"
done

