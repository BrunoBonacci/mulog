define helpdoc
# +----------------------------------------------------------------------------+
# |                                                                            |
# |                                 AW  ,,                                     |
# |                                ,M'`7MM                                     |
# |                                MV   MM                                     |
# |                  MM    MM     AW    MM  ,pW"Wq.   .P"Ybmmm                 |
# |                  MM    MM    ,M'    MM 6W'   `Wb :MI  I8                   |
# |                  MM    MM    MV     MM 8M     M8  WmmmP"                   |
# |                  MM    MM   AW      MM YA.   ,A9 8M                        |
# |                  MVbgd"'Ybo,M'    .JMML.`Ybmd9'   YMMMMMb                  |
# |                  M.        MV                    6'     dP                 |
# |                  M8       AW                     Ybmmmd'                   |
# |                                                                            |
# +----------------------------------------------------------------------------+
#
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
# - deploy:  it deploys the jars into clojar (FOR RELEASE ONLY)
# - ancient: updates all the dependencies
# - all:     same as `make ancient clean build`
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
all: ancient clean build


#
# Build
#
#build: build-core build-els build-kafka build-kinesis build-slack build-zipkin build-examples
build: build-core build-els build-kafka build-slack build-zipkin build-examples
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
mulog-kafka/target/mulog*.jar: $(kafka_src)
- @printf "#\n# Building mulog-kafka\n#\n"
- (cd mulog-kafka; lein do check, midje, install)


#
# Build kinesis
#
kinesis_src = $(shell find mulog-kinesis/project.clj mulog-kinesis/src mulog-kinesis/resources -type f)
build-kinesis: build-core mulog-kinesis/target/mulog*.jar
mulog-kinesis/target/mulog*.jar: $(kinesis_src)
- @printf "#\n# Building mulog-kinesis\n#\n"
- (cd mulog-kinesis; lein do check, midje, install)


#
# Build slack
#
slack_src = $(shell find mulog-slack/project.clj mulog-slack/src mulog-slack/resources -type f)
build-slack: build-core mulog-slack/target/mulog*.jar
mulog-slack/target/mulog*.jar: $(slack_src)
- @printf "#\n# Building mulog-slack\n#\n"
- (cd mulog-slack; lein do check, midje, install)


#
# Build zipkin
#
zipkin_src = $(shell find mulog-zipkin/project.clj mulog-zipkin/src mulog-zipkin/resources -type f)
build-zipkin: build-core mulog-zipkin/target/mulog*.jar
mulog-zipkin/target/mulog*.jar: $(zipkin_src)
- @printf "#\n# Building mulog-zipkin\n#\n"
- (cd mulog-zipkin; lein do check, midje, install)


#
# Build examples
#
build-examples: build-examples-disruptions

#
# Build Disruption example
#
disruptions_src = $(shell find examples/roads-disruptions/project.clj examples/roads-disruptions/src examples/roads-disruptions/resources -type f)
build-examples-disruptions: build-core build-els build-kafka build-zipkin examples/roads-disruptions/target/roads-disruptions*.jar
examples/roads-disruptions/target/roads-disruptions*.jar: $(disruptions_src)
- @printf "#\n# Building examples/roads-disruptions\n#\n"
- (cd examples/roads-disruptions; lein do check, test, jar)


#
# Deploy jars into clojars
#
.PHONY: deploy
deploy:
- @printf "#\n# Deploying jars \n#\n"
- (cd mulog-core;                 lein deploy clojars)
- (cd mulog-elasticsearch;        lein deploy clojars)
- (cd mulog-kafka;                lein deploy clojars)
- (cd mulog-kinesis               lein deploy clojars)
- (cd mulog-slack;                lein deploy clojars)
- (cd mulog-zipkin;               lein deploy clojars)


#
# update dependencies in project.clj
#
.PHONY: ancient
ancient:
- @printf "#\n# Updating dependencies \n#\n"
- (cd mulog-core;                 lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-elasticsearch;        lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-kafka;                lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-kinesis;              lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-slack;                lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-zipkin;               lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd examples/roads-disruptions; lein with-profile tools ancient upgrade ; lein do clean, install)


#
# Clean target directories
#
.PHONY: clean
clean:
- @printf "#\n# Cleaning \n#\n"
- (cd mulog-core;                 rm -fr target)
- (cd mulog-elasticsearch;        rm -fr target)
- (cd mulog-kafka;                rm -fr target)
- (cd mulog-kinesis;              rm -fr target)
- (cd mulog-slack;                rm -fr target)
- (cd mulog-zipkin;               rm -fr target)
- (cd examples/roads-disruptions; rm -fr target)
