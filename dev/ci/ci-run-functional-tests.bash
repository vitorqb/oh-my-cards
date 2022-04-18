#!/bin/bash
USAGE="$0"'

Runs functional tests in the ci.
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
msg "Running functional tests..."
run \
    docker \
    exec \
    functional-test-runner \
    bash -c "cd /home/circleci/repo && source .env_test_example && sbt functionalTests"
