variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "state_bucket_name" {
  description = "Name for the Terraform state S3 bucket (must be globally unique)"
  type        = string
}
