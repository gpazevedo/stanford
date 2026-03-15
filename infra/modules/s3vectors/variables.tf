variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "vector_dimension" {
  description = "Embedding vector dimension (1024 for Titan Embed v2)"
  type        = number
  default     = 1024
}
