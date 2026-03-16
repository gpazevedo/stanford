variable "project_name" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "environment" {
  type = string
}

variable "alarm_email" {
  description = "Email address for CloudWatch alarm notifications"
  type        = string
}

variable "api_lambda_name" {
  type = string
}

variable "ingestion_lambda_name" {
  type = string
}

variable "api_gateway_id" {
  type = string
}

variable "appconfig_application_id" {
  type = string
}

variable "appconfig_environment_id" {
  type = string
}

variable "appconfig_profile_id" {
  type = string
}

variable "appconfig_version_number" {
  description = "AppConfig hosted configuration version number to deploy"
  type        = string
}

variable "courses_table_name" {
  type = string
}

variable "applications_table_name" {
  type = string
}

variable "users_table_name" {
  type = string
}
