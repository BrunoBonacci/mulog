define helpdoc
# +----------------------------------------------------------------------------+
# |                                                                            |
# |                         ----==| μ / L O G |==----                          |
# |                                                                            |
# +----------------------------------------------------------------------------+
#
# It requires GNU make 3.82+
#
# Install with:
#   brew install make
#   echo "alias make=gmake" >> ~/.profile
#
# Run with:
#   $ make <target> ... <target>
#
# Examples:
#   $ make all
#   $ make clean build
#
# Available targets:
#
# - clean:   removes compilation outputs
# - build:   compiles and run unit tests for each modules
# - all:     same as `make clean build`
#
endef

#
# Recipe prefix requires GNU make 3.82+
#
.RECIPEPREFIX := -


#
# Help
#
.PHONY: help
export helpdoc
help:
- echo "$$helpdoc"


#
# Preparing all
#
all: clean build


#
# Build
#
build: build-core build-els
- @printf "#\n# Building μ/log Completed!\n#\n"


#
# Build Core
#
core_src = $(shell find mulog-core/project.clj mulog-core/src mulog-core/resources -type f)
build-core: mulog-core/target/mulog*.jar
mulog-core/target/mulog*.jar: $(core_src)
- @printf "#\n# Building mulog-core\n#\n"
- (cd mulog-core; lein do check, midje, install)


#
# Build elasticsearch
#
els_src = $(shell find mulog-elasticsearch/project.clj mulog-elasticsearch/src mulog-elasticsearch/resources -type f)
build-els: build-core mulog-elasticsearch/target/mulog*.jar
mulog-elasticsearch/target/mulog*.jar: $(els_src)
- @printf "#\n# Building mulog-elasticsearch\n#\n"
- (cd mulog-elasticsearch; lein do check, midje, install)


#
# Build kafka
#
kafka_src = $(shell find mulog-kafka/project.clj mulog-kafka/src mulog-kafka/resources -type f)
build-kafka: build-core mulog-kafka/target/mulog*.jar
mulog-kafka/target/mulog*.jar: $(els_src)
- @printf "#\n# Building mulog-kafka\n#\n"
- (cd mulog-kafka; lein do check, midje, install)


#
# Clean target directories
#
.PHONY: clean
clean:
- @printf "#\n# Cleaning \n#\n"
- (cd mulog-core;           rm -fr target)
- (cd mulog-elasticsearch;  rm -fr target)
- (cd mulog-kafka;          rm -fr target)
