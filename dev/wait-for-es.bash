#!/bin/bash
USAGE="$0"' -n NAME

Waits for ES to be available in a docker container named NAME.

  -n NAME)
    The name of the container where ES is running.'


# Globals
DOCKER="${DOCKER:-docker}"


# GetOpt configuration
SHORT='hn:'
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
        -n)
            NAME="$2"
            shift
            shift
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


# Checks
ensure_var NAME $NAME


# Script
SUCCESS=false
ATTEMPT=0
while [ "$SUCCESS" = "false" ] && [[ "$ATTEMPT" -lt 10 ]]
do
    msg "Waiting for ES [ATTEMPT=$ATTEMPT]..."
    run ${DOCKER} 'exec' "$NAME" curl -v --retry 10 --retry-delay 5 '127.0.0.1:9200/_cluster/health?wait_for_status=yellow&timeout=60s'
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
    err "Something went wrong and ES could not start!"
fi
