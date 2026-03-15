variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  type = string
}

variable "courses_table_arn" {
  type = string
}

variable "applications_table_arn" {
  type = string
}

variable "users_table_arn" {
  type = string
}

variable "user_pool_arn" {
  type = string
}

variable "user_pool_id" {
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

variable "api_image_uri" {
  description = "ECR image URI for the API Lambda (set after first image push)"
  type        = string
  default     = ""
}

variable "ingestion_image_uri" {
  description = "ECR image URI for the Ingestion Lambda"
  type        = string
  default     = ""
}

variable "post_confirmation_image_uri" {
  description = "ECR image URI for the Post-Confirmation Lambda"
  type        = string
  default     = ""
}
