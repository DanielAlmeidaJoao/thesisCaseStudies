#!/bin/bash
for node in $(uniq $OAR_FILE_NODES); do
	oarsh $node -n "killall java" &
done

echo "All processes done!"