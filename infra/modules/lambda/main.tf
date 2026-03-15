data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
}

# Note: SnapStart is not supported for container image Lambdas (package_type = "Image")
# API Lambda
resource "aws_lambda_function" "api" {
  count = var.api_image_uri != "" ? 1 : 0

  function_name = "${var.project_name}-${var.environment}-api"
  role          = aws_iam_role.api.arn
  package_type  = "Image"
  image_uri     = var.api_image_uri
  timeout       = 30
  memory_size   = 1024
  publish       = true

  environment {
    variables = {
      ENVIRONMENT              = var.environment
      APPCONFIG_APPLICATION_ID = var.appconfig_application_id
      APPCONFIG_ENVIRONMENT_ID = var.appconfig_environment_id
      APPCONFIG_PROFILE_ID     = var.appconfig_profile_id
    }
  }
}

resource "aws_lambda_alias" "api_prod" {
  count            = var.api_image_uri != "" ? 1 : 0
  name             = "prod"
  function_name    = aws_lambda_function.api[0].function_name
  function_version = aws_lambda_function.api[0].version
}

# Ingestion Lambda
resource "aws_lambda_function" "ingestion" {
  count = var.ingestion_image_uri != "" ? 1 : 0

  function_name = "${var.project_name}-${var.environment}-ingestion"
  role          = aws_iam_role.ingestion.arn
  package_type  = "Image"
  image_uri     = var.ingestion_image_uri
  timeout       = 300
  memory_size   = 512

  environment {
    variables = {
      ENVIRONMENT              = var.environment
      APPCONFIG_APPLICATION_ID = var.appconfig_application_id
      APPCONFIG_ENVIRONMENT_ID = var.appconfig_environment_id
      APPCONFIG_PROFILE_ID     = var.appconfig_profile_id
    }
  }
}

# Post-Confirmation Lambda
resource "aws_lambda_function" "post_confirmation" {
  count = var.post_confirmation_image_uri != "" ? 1 : 0

  function_name = "${var.project_name}-${var.environment}-post-confirmation"
  role          = aws_iam_role.post_confirmation.arn
  package_type  = "Image"
  image_uri     = var.post_confirmation_image_uri
  timeout       = 10
  memory_size   = 256

  environment {
    variables = {
      USER_POOL_ID = var.user_pool_id
    }
  }
}

# Cognito permission to invoke post-confirmation Lambda
resource "aws_lambda_permission" "cognito_post_confirmation" {
  count         = var.post_confirmation_image_uri != "" ? 1 : 0
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.post_confirmation[0].function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = var.user_pool_arn
}

# Admin authorizer Lambda (inline zip — no container needed, tiny function)
data "archive_file" "admin_authorizer" {
  type        = "zip"
  output_path = "${path.module}/admin_authorizer.zip"
  source {
    content  = <<-EOF
      import { createPublicKey } from "node:crypto";
      import { createVerify } from "node:crypto";

      const REGION = process.env.AWS_REGION;
      const USER_POOL_ID = process.env.USER_POOL_ID;
      const JWKS_URI = `https://cognito-idp.$${REGION}.amazonaws.com/$${USER_POOL_ID}/.well-known/jwks.json`;

      let cachedKeys = null;

      async function getPublicKeys() {
        if (cachedKeys) return cachedKeys;
        const res = await fetch(JWKS_URI);
        const { keys } = await res.json();
        cachedKeys = keys;
        return keys;
      }

      export const handler = async (event) => {
        try {
          const authHeader = event.headers?.authorization || event.headers?.Authorization || "";
          const token = authHeader.replace(/^Bearer\s+/i, "");
          if (!token) return { isAuthorized: false };

          const [headerB64] = token.split(".");
          const header = JSON.parse(Buffer.from(headerB64, "base64url").toString());

          const keys = await getPublicKeys();
          const key = keys.find(k => k.kid === header.kid);
          if (!key) return { isAuthorized: false };

          // Verify signature using the public key
          const [h, p, sig] = token.split(".");
          const pubKey = createPublicKey({ key, format: "jwk" });
          const verify = createVerify("RSA-SHA256");
          verify.update(`$${h}.$${p}`);
          const valid = verify.verify(pubKey, sig, "base64url");
          if (!valid) return { isAuthorized: false };

          const payload = JSON.parse(Buffer.from(p, "base64url").toString());

          // Check expiry
          if (payload.exp < Math.floor(Date.now() / 1000)) return { isAuthorized: false };

          // Check admins group
          const groups = payload["cognito:groups"] || [];
          return { isAuthorized: Array.isArray(groups) ? groups.includes("admins") : groups === "admins" };
        } catch {
          return { isAuthorized: false };
        }
      };
    EOF
    filename = "index.mjs"
  }
}

resource "aws_lambda_function" "admin_authorizer" {
  function_name    = "${var.project_name}-${var.environment}-admin-authorizer"
  role             = aws_iam_role.admin_authorizer.arn
  runtime          = "nodejs22.x"
  handler          = "index.handler"
  filename         = data.archive_file.admin_authorizer.output_path
  source_code_hash = data.archive_file.admin_authorizer.output_base64sha256
  timeout          = 5
  memory_size      = 128

  environment {
    variables = {
      USER_POOL_ID = var.user_pool_id
    }
  }
}
