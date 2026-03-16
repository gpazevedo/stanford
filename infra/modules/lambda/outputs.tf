output "api_function_name" {
  value = aws_lambda_function.api.function_name
}

output "api_function_arn" {
  value = aws_lambda_function.api.arn
}

output "api_alias_arn" {
  value = aws_lambda_alias.api_prod.arn
}

output "ingestion_function_name" {
  value = aws_lambda_function.ingestion.function_name
}

output "ingestion_function_arn" {
  value = aws_lambda_function.ingestion.arn
}

output "post_confirmation_function_arn" {
  value = aws_lambda_function.post_confirmation.arn
}

output "admin_authorizer_arn" {
  value = aws_lambda_function.admin_authorizer.arn
}

output "api_role_arn" {
  value = aws_iam_role.api.arn
}

output "ingestion_role_arn" {
  value = aws_iam_role.ingestion.arn
}
