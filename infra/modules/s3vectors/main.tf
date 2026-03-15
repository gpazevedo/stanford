resource "aws_s3vectors_vector_bucket" "main" {
  vector_bucket_name = "${var.project_name}-${var.environment}-vectors"
}

resource "aws_s3vectors_index" "course_embeddings" {
  vector_bucket_name = aws_s3vectors_vector_bucket.main.vector_bucket_name
  index_name         = "course-embeddings"
  data_type          = "float32"
  dimension          = var.vector_dimension
  distance_metric    = "cosine"
}
