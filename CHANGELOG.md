# Changelog

## v0.1.9 - (unreleased)
  - Added flag to support ElasticSearch v6.x correctly
  - Added built-in leveled logging
  - Deprecated `:transform` in favor of transducers via `:transduce`


## v0.1.8 - (2020-03-09)

  - Added ability to configure multi-publishers
  - Added ability to provide custom transformation to built-in publishers
  - Added ability to release resources when a publisher is stopped
  - Fixed issue with Kafka publisher handling serialization of Exceptions
