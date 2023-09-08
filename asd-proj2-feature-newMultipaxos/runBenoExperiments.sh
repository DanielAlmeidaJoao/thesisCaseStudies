#!/bin/sh
oldNetwork=$1
if [ -z $oldNetwork ] || [ $oldNetwork -lt 0 ]; then
  echo "ENTER 0 FOR OLD BABEL NETWORK, AND GREATER THAN ZERO FOR NEW NETWORKS"
  exit 1
fi

for test in 1 2 3; do
	for client in 1 2 3 4 5 6 7 8 9 10; do
		echo "STARING TEST ${test} WITH ${client} CLIENTS. OLDNETWORK: ${oldNetwork}"
		if [ $oldNetwork -eq 0 ]; then
			./runTestOldBabel.sh $client $test
		elif [ $oldNetwork -gt 0 ]; then
			./runTest.sh $client $test
		else
			echo "Input should be a non-negative number."
		fi
		echo "FINISHED TEST ${test} WITH ${client} CLIENTS. OLDNETWORK: ${oldNetwork}"
		sleep 5
	done
done

echo "ALL TESTS DONE!"