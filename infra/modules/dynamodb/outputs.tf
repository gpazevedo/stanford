output "courses_table_name" {
  value = aws_dynamodb_table.courses.name
}

output "courses_table_arn" {
  value = aws_dynamodb_table.courses.arn
}

output "applications_table_name" {
  value = aws_dynamodb_table.applications.name
}

output "applications_table_arn" {
  value = aws_dynamodb_table.applications.arn
}

output "users_table_name" {
  value = aws_dynamodb_table.users.name
}

output "users_table_arn" {
  value = aws_dynamodb_table.users.arn
}
