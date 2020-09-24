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
# |                  MVbgd"'Mb ,M'    .JMML.`Ybmd9'   YMMMMMb                  |
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
# - prep:    prep for release: same as `make clean ancient format build`
# - deploy:  it deploys the jars into clojar (FOR RELEASE ONLY)
# - ancient: updates all the dependencies
# - format:  re-format source code across all modules
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
# all
#
all:  clean build


#
# prep
#
prep: clean ancient format build


#
# Build
#
MULOG_MODULES := build-core build-json build-jvm-metrics
MULOG_MODULES += build-cloudwatch build-els build-kafka build-kinesis build-prometheus
MULOG_MODULES += build-slack build-zipkin build-examples
build: $(MULOG_MODULES)
- @printf "#\n# Building μ/log Completed!\n#\n"


#
# Build Core
#
core_src = $(shell find mulog-core/project.clj mulog-core/src mulog-core/resources -type f)
build-core: mulog-core/target/mulog*.jar
mulog-core/target/mulog*.jar: $(core_src)
- @printf "#\n# Building mulog-core\n#\n"
- (cd mulog-core; lein do check, test, install)


#
# Build JSON
#
json_src = $(shell find mulog-json/project.clj mulog-json/src mulog-json/resources -type f)
build-json: build-core mulog-json/target/mulog*.jar
mulog-json/target/mulog*.jar: $(json_src)
- @printf "#\n# Building mulog-json\n#\n"
- (cd mulog-json; lein do check, test, install)


#
# Build elasticsearch
#
els_src = $(shell find mulog-elasticsearch/project.clj mulog-elasticsearch/src mulog-elasticsearch/resources -type f)
build-els: build-core mulog-elasticsearch/target/mulog*.jar
mulog-elasticsearch/target/mulog*.jar: $(els_src)
- @printf "#\n# Building mulog-elasticsearch\n#\n"
- (cd mulog-elasticsearch; lein do check, test, install)


#
# Build jvm-metrics
#
jvm-metrics_src = $(shell find mulog-jvm-metrics/project.clj mulog-jvm-metrics/src mulog-jvm-metrics/resources -type f)
build-jvm-metrics: build-core mulog-jvm-metrics/target/mulog*.jar
mulog-jvm-metrics/target/mulog*.jar: $(jvm-metrics_src)
- @printf "#\n# Building mulog-jvm-metrics\n#\n"
- (cd mulog-jvm-metrics; lein do check, test, install)


#
# Build kafka
#
kafka_src = $(shell find mulog-kafka/project.clj mulog-kafka/src mulog-kafka/resources -type f)
build-kafka: build-core mulog-kafka/target/mulog*.jar
mulog-kafka/target/mulog*.jar: $(kafka_src)
- @printf "#\n# Building mulog-kafka\n#\n"
- (cd mulog-kafka; lein do check, test, install)


#
# Build kinesis
#
kinesis_src = $(shell find mulog-kinesis/project.clj mulog-kinesis/src mulog-kinesis/resources -type f)
build-kinesis: build-core mulog-kinesis/target/mulog*.jar
mulog-kinesis/target/mulog*.jar: $(kinesis_src)
- @printf "#\n# Building mulog-kinesis\n#\n"
- (cd mulog-kinesis; lein do check, test, install)


#
# Build CloudWatch
#
cloudwatch_src = $(shell find mulog-cloudwatch/project.clj mulog-cloudwatch/src mulog-cloudwatch/resources -type f)
build-cloudwatch: build-core mulog-cloudwatch/target/mulog*.jar
mulog-cloudwatch/target/mulog*.jar: $(cloudwatch_src)
- @printf "#\n# Building mulog-cloudwatch\n#\n"
- (cd mulog-cloudwatch; lein do check, test, install)


