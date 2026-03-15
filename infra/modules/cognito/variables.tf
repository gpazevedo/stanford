variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "post_confirmation_lambda_arn" {
  description = "ARN of the Post-Confirmation Lambda (added after lambda module)"
  type        = string
  default     = ""
}
