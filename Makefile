.PHONY: run test ~test tests unitTests functionalTests lint/check lint build sbt

# 
# Arguments
# 
SBT_RUN_ARGS=''
DOCKER ?= sudo -A docker

# 
# Recipts
# 
run:
	sbt 'run $(SBT_RUN_ARGS)'

unitTests: unitTest
unitTest:
	sbt 'unitTests'

functionalTests: functionalTest
functionalTest: 
	sbt 'functionalTests'

ci/functionalTests: ci/functionalTest
ci/functionalTest:
	devops/ci/run-functional-tests.bash

test: tests
tests:
	sbt 'test'

~test:
	sbt '~test'

sbt:
	sbt

# delegates to devTools
devTools/%:
	make -C devTools $(@:devTools/%=%)

# creates a .tgz artifact with all needed dependencies
build:
	sbt 'universal:packageZipTarball'

lint/check:
	sbt scalafmtCheckAll

lint:
	sbt scalafmtAll
