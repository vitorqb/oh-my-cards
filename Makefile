.PHONY: run test ~test

SOURCE_ENV=touch ./.env && . ./.env

run:
	$(SOURCE_ENV) && sbt 'run'

test:
	$(SOURCE_ENV) && sbt 'test'

~test:
	$(SOURCE_ENV) && sbt '~test'
