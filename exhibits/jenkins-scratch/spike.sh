#!/bin/bash
J=$(dirname "$0"); U=http://localhost:8080; T=$(cat $J/token)
c() { curl -sg -u "admin:$T" "$@"; }
c -o $J/folder0.json -w 'folder %{http_code}\n' "$U/job/services/api/json?tree=name,_class,jobs[name,_class,url]"; jq -c . $J/folder0.json
c -o $J/job0.json -w 'job %{http_code}\n' "$U/job/services/job/build-service/api/json?tree=name,_class,buildable,inQueue,nextBuildNumber,property[parameterDefinitions[name,type,defaultParameterValue[value]]]"; jq -c . $J/job0.json
echo TRIGGER; c -D $J/trigger.headers -o $J/trigger.body -X POST "$U/job/services/job/build-service/buildWithParameters?SERVICE=payments"; grep -iE '^(HTTP|Location)' $J/trigger.headers; echo "body bytes: $(wc -c < $J/trigger.body)"
Q=$(grep -i '^Location' $J/trigger.headers | tr -d '\r' | awk '{print $2}')
c -o $J/queue0.json "${Q}api/json"; echo "QUEUE first look:"; jq -c . $J/queue0.json
for i in $(seq 1 15); do c -o $J/queue.json "${Q}api/json"; jq -e '.executable' $J/queue.json >/dev/null 2>&1 && break; sleep 2; done; echo "QUEUE after $i polls"; jq -c . $J/queue.json
BU=$(jq -r '.executable.url' $J/queue.json)
echo BUILDING; c -o $J/build-running.json "${BU}api/json?tree=number,building,result,timestamp,duration,estimatedDuration,url,queueId"; jq -c . $J/build-running.json
sleep 22; echo DONE; c -o $J/build-done.json "${BU}api/json?tree=number,building,result,timestamp,duration,estimatedDuration,url,queueId,actions[parameters[name,value]]"; jq -c . $J/build-done.json
echo ROLLUP; c -o $J/rollup.json "$U/job/services/api/json?tree=name,url,jobs[name,url,color,inQueue,lastBuild[number,result,timestamp,duration,building],lastSuccessfulBuild[number,timestamp],lastFailedBuild[number,timestamp],lastCompletedBuild[number,result]]"; jq -c . $J/rollup.json
echo HEADERS; c -D - -o /dev/null "$U/job/services/api/json?tree=name" | grep -iE '^(HTTP|ETag|Cache-Control|Content-Type|X-Jenkins|Last-Modified|Expires)'
echo "QUEUE ITEM LATER (after build started):"; c -o $J/queue-late.json -w '%{http_code}\n' "${Q}api/json"; jq -c . $J/queue-late.json 2>/dev/null | head -c 300; echo
