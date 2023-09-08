#!/usr/bin/env bash
now=$(date)
java -Dlog4j.configurationFile=log4j2.xml \
		-DlogFilename=l4j_dan \
		-cp paxos-client.jar site.ycsb.Client -t -s -P config.properties \
		-threads 3 -p fieldlength=1024 \
		-p hosts=localhost:35000,localhost:35001,localhost:35002 \
		-p readproportion=50 -p insertproportion=50 \
		| tee "$now.log"
