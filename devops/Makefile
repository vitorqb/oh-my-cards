.PHONY: images/ohmycards/prod images/ohmycards/dev images/scala-sbt clean build version

# Versions to use (on scala-sbt image)
SBT_VERSION ?= 1.3.4
SCALA_VERSION ?= 2.13.1

# User and group id to use on all images
USER_ID ?= $(shell id -u)
GROUP_ID ?= $(shell id -g)

# The tag for the different images
OHMYCARDS_SCALA_SBT_TAG ?= latest
OHMYCARDS_DEV_TAG ?= latest

# The tag for the prod image
IMGTAG ?= ohmycards:latest

# Regexp of files to ignore when copying source files to build directory.
IGNORE_BUILD_FILES_REG ?= ^(devops|\.|\.\.|\.g8|\.bloop|\.metals|target|\.git)$$

# The docker command to use
DOCKER ?= sudo docker

# The build directory to use
BUILD_DIR ?= ./build

# Get's the current version for the ohmycards source code in ./build/
define version
$(shell sed -n -r -e '/^version/ s/^.*\"(.*)\".*$$/\1/p' <$(BUILD_DIR)/build.sbt | tr -d '\n')
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
	$(MAKE) -C $(BUILD_DIR) build

	$(call echo,"Copying the Dockerfile on the build context.")
	cp ./docker/ohmycards/prod/* $(BUILD_DIR)/

	$(call echo,"Preparing docker image with artifact.")
	$(DOCKER) \
	  build \
	  --build-arg USER_ID="$(USER_ID)" \
	  --build-arg GROUP_ID="$(GROUP_ID)" \
	  --build-arg OHMYCARDS_VERSION="$(VERSION)"\
	  -t '$(IMGTAG)' \
	  $(BUILD_DIR)

# Builds the ohmycards dev image.
images/ohmycards/dev: build images/scala-sbt
	cp ./docker/ohmycards/dev/Dockerfile $(BUILD_DIR)/Dockerfile
	$(DOCKER) \
	  build\
	  --build-arg OHMYCARDS_VERSION="$(VERSION)"\
	  -t 'vitorqb23/ohmycards-dev:$(OHMYCARDS_DEV_TAG)' \
	  $(BUILD_DIR)

# Builds the base scala-sbt image. See https://hub.docker.com/r/hseeberger/scala-sbt/
images/scala-sbt:
	$(DOCKER) build \
	  --build-arg BASE_IMAGE_TAG="8u212-b04-jdk-stretch" \
	  --build-arg SBT_VERSION="$(SBT_VERSION)" \
	  --build-arg SCALA_VERSION="$(SCALA_VERSION)" \
	  --build-arg USER_ID="$(USER_ID)" \
	  --build-arg GROUP_ID="$(GROUP_ID)" \
	  -t "vitorqb23/ohmycards-scala-sbt:$(OHMYCARDS_SCALA_SBT_TAG)" \
	  github.com/hseeberger/scala-sbt.git#:debian

# Cleans the build dir
clean:
	$(call echo,"Cleaning build directory...")
	rm -rf $(BUILD_DIR)
	mkdir -p $(BUILD_DIR)

# Prepares the build directory with the ohmycards contents
build: clean
	$(call echo,"Copying all source to build directory...")
	ls -a .. | grep -vP '$(IGNORE_BUILD_FILES_REG)' | xargs -I {} cp -rv ../{} $(BUILD_DIR)/{}

# Simply echos the version
version:
	@echo $(call version) | tr -d '\n'
