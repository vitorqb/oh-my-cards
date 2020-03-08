ARG SCALA_SBT_TAG=latest

FROM ohmycards/scala-sbt:${SCALA_SBT_TAG}

# Adds base packages
RUN apt-get update && apt-get install make

# Exposes port
EXPOSE 9000

# Sets working directory
WORKDIR /sources

# Copies the source files
COPY . .

# Install all dependencies
RUN sbt 'compile'

# Entrypoint is make run
ENTRYPOINT make
CMD ["run"]
