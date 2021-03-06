#!/bin/bash
USAGE="$0"'
Runs functional tests for the CI. Must be run at the repo level.'


# 
# Sanity Checks
# 
if [ "$#" != "0" ]
then
    echo "Received unexpected arguments" 1>&2
    exit 1
fi

if [ ! -r "./.git" ]
then
    echo "Must be run on repository root"
    exit 1
fi

function msg() {
    echo ""
    echo "=> $1"
    echo ""
}

# 
# Script
#
msg "Starting ES in the background..."
make devTools/ci/elasticSearch ES_API_PORT="$OHMYCARDS_TEST_ES_PORT" >es.log 2>&1 &

msg "Compiling code for test..."
sbt test:compile

msg "Waiting for ES to be ready..."
./devTools/waitForEs -e "http://127.0.0.1:${OHMYCARDS_TEST_ES_PORT}"

msg "Running functional tests"
make functionalTests | tee test.log

# Exists with the make functionalTests result
exit "${PIPESTATUS[0]}"

