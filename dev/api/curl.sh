#!/bin/bash
USAGE="$0"' -- [...curlargs]

Runs a curl request using token from env vars.

Try `source ./dev/api/setup.sh` before running this :)

  ...curlargs)
    Arguments passed to curl.
'


# GetOpt configuration
SHORT='h'
OPTS="$(getopt --options $SHORT --name "$0" -- "$@")"
! [ "$?" = 0 ] && echo "$USAGE" 1>&2 && exit 1

CURL_ARGS=""

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
            CURL_ARGS="$@"
            break
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
ARGS=(
    curl
    -s
    '-HContent-Type: application/json'
    "-HAuthorization: Bearer $OHMYCARDS_API_TOKEN"
)
ARGS+=( ${CURL_ARGS[@]} )
run "${ARGS[@]}"

