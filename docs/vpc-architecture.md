# AWS VPC Architecture

## VPC
CIDR: 10.0.0.0/16

## Public Subnet
10.0.1.0/24

## Private App Subnet
10.0.2.0/24

## Private DB Subnet
10.0.3.0/24

## Security Groups
- ALB-SG
- APP-SG
- DB-SG

## IAM Roles
- ECS Task Role
- Lambda Role
- DynamoDB Role
