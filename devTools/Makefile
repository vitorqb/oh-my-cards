.PHONY: elasticSearch ci/elasticSearch

# Docker command to run
DOCKER ?= sudo -A docker

# ElasticSearch Configuration
ES_DATA_DIR ?= ${HOME}/.ohmycards_devtools/elasticSearchData
ES_DOCKER_IMAGE ?= docker.elastic.co/elasticsearch/elasticsearch:7.6.2
ES_API_PORT ?= 9200

# Runs ElasticSearch focused on development
elasticSearch:
	mkdir -p $(ES_DATA_DIR)
	$(DOCKER) run \
	  --rm \
	  --name ohmycards-elasticsearch \
	  -v '$(ES_DATA_DIR):/usr/share/elasticsearch/data' \
	  -p $(ES_API_PORT):9200 \
	  -e "discovery.type=single-node" \
          '$(ES_DOCKER_IMAGE)'

# Runs ElasticSearch focused on CIs
ci/elasticSearch:
	$(DOCKER) run \
          --rm \
          -v 'elasticSearch:/usr/share/elasticsearch/data' \
          -p $(ES_API_PORT):9200 \
          -e "discovery.type=single-node" \
          '$(ES_DOCKER_IMAGE)'

