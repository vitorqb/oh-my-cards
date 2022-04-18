#!/bin/bash
USAGE="$0"'

Starts ES in the CI'
set -e


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
msg "Running elasticsearch for the CI"
run "${REPO_ROOT}/dev/start-es.bash" -p 9999 -d es-ci
