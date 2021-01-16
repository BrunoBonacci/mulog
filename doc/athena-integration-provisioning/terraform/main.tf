
variable region {}
variable profile {}
variable account_id {}
variable stage {}
variable glue_database {}
variable glue_crawler_name {}
variable glue_crawler_schedule {}
variable bucket_name {}
variable athena_log_group {}
variable athena_log_stream {}

variable glue_table {}
variable glue_crawler_role {}
variable glue_crawler_policy {}
variable mulog_stream_name {}
variable firehose_delivery_stream_name {}
variable firehose_delivery_stream_role {}
variable firehose_delivery_policy {}

terraform {
  required_version = ">= 0.13"
}

provider "aws" {
  region  = var.region
  profile  = var.profile
}

resource "aws_s3_bucket" "mulog_events_bucket" {
  bucket = "${var.bucket_name}-${var.stage}"
  acl    = "private"
  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm     = "aws:kms"
      }
    }
  }
  lifecycle_rule {  # consider overriding configuration for the particular use case
    id      = "ObjectExpiry_Day"
    enabled = true
    prefix = "${var.bucket_name}-${var.stage}/${var.glue_table}"
    tags = {
      "rule"      = "log"
      "autoclean" = "true"
    }
    expiration {
      days = 1
    }
  }
  lifecycle_rule {
    id      = "ObjectExpiry_3Days"
    enabled = true
    prefix = "${var.bucket_name}-${var.stage}/Unsaved"
    tags = {
      "rule"      = "log"
      "autoclean" = "true"
    }
    expiration {
      days = 3
    }
  }
  tags = {
    Name        = "mulog"
    Environment = "prod"
  }
}

#=========== Firehose Log Group/Stream =========================
resource "aws_cloudwatch_log_group" "firehose_log_group" {
  name = "${var.athena_log_group}-${var.stage}"
  retention_in_days = 14
  tags = {
    Environment = "prod"
    Application = "mulog"
  }
}

resource "aws_cloudwatch_log_stream" "firehose_log_stream" {
  name           = "${var.athena_log_stream}-${var.stage}"
  log_group_name = aws_cloudwatch_log_group.firehose_log_group.name
}

#=========== create Glue Catalog table =========================
resource "aws_glue_catalog_database" "mulog_events_database" {
  name = "${var.glue_database}_${var.stage}"
  catalog_id = var.account_id
}

resource "aws_glue_catalog_table" "mulog_events_table" {
  name = var.glue_table
  database_name = aws_glue_catalog_database.mulog_events_database.name
  description = "mulog events table"
  owner= "${var.glue_table}-${var.stage}"
  table_type = "EXTERNAL_TABLE"
  parameters = {
    EXTERNAL = "TRUE"
    has_encrypted_data = true
    classification = "parquet"
  }
  partition_keys {
    name = "dt"
    type = "string"
  }
  partition_keys {
    name = "hour"
    type = "tinyint"
  }
  storage_descriptor {
    stored_as_sub_directories= false
    location = "s3://${var.bucket_name}-${var.stage}/${var.glue_table}/"
    input_format = "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat"
    output_format = "org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat"

    ser_de_info {
      serialization_library = "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe"
      parameters = {
        "serialization.format" = 1
      }
    }

    columns {
      name = "mulog/timestamp"
      type = "string"
    }
    columns {
      name = "mulog/trace-id"
      type = "string"
    }
    columns {
      name = "mulog/event-name"
      type = "string"
    }
    columns {
      name = "mulog/namespace"
      type = "string"
    }
    columns {
      name = "message"
      type = "string"
    }
    compressed= true
  }
}

resource "aws_glue_crawler" "glue_crawler" {
  name          = "${var.glue_crawler_name}_${var.stage}"
  description   = "builds and keeps up-to-date data catalog."
  database_name = aws_glue_catalog_database.mulog_events_database.name
  role          = aws_iam_role.mulog_events_crawler_role.arn
  schedule      = var.glue_crawler_schedule
  s3_target {
    path = "s3://${var.bucket_name}-${var.stage}/${var.glue_table}/"
  }
  schema_change_policy {
    delete_behavior = "LOG"
    update_behavior = "UPDATE_IN_DATABASE"
  }
}

