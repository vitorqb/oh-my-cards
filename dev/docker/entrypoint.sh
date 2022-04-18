#!/bin/bash
USAGE="$0"' PLAY_RUN_ARGS
Provides default arguments for the Play entrypoint. All `PLAY_RUN_ARGS` are
appended to the play entrypoint.

For example, you can change the port using
'"$0"' -Dhttp.port=9999'

# getopt
SHORT='h:'
LONG='help:'
OPTS="$(getopt --options $SHORT --long $LONG --name "$0" -- "$@")"
! [ "$?" = 0 ] && echo "$USAGE" 1>&2 && exit 1
eval set -- "$OPTS"

# Parses params
while [[ "$#" -gt 0 ]]
do
    key="$1"
    case "$key" in
        -h|--help)
            echo "$USAGE"
            exit 0
            ;;
        --)
            shift
            break
            ;;
        *)
            echo "$USAGE" 1>&2
            exit 1
    esac
done

/home/ohmycards/run -Dhttp.port=8000 -Dplay.evolutions.db.default.autoApply=true "$@"
