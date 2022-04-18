#!/bin/bash
USAGE="$0"'

Waits for the test container to be ready.
'


# GetOpt configuration
SHORT='h'
OPTS="$(getopt --options $SHORT --name "$0" -- "$@")"
! [ "$?" = 0 ] && echo "$USAGE" 1>&2 && exit 1


# Parsing
while [[ "$#" -gt 0 ]]
do
    case "$1" in
        -h)
            echo "$USAGE"
            exit 0
            ;;
        --)
            shift
            ;;
        *)
            echo "$USAGE" 1>&2
            exit 1
    esac
done


# Setup
REPO_ROOT=$(git rev-parse --show-toplevel)
source ${REPO_ROOT}/dev/utils.bash


# Script
SUCCESS=false
ATTEMPT=0
while [ "$SUCCESS" = "false" ] && [[ "$ATTEMPT" -lt 20 ]]
do
    msg "Waiting for test container [ATTEMPT=$ATTEMPT]..."
    run ${DOCKER} 'exec' functional-test-runner whoami
    if [ "$?" = "0" ]
    then
        msg "Success!"
        SUCCESS=true
    else
        ATTEMPT=$((ATTEMPT+1))
        sleep 10
    fi
done

if [ $SUCCESS = "false" ]
then
    err "Something went wrong and the test container did not start!"
fi
