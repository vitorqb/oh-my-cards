#!/bin/bash
USAGE="$0"'

Pushes the built image in the CI

Requires the following env vars:
 - DOCKER_PASSWORD
 - DOCKER_USER
 - DOCKER_IMG_PREFIX'
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
ensure_var DOCKER_PASSWORD $DOCKER_PASSWORD
ensure_var DOCKER_USER $DOCKER_USER
ensure_var DOCKER_IMG_PREFIX $DOCKER_IMG_PREFIX

msg "Logging in to docker..."
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USER" --password-stdin

msg "Pushing..."
docker push "${DOCKER_IMG_PREFIX}:$(get_version)"
