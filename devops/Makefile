.PHONY: images/ohmycards/prod images/ohmycards/dev images/scala-sbt clean build

# Versions to use (on scala-sbt image)
SBT_VERSION ?= 1.3.4
SCALA_VERSION ?= 2.13.1

# User and group id to use on all images
USER_ID ?= $(shell id -u)
GROUP_ID ?= $(shell id -g)

# The tag for the different images
OHMYCARDS_SCALA_SBT_TAG ?= latest
OHMYCARDS_DEV_TAG ?= latest
OHMYCARDS_PROD_TAG ?= latest

# Regexp of files to ignore when copying source files to build directory.
IGNORE_BUILD_FILES_REG ?= ^(devops|\.|\.\.|\.g8|\.bloop|\.metals|target|\.git)$$

# The docker command to use
DOCKER ?= docker

# Get's the current version for the ohmycards source code in ./build/
define version
    $(shell sed -n -r -e '/^version/ s/^.*\"(.*)\".*$$/\1/p' <./build/build.sbt | tr -d '\n')
endef

# A nicer echo command
define echo
    @echo ""; echo "=>> "${1}; echo ""
endef

# Builds the ohmycards prod image.
images/ohmycards/prod: build
	$(eval VERSION=$(call version))
	@echo "INFO: version=$(VERSION)"

	$(call echo,"Compiling the source code (generating .tgz artifact).") 
	$(MAKE) -C ./build/ build

	$(call echo,"Copying the Dockerfile on the build context.")
	cp ./docker/ohmycards/prod/Dockerfile ./build/Dockerfile

	$(call echo,"Preparing docker image with artifact.")
	$(DOCKER) \
	  build \
	  --build-arg USER_ID="$(USER_ID)" \
	  --build-arg GROUP_ID="$(GROUP_ID)" \
	  --build-arg OHMYCARDS_VERSION="$(VERSION)"\
	  -t 'ohmycards/ohmycards:$(OHMYCARDS_PROD_TAG)' \
	  build

# Builds the ohmycards dev image.
images/ohmycards/dev: build images/scala-sbt
	cp ./docker/ohmycards/dev/Dockerfile ./build/Dockerfile
	$(DOCKER) \
	  build\
	  --build-arg OHMYCARDS_VERSION="$(VERSION)"\
	  -t 'ohmycards/ohmycards-dev:$(OHMYCARDS_DEV_TAG)' \
	  build

# Builds the base scala-sbt image. See https://hub.docker.com/r/hseeberger/scala-sbt/
images/scala-sbt:
	$(DOCKER) build \
	  --build-arg BASE_IMAGE_TAG="8u212-b04-jdk-stretch" \
	  --build-arg SBT_VERSION="$(SBT_VERSION)" \
	  --build-arg SCALA_VERSION="$(SCALA_VERSION)" \
	  --build-arg USER_ID="$(USER_ID)" \
	  --build-arg GROUP_ID="$(GROUP_ID)" \
	  -t "ohmycards/scala-sbt:$(OHMYCARDS_SCALA_SBT_TAG)" \
	  github.com/hseeberger/scala-sbt.git#:debian

# Cleans the build dir
clean:
	$(call echo,"Cleaning build directory...")
	rm -rf ./build

# Prepares the build directory with the ohmycards contents
build: clean
	$(call echo,"Copying all source to build directory...")
	mkdir ./build
	ls -a .. | grep -vP '$(IGNORE_BUILD_FILES_REG)' | xargs -I {} cp -rv ../{} ./build/{}


