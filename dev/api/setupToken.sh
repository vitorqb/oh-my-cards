#!/bin/bash

# Setup
REPO_ROOT=$(git rev-parse --show-toplevel)
source ${REPO_ROOT}/dev/utils.bash

token=$(${REPO_ROOT}/dev/api/getToken.sh)
if [ -z $token ]
then
    errmsg "Something went wrong when getting the token :("
else
    export OHMYCARDS_API_TOKEN="$token"
    msg "Setup finished! OHMYCARDS_API_TOKEN=$OHMYCARDS_API_TOKEN"
fi
