variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "embedding_model_id" {
  description = "Bedrock model ID for embeddings"
  type        = string
  default     = "amazon.titan-embed-text-v2:0"
}

variable "generative_model_id" {
  description = "Bedrock model ID for generative tasks"
  type        = string
  default     = "anthropic.claude-sonnet-4-5"
}
