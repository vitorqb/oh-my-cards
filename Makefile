.PHONY: run test ~test

# Shortcuts methods to source `.env` files.
SOURCE_ENV=touch ./.env && . ./.env
SOURCE_ENV_TEST=touch ./test.env && . ./test.env

# Recipts
run:
	$(SOURCE_ENV) && sbt 'run $(RUN_ARGS)'

test:
	$(SOURCE_ENV) && $(SOURCE_ENV_TEST) && sbt 'test'

~test:
	$(SOURCE_ENV) && $(SOURCE_ENV_TEST) && sbt '~test'

# Creates a .tgz artifact with all needed dependencies
build:
	sbt 'universal:packageZipTarball'
