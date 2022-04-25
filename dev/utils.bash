#!/bin/bash

RED='\e[31m'
CYAN='\e[36m'
NC='\e[0m'

DOCKER=${DOCKER:-docker}

function msg() {
    echo -e "" >&2
    echo -e "${CYAN}=> ${@}${NC}" >&2
    echo -e "" >&2
}

function errmsg() {
    echo -e "" >&2
    echo -e "${RED}ERROR: ${@}${NC}" >&2
    echo -e "" >&2
}

function err() {
    errmsg "$@"
    exit 1
}

function run() {
    echo -e "${CYAN}=> => ${@}${NC}" >&2
    "$@"
}

function ensure_var() {
    if [ -z "$2" ]
    then
        echo "ERROR: MISSING VARIABLE $1" >&2
        exit 1
    fi
}

function get_version() {
    git describe --tags | tr -d '\n'
}

function yes_or_no() {
    read -p "$1" -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]
    then
        return 1
    else
        return 0
    fi
}

function cleanup_dir() {
    if [ -d "$1" ]
    then
        if ! yes_or_no "Are you sure you want to delete $1 ?"
        then
            err "Aborting due to user..."
        fi
        run rm -rf "$1"
    fi
}
