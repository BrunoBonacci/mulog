profile       = # TODO. SET VALUE
region        = # TODO. SET VALUE
account_id    = # TODO. SET VALUE
stage         = # TODO. SET VALUE

#### stream
mulog_stream_name = # TODO. SET VALUE

#### s3
bucket_name = "mulog-events"

#### athena
athena_log_group  = "mulog-events-athena"
athena_log_stream = "events-delivery"

#### firehose
firehose_delivery_stream_name = "mulog-events-athena"
firehose_delivery_stream_role = "mulog-firehose-delivery-role"
firehose_delivery_policy      = "mulog-firehose-delivery-policy"

#### glue
glue_table                    = "mulog_events"
glue_database                 = "mulog_events_db"
glue_crawler_name             = "mulog_events_crawler"
glue_crawler_schedule         = "cron(45 0/1 * * ? *)" # At 45 minutes past the hour
glue_crawler_role             = "mulog-events-crawler-role"
glue_crawler_policy           = "mulog-events-crawler-policy"