#
# Build slack
#
slack_src = $(shell find mulog-slack/project.clj mulog-slack/src mulog-slack/resources -type f)
build-slack: build-core mulog-slack/target/mulog*.jar
mulog-slack/target/mulog*.jar: $(slack_src)
- @printf "#\n# Building mulog-slack\n#\n"
- (cd mulog-slack; lein do check, test, install)


#
# Build zipkin
#
zipkin_src = $(shell find mulog-zipkin/project.clj mulog-zipkin/src mulog-zipkin/resources -type f)
build-zipkin: build-core mulog-zipkin/target/mulog*.jar
mulog-zipkin/target/mulog*.jar: $(zipkin_src)
- @printf "#\n# Building mulog-zipkin\n#\n"
- (cd mulog-zipkin; lein do check, test, install)


#
# Build prometheus
#
prometheus_src = $(shell find mulog-prometheus/project.clj mulog-prometheus/src mulog-prometheus/resources -type f)
build-prometheus: build-core mulog-prometheus/target/mulog*.jar
mulog-prometheus/target/mulog*.jar: $(prometheus_src)
- @printf "#\n# Building mulog-prometheus\n#\n"
- (cd mulog-prometheus; lein do check, test, install)


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
- (cd mulog-json;                 lein deploy clojars)
- (cd mulog-elasticsearch;        lein deploy clojars)
- (cd mulog-jvm-metrics;          lein deploy clojars)
- (cd mulog-kafka;                lein deploy clojars)
- (cd mulog-kinesis;              lein deploy clojars)
- (cd mulog-cloudwatch;           lein deploy clojars)
- (cd mulog-slack;                lein deploy clojars)
- (cd mulog-zipkin;               lein deploy clojars)
- (cd mulog-prometheus;           lein deploy clojars)


#
# update dependencies in project.clj
#
.PHONY: ancient
ancient:
- @printf "#\n# Updating dependencies \n#\n"
- (cd mulog-core;                 lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-json;                 lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-elasticsearch;        lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-jvm-metrics;          lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-kafka;                lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-kinesis;              lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-cloudwatch;           lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-slack;                lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-zipkin;               lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd mulog-prometheus;           lein with-profile tools ancient upgrade ; lein do clean, install)
- (cd examples/roads-disruptions; lein with-profile tools ancient upgrade ; lein do clean, install)


#
# uniforms formatting on all sources
#
.PHONY: format
format:
- @printf "#\n# Re-Format source code \n#\n"
- (cd mulog-core;                 lein with-profile tools cljfmt fix)
- (cd mulog-json;                 lein with-profile tools cljfmt fix)
- (cd mulog-elasticsearch;        lein with-profile tools cljfmt fix)
- (cd mulog-jvm-metrics;          lein with-profile tools cljfmt fix)
- (cd mulog-kafka;                lein with-profile tools cljfmt fix)
- (cd mulog-kinesis;              lein with-profile tools cljfmt fix)
- (cd mulog-cloudwatch;           lein with-profile tools cljfmt fix)
- (cd mulog-slack;                lein with-profile tools cljfmt fix)
- (cd mulog-zipkin;               lein with-profile tools cljfmt fix)
- (cd mulog-prometheus;           lein with-profile tools cljfmt fix)
- (cd examples/roads-disruptions; lein with-profile tools cljfmt fix)


#
# Clean target directories
#
.PHONY: clean
clean:
- @printf "#\n# Cleaning \n#\n"
- (cd mulog-core;                 rm -fr target)
- (cd mulog-json;                 rm -fr target)
- (cd mulog-elasticsearch;        rm -fr target)
- (cd mulog-jvm-metrics;          rm -fr target)
- (cd mulog-kafka;                rm -fr target)
- (cd mulog-kinesis;              rm -fr target)
- (cd mulog-cloudwatch;           rm -fr target)
- (cd mulog-slack;                rm -fr target)
- (cd mulog-zipkin;               rm -fr target)
- (cd mulog-prometheus;           rm -fr target)
- (cd examples/roads-disruptions; rm -fr target)
