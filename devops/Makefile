.PHONY: images/ohmycards/prod clean builddir build

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
$(addprefix $(BUILD_DIR)/,$(SOURCE_FILES))
endef

# A nicer echo command
define echo
@echo ""; echo "=>> "${1}; echo ""
endef

# get's the current version
define version
$(shell cd $(SOURCE_DIR) && git describe --tags | tr -d '\n')
endef

# Builds the ohmycards prod image.
images/ohmycards/prod: build
	$(call echo,"Injecting version...")
	echo "" >>$(BUILD_DIR)/conf/application.conf
	echo '# VERSION INJECTED BY make images/ohmycards/prod'>>$(BUILD_DIR)/conf/application.conf
	echo 'app.version = "$(call version)"' >>$(BUILD_DIR)/conf/application.conf
	echo "" >>$(BUILD_DIR)/conf/application.conf

	$(call echo,"Compiling the source code (generating .tgz artifact).") 
	$(MAKE) -C $(BUILD_DIR) build

	$(call echo,"Copying the Dockerfile on the build context.")
	cp ./docker/ohmycards/prod/* $(BUILD_DIR)/

	$(call echo,"Preparing docker image with artifact.")
	$(DOCKER) \
	  build \
	  --build-arg USER_ID="$(USER_ID)" \
	  --build-arg GROUP_ID="$(GROUP_ID)" \
	  --build-arg OHMYCARDS_VERSION="$(version)"\
	  -t '$(IMGTAG)' \
	  $(BUILD_DIR)

# Cleans the build dir
clean:
	$(call echo,"Cleaning build directory...")
	rm -rf $(BUILD_DIR)

# Prepares the build directory with the ohmycards contents
$(BUILD_DIR)/%:
	cp -rv $(SOURCE_DIR)/$* $(BUILD_DIR)/$*

# Creates the build directory with all files
$(BUILD_DIR): build
build: builddir $(BUILD_FILES)

# Creates an (empty) build directory.
builddir:
	mkdir -p $(BUILD_DIR)
