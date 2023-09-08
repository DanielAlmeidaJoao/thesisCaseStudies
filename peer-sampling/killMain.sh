#!/bin/bash
processes=$1

if [ -z $processes ] || [ $processes -lt 1 ]; then
  echo "please indicate a number of processes of at least one"
  exit 0
fi

echo "Java processes: $processes"
echo "================"

# Use the jps command to find all Java processes and their full process names
java_procs=$(jps -l)
first=""
i=0
# Loop through each Java process and extract its PID and process name
while read -r line; do
    pid=$(echo "$line" | awk '{print $1}')
    name=$(echo "$line" | awk '{$1=""; print $0}' | xargs)
    #echo "PID: $pid  Name: $name"
    if [ $name == "Main" ]; then

        if [ -z "$first" ]
        then
            first=$pid
            #echo "FIRST $first"
        else
            m="kill $pid"
            #echo "KILLING $m"
            $m
            i=$(($i+1))
            if [ $i == $processes ]; then
                exit 0
            fi
        fi
        #echo "RUNING"
    fi
done <<< "$java_procs"
