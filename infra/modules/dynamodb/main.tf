resource "aws_dynamodb_table" "courses" {
  name         = "${var.project_name}-${var.environment}-courses"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "courseId"

  attribute {
    name = "courseId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "courses"
  }
}

resource "aws_dynamodb_table" "applications" {
  name         = "${var.project_name}-${var.environment}-applications"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"
  range_key    = "courseId"

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "courseId"
    type = "S"
  }

  global_secondary_index {
    name            = "courseId-index"
    hash_key        = "courseId"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "applications"
  }
}

resource "aws_dynamodb_table" "users" {
  name         = "${var.project_name}-${var.environment}-users"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"

  attribute {
    name = "userId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "users"
  }
}
