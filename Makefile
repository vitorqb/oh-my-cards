.PHONY: run test ~test

run:
	. ./.env && sbt 'run'

test:
	. ./.env && sbt 'test'

~test:
	. ./.env && sbt '~test'
