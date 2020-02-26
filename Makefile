.PHONY: run test ~test

SOURCE_ENV=touch ./.env && . ./.env
SOURCE_ENV_TEST=touch ./test.env && . ./test.env

run:
	$(SOURCE_ENV) && sbt 'run $(RUN_ARGS)'

test:
	$(SOURCE_ENV) && $(SOURCE_ENV_TEST) && sbt 'test'

~test:
	$(SOURCE_ENV) && $(SOURCE_ENV_TEST) && sbt '~test'
