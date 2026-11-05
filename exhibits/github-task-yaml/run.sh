#!/bin/zsh
# One run of a GitHub task script as data, on the scratch repository, through the gateway on 8393.
#   run.sh NAME SCRIPT [--reasoner claude]      writes runs/NAME.out and runs/NAME.trace.yaml
set -u
name=$1 script=$2; shift 2
here=${0:A:h}
cd /Users/josh/repos/artie/client/scripts && . ./.env
export ARTIE_TOKEN_LOCALHOST_8393=$ARTIE_TOKEN_LOCALHOST_8392
export JAVA_OPTS="-Djavax.net.ssl.trustStore=$HOME/.cache/artie/tls/trust.p12 -Djavax.net.ssl.trustStorePassword=changeit"
export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/openjdk-22.0.1/Contents/Home
[[ " $* " == *" --reasoner "* ]] && export ANTHROPIC_API_KEY=$(cat ~/.anthropic/api_key)
cd $here && mkdir -p runs
pins=()
[[ -f ${script%.heart.yaml}.pins.yaml ]] && pins=(--pins ${script%.heart.yaml}.pins.yaml)
start=$(date +%s)
/Users/josh/repos/artie/client/artie-cli/build/install/artie/bin/artie run $script owner=JogoShugh stamp=$(date +%m%d-%H%M%S) \
  $pins --exchanges --trace runs/$name.trace.yaml "$@" > runs/$name.out 2>&1
code=$?
echo "$name: exit $code, $(( $(date +%s) - start )) s, $(tail -1 runs/$name.out)"
