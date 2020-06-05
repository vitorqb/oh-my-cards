.PHONY: run test ~test tests unitTests functionalTests

# 
# Arguments
# 
SBT_RUN_ARGS=''
ENV_FILE="./.env"
TEST_ENV_FILE="./.env.test"
DOCKER ?= sudo -A docker

# 
# Recipts
# 
envfiles:
	[ ! -f $(ENV_FILE) ] && touch $(ENV_FILE) || :
	[ ! -f $(TEST_ENV_FILE) ] && touch $(TEST_ENV_FILE) || :

run: envfiles
	. $(ENV_FILE) && sbt 'run $(SBT_RUN_ARGS)'

unitTests: envfiles
	. $(TEST_ENV_FILE) && sbt 'unitTests'

functionalTests: envfiles
	. $(TEST_ENV_FILE) && sbt 'functionalTests'

test: tests
tests:
	. $(TEST_ENV_FILE) && sbt 'test'

~test: envfiles
	. $(TEST_ENV_FILE) && sbt '~test'

sbt: envfiles
	. $(TEST_ENV_FILE) && sbt

# Delegates to devTools
devTools/%: envfiles
	. $(TEST_ENV_FILE) && make -C devTools $(@:devTools/%=%)

# Creates a .tgz artifact with all needed dependencies
build:
	sbt 'universal:packageZipTarball'
