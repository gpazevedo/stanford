variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "ingestion_lambda_arn" {
  type = string
}

variable "schedule_expression" {
  description = "EventBridge schedule expression for ingestion (cron or rate)"
  type        = string
  default     = "cron(0 2 ? * SUN *)"
}
