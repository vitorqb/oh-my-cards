#!/bin/bash
USAGE="$0"'

Starts a container to run tests in circle ci'
set -e


# Globals
IMG=vitorqb23/oh-my-cards-circle-ci-primary:9


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
msg "Running container for functional tests"
run \
    ${DOCKER} \
    run \
    --rm \
    --entrypoint '/bin/bash' \
    --name functional-test-runner \
    -ti \
    -v "$REPO_ROOT:/home/circleci/repo" \
    --network=host \
    "$IMG"
