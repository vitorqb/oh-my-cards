

DOCKER ?= sudo -A docker

DATA_DIR ?= ${HOME}/.ohmycards_devtools

ELASTIC_PORT1 ?= 9200
ELASTIC_PORT2 ?= 9300

elasticSearch:
	mkdir -p $(DATA_DIR)/elasticSearchData
	$(DOCKER) run \
	  --rm \
	  --name ohmycards-elasticsearch \
	  -v '$(DATA_DIR)/elasticSearchData:/usr/share/elasticsearch/data' \
	  -p $(ELASTIC_PORT1):9200 \
	  -p $(ELASTIC_PORT2):9300 \
	  -e "discovery.type=single-node" \
	  docker.elastic.co/elasticsearch/elasticsearch:7.6.2
