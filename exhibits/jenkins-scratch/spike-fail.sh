#!/bin/bash
J=$(dirname "$0"); U=http://localhost:8080; T=$(cat $J/token)
c() { curl -sg -u "admin:$T" "$@"; }
c -D $J/trigger2.headers -o /dev/null -X POST "$U/job/services/job/build-service/buildWithParameters?SERVICE=broken"; grep -iE '^(HTTP|Location)' $J/trigger2.headers
Q=$(grep -i '^Location' $J/trigger2.headers | tr -d '\r' | awk '{print $2}')
for i in $(seq 1 15); do c -o $J/queue2.json "${Q}api/json"; jq -e '.executable' $J/queue2.json >/dev/null 2>&1 && break; sleep 2; done
BU=$(jq -r '.executable.url' $J/queue2.json); echo "build: $BU"
sleep 24; c -o $J/build2-done.json "${BU}api/json?tree=number,building,result,duration,timestamp"; jq -c . $J/build2-done.json
echo ROLLUP2; c -o $J/rollup2.json "$U/job/services/api/json?tree=name,url,jobs[name,url,color,inQueue,lastBuild[number,result,timestamp,duration,building],lastSuccessfulBuild[number,timestamp],lastFailedBuild[number,timestamp],lastCompletedBuild[number,result]]"; jq -c . $J/rollup2.json
echo "BAD PARAM (unknown job):"; c -o /dev/null -w '%{http_code}\n' -X POST "$U/job/services/job/nope/buildWithParameters?SERVICE=x"
echo "NO AUTH:"; curl -s -o /dev/null -w '%{http_code}\n' -X POST "$U/job/services/job/build-service/buildWithParameters?SERVICE=x"
echo "PASSWORD BASIC, NO CRUMB:"; curl -s -o /dev/null -w '%{http_code}\n' -u admin:spike-admin -X POST "$U/job/services/job/build-service/buildWithParameters?SERVICE=x"
echo "CONDITIONAL GET (If-None-Match ignored?):"; c -o /dev/null -w '%{http_code}\n' -H 'If-None-Match: "x"' "$U/job/services/api/json?tree=name"
echo "WHOAMI:"; c "$U/me/api/json?tree=id,fullName" | jq -c .
