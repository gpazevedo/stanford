output "api_function_name" {
  value = length(aws_lambda_function.api) > 0 ? aws_lambda_function.api[0].function_name : ""
}

output "api_function_arn" {
  value = length(aws_lambda_function.api) > 0 ? aws_lambda_function.api[0].arn : ""
}

output "api_alias_arn" {
  value = length(aws_lambda_alias.api_prod) > 0 ? aws_lambda_alias.api_prod[0].arn : ""
}

output "ingestion_function_name" {
  value = length(aws_lambda_function.ingestion) > 0 ? aws_lambda_function.ingestion[0].function_name : ""
}

output "ingestion_function_arn" {
  value = length(aws_lambda_function.ingestion) > 0 ? aws_lambda_function.ingestion[0].arn : ""
}

output "post_confirmation_function_arn" {
  value = length(aws_lambda_function.post_confirmation) > 0 ? aws_lambda_function.post_confirmation[0].arn : ""
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
