.PHONY: run test ~test tests unitTests functionalTests

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

unitTests:
	sbt 'unitTests'

functionalTests:
	sbt 'functionalTests'

test: tests
tests:
	sbt 'test'

~test:
	sbt '~test'

sbt:
	sbt

# Delegates to devTools
devTools/%:
	make -C devTools $(@:devTools/%=%)

# Creates a .tgz artifact with all needed dependencies
build:
	sbt 'universal:packageZipTarball'
