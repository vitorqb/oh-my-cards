#!/bin/bash
USAGE="$0"'
Publishes a docker image.
Requires the following env vars:
 - DOCKER_PASSWORD
 - DOCKER_USER
 - DOCKER_IMG_PREFIX'


# GetOpt configuration
SHORT='ht:'
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
        -t)
            TAG="$2"
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


# Checking
if [ -z "$DOCKER_PASSWORD" ]
then
    echo "ERROR: MISSING DOCKER_PASSWORD!" >&2
    exit 1
fi

if [ -z "$DOCKER_USER" ]
then
    echo "ERROR: MISSING DOCKER_USER!" >&2
    exit 1
fi

if [ -z "$DOCKER_IMG_PREFIX" ]
then
    echo "ERROR: MISSING DOCKER_IMG_PREFIX!" >&2
    exit 1
fi

if [ ! -r "./.git" ]
then
    echo "Must be run on repository root" >&2
    exit 1
fi


# Script
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USER" --password-stdin
export IMGTAG="${DOCKER_IMG_PREFIX}:$(git describe --tags | tr -d '\n')"
docker push "$IMGTAG"
