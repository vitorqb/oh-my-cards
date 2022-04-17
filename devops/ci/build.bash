#!/bin/bash
USAGE="$0"'
Builds a docker image. Requires the following env vars:
 - DOCKER_IMG_PREFIX'


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


# Checking
if [ ! -r "./.git" ]
then
    echo "ERRO: MUST BE RUN ON REPOSITORY ROOT" >&2
    exit 1
fi

if [ -z $DOCKER_IMG_PREFIX ]
then
    echo "ERROR: MISSING DOCKER_IMG_PREFIX VAR" >&2
    exit 1
fi


# Utils
function msg() { printf "\e[38;5;81m ==> ${@} \e[0m\n" ; }


# Script
IMGTAG="${DOCKER_IMG_PREFIX}:$(git describe --tags | tr -d '\n')"
( cd devops && make images/ohmycards/prod IMGTAG="$IMGTAG" DOCKER="docker" )
