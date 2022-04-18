#!/bin/bash
USAGE="$0"' [-d BUILD_DIR] [-t IMG_TAG] [-u USER_ID] [-g GROUP_ID]

Builds the application (into a .jar file)

  -d BUILD_DIR)
    The build directory (defaults to ./build)

  -t IMG_TAG)
    The tag to give to the image (defaults to ohmycards:latest)

  -u USER_ID)
    The user id to use on the docker image (defaults to `id -u`)

  -g GROUP_ID)
    The group id to use on the docker image (defaults to `id -g`)
'
set -e


# Defaults
BUILD_DIR="./build"
IMG_TAG="ohmycards:latest"
USER_ID=$(id -u)
GROUP_ID=$(id -g)


# GetOpt configuration
SHORT='hb:t:u:g:'
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
        -b)
            BUILD_DIR="$2"
            shift
            shift
            ;;
        -t)
            IMG_TAG="$2"
            shift
            shift
            ;;
        -u)
            USER_ID="$2"
            shift
            shift
            ;;
        -g)
            GROUP_ID="$2"
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


# Setup
REPO_ROOT=$(git rev-parse --show-toplevel)
source ${REPO_ROOT}/dev/utils.bash
trap "pushd -0 && dirs -c" EXIT


# Helpers
SOURCE_FILES=(app build.sbt conf Makefile project test .java-version)


# Script
msg "Cleaning up..."
run cleanup_dir "$BUILD_DIR"
run mkdir -p "$BUILD_DIR"


msg "Copying files to build directory..."
for f in ${SOURCE_FILES[@]}
do
    cp -rv ${REPO_ROOT}/${f} ${BUILD_DIR}/${f}
done


msg "Injecting version..."
run pushd "$BUILD_DIR"
run bash -c 'echo "" >>conf/application.conf'
run bash -c "echo '# VERSION INJECTED BY build.bash' >>conf/application.conf"
run bash -c "echo 'app.version = \"$(cd $REPO_ROOT && get_version)\"' >>conf/application.conf"
run bash -c 'echo "" >>conf/application.conf'
run tail -n5 conf/application.conf
run popd


msg "Compiling the source code (generating .tgz artifact)..."
run pushd "$BUILD_DIR"
run sbt universal:packageZipTarball
run popd


msg "Copying the Dockerfile on the build context..."
run cp -v $REPO_ROOT/dev/docker/* ${BUILD_DIR}/


msg "Creating the docker image..."
run \
    ${DOCKER} \
    build \
    --build-arg USER_ID="${USER_ID}" \
    --build-arg GROUP_ID="${GROUP_ID}" \
    --build-arg OHMYCARDS_VERSION="$(get_version)" \
    -t "$IMG_TAG" \
    $BUILD_DIR
