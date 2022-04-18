#!/bin/bash

RED='\e[31m'
CYAN='\e[36m'
NC='\e[0m'

DOCKER=${DOCKER:-docker}

function msg() {
    echo -e ""
    echo -e "${RED}=> ${@}${NC}"
    echo -e ""
}

function err() {
    echo -e ""
    echo -e "${RED}ERROR: ${@}${NC}"
    echo -e ""
    exit 1
}

function run() {
    echo -e "${CYAN}=> => ${@}${NC}"
    "$@"
}

function ensure_var() {
    if [ -z "$2" ]
    then
        echo "ERROR: MISSING VARIABLE $1" >&2
        exit 1
    fi
}
