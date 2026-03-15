output "vector_bucket_name" {
  value = aws_s3vectors_vector_bucket.main.vector_bucket_name
}

output "index_name" {
  value = aws_s3vectors_index.course_embeddings.index_name
}
