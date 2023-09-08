#!/bin/bash

usage() { echo "Usage: $(basename $0) [-l local] [-d docker]"; }




while getopts 'ldf: ' opt; do
  case "$opt" in
  l)
    local=1
    ;;
  d)
    docker=1
   ;;
  f)
    filename="$OPTARG"
    ;;
  ?)
    usage
    exit 1
    ;;
  esac
done

if [ -n "$local" ] && [ -n "$docker" ]; then
    echo "You can only run local or docker."
    exit 1
fi
if [ -n "$docker" ] && [ -n "$local" ]; then
    echo "You can only run local or docker."
    exit 1
fi

if [ -z "$filename" ]; then
    echo "Missing Filename."
    exit 1
fi

if [ -z "$docker" ] && [ -z "$local" ]; then
    echo "Missing -l or -d."
    exit 1
fi

shift
cd client || echo "Failed cd client" || exit 1

if [ -n "$local" ]; then
    ./exec.sh "$@"
fi

if [ -n "$local" ]; then
    docker build -t ycsb . > /dev/null
    docker run -it ycsb "$@"    
fi


