terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.72"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # 버킷은 infra/bootstrap에서 만든다. S3 네이티브 잠금(use_lockfile)을 쓴다.
  backend "s3" {
    bucket       = "stock-management-tfstate-378040395734"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.aws_account_id]
  default_tags {
    tags = { Project = "stock-management", Environment = "prod", ManagedBy = "terraform" }
  }
}
