.PHONY: images/ohmycards/prod images/ohmycards/dev images/scala-sbt clean build version

# User and group id to use on all images
USER_ID ?= $(shell id -u)
GROUP_ID ?= $(shell id -g)

# The tag for the prod image
IMGTAG ?= ohmycards:latest

# The docker command to use
DOCKER ?= sudo -A docker

# The build directory to use
BUILD_DIR ?= ./build

# The directory with the source files
SOURCE_DIR ?= ..

# The source files to use when building
SOURCE_FILES = app build.sbt conf Makefile project test

# Same as SOURCE_FILES, but prefixed by build/
define BUILD_FILES
	$(addprefix build/,$(SOURCE_FILES))
endef

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
	$(call "Version: $(VERSION)")

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

# Cleans the build dir
clean:
	$(call echo,"Cleaning build directory...")
	rm -rf $(BUILD_DIR)

# Prepares the build directory with the ohmycards contents
build/%:
	cp -rv $(SOURCE_DIR)/$* $(BUILD_DIR)/$*

build: builddir $(BUILD_FILES)

builddir:
	mkdir -p $(BUILD_DIR)

# Simply echos the version
version:
	@echo $(call version) | tr -d '\n'