#=========== create an IAM role for Glue =========================
resource "aws_iam_role" "mulog_events_crawler_role" {
  name = "${var.glue_crawler_role}-${var.stage}"
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "glue.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

# AWS policy: Attach default glue policy to glue role (allows also to write logs)
resource "aws_iam_role_policy_attachment" "mulog_events_crawler_glue_service" {
  role = aws_iam_role.mulog_events_crawler_role.id
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSGlueServiceRole"
}
# AWS policy: allow to read/write from input bucket
resource "aws_iam_role_policy" "mulog_events_crawler_policy" {
  name = "${var.glue_crawler_policy}-${var.stage}"
  role = aws_iam_role.mulog_events_crawler_role.id
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::${var.bucket_name}-${var.stage}/${var.glue_table}/*"
      ]
    }
  ]
}
EOF
}

#=========== create an IAM role for Kinesis =========================
# AWS role for glue
resource "aws_iam_role" "firehose_delivery_role" {
  name = "${var.firehose_delivery_stream_role}-${var.stage}"
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "",
      "Effect": "Allow",
      "Principal": {
        "Service": "firehose.amazonaws.com"
      },
      "Action": "sts:AssumeRole",
      "Condition": {
        "StringEquals": {
          "sts:ExternalId": ${var.account_id}
        }
      }
    }
  ]
}
EOF
}

# AWS policy: Attach default glue policy to glue role (allows also to write logs)
resource "aws_iam_role_policy_attachment" "ManagedAmazonKinesisAnalyticsReadOnly" {
  role = aws_iam_role.firehose_delivery_role.id
  policy_arn = "arn:aws:iam::aws:policy/AmazonKinesisAnalyticsReadOnly"
}

# AWS policy: allow to read/write from input bucket
resource "aws_iam_role_policy" "FirehoseDeliveryPolicy" {
  name = "${var.firehose_delivery_policy}-${var.stage}"
  role = aws_iam_role.firehose_delivery_role.id
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "*",
      "Effect": "Allow"
    },
    {
      "Action": [
        "glue:GetTable",
        "glue:GetTableVersions",
        "glue:GetTables"
      ],
      "Resource": "*",
      "Effect": "Allow"
    },
    {
      "Action": [
        "kinesis:DescribeStream",
        "kinesis:GetShardIterator",
        "kinesis:GetRecords"
      ],
      "Resource": "arn:aws:kinesis:${var.region}:${var.account_id}:stream/${var.mulog_stream_name}",
      "Effect": "Allow"
    },
    {
      "Condition": {
        "StringEquals": {
          "kms:ViaService": "s3.region.amazonaws.com"
        },
        "StringLike": {
          "kms:EncryptionContext:aws:s3:arn": "arn:aws:s3:::${var.bucket_name}-${var.stage}/*"
        }
      },
      "Action": [
        "kms:Decrypt",
        "kms:GenerateDataKey"
      ],
      "Resource": "*",
      "Effect": "Allow"
    },
    {
      "Action": [
        "s3:AbortMultipartUpload",
        "s3:GetBucketLocation",
        "s3:GetObject",
        "s3:ListBucket",
        "s3:ListBucketMultipartUploads",
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::${var.bucket_name}-${var.stage}/",
        "arn:aws:s3:::${var.bucket_name}-${var.stage}/*"
      ],
      "Effect": "Allow"
    }
  ]
}
EOF
}

#=========== create Firehose Delivery Stream =========================
resource "aws_kinesis_firehose_delivery_stream" "mulog_events_firehose_delivery_stream" {
  name        = "${var.firehose_delivery_stream_name}-${var.stage}"
  destination = "extended_s3"
  kinesis_source_configuration{
    kinesis_stream_arn = "arn:aws:kinesis:${var.region}:${var.account_id}:stream/${var.mulog_stream_name}"
    role_arn = aws_iam_role.firehose_delivery_role.arn
  }
  extended_s3_configuration {
    role_arn = aws_iam_role.firehose_delivery_role.arn
    bucket_arn = aws_s3_bucket.mulog_events_bucket.arn
    prefix = "${var.glue_table}/dt=!{timestamp:yyyy-MM-dd}/hour=!{timestamp:HH}/"
    error_output_prefix = "errors/errtype=!{firehose:error-output-type}/dt=!{timestamp:yyyy-MM-dd}/hour=!{timestamp:HH}/"
    buffer_interval = "300"
    buffer_size = 64
    data_format_conversion_configuration {
      input_format_configuration {
        deserializer {
          open_x_json_ser_de {
            case_insensitive = "true"
            convert_dots_in_json_keys_to_underscores = "false"
          }
        }
      }
      output_format_configuration {
        serializer {
          parquet_ser_de {
            compression = "SNAPPY"
            enable_dictionary_compression = "false"
          }
        }
      }
      schema_configuration {
        catalog_id = var.account_id
        database_name = aws_glue_catalog_table.mulog_events_table.database_name
        role_arn = aws_iam_role.firehose_delivery_role.arn
        region = var.region
        table_name = aws_glue_catalog_table.mulog_events_table.name
        version_id = "LATEST"
      }
    }
  }
}
