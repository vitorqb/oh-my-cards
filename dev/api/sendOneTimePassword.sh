#!/bin/bash
USAGE="$0"'

Sends a request for a oneTimePassword to user user@email.com.'
set -e


# Globals
USER="user@email.com"


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
msg "Sending request for oneTimePassword"
run curl -s \
    -H'Content-Type: application/json' \
    --data "{\"email\":\"$USER\"}" \
    '127.0.0.1:9000/v1/auth/oneTimePassword'
