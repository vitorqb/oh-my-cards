#!/bin/bash
USAGE="$0"' -p PORT -d DATA_DIR -b

Runs an instance of Elastic Search for development/tests.

  -p PORT)
    The port to run.

  -d DATA_DIR)
    The host data directory, or a docker volume name.

  -b)
    If given, forks the process to the background.'
set -e


# Globals
ES_DOCKER_IMAGE=docker.elastic.co/elasticsearch/elasticsearch:7.6.2
DOCKER="${DOCKER:-docker}"


# Defaults
BACKGROUND=0


# GetOpt configuration
SHORT='hp:d:b'
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
        -p)
            PORT="$2"
            shift
            shift
            ;;
        -d)
            DATA_DIR="$2"
            shift
            shift
            ;;
        -b)
            BACKGROUND=1
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
ensure_var PORT "$PORT"
ensure_var DATA_DIR "$DATA_DIR"


# Script
msg "Running ES inside docker..."
CMD=(
    ${DOCKER}
    run
    --rm
    --name ohmycards-es
    -v "${DATA_DIR}:/usr/share/elasticsearch/data"
    -p "${PORT}:9200"
    -e "discovery.type=single-node"
)
if [ "$BACKGROUND" = 1 ]
then
    CMD+=( -d )
fi
CMD+=( "$ES_DOCKER_IMAGE" )
 
run "${CMD[@]}"
