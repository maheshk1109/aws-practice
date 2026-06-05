package com.lastmile.secrets;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 2.21 — AWS Secrets Manager
 *
 * WHAT IS SECRETS MANAGER?
 *   A managed service to store, rotate, and retrieve sensitive values like:
 *   - DB passwords, API keys, OAuth tokens, private keys
 *
 * WHY NOT USE ENVIRONMENT VARIABLES?
 *   - Env vars are visible in ECS task definitions, logs, crash dumps
 *   - No rotation support — changing a secret means redeploying
 *   - Secrets Manager: encrypted at rest (KMS), audited via CloudTrail,
 *     supports automatic rotation via Lambda
 *
 * SECRET FORMATS:
 *   - Plain string  : "mysecretpassword"
 *   - JSON string   : {"username":"admin","password":"pass123"}
 *     → Store DB credentials as JSON, parse both fields from one secret
 *
 * CACHING:
 *   Each GetSecretValue call costs money and adds latency (~10ms).
 *   Cache secrets in memory for a TTL (e.g. 5 min) so you only fetch once.
 *   AWS provides aws-secretsmanager-caching-java for production use.
 *   Here we use a simple ConcurrentHashMap to illustrate the concept.
 *
 * IAM PERMISSION NEEDED:
 *   secretsmanager:GetSecretValue on the specific secret ARN
 *   (see iam/ecs-task-policy.json)
 *
 * ROTATION:
 *   Secrets Manager can auto-rotate secrets using a Lambda function.
 *   Enable via Console or CloudFormation: RotationSchedule resource.
 *   Your app re-fetches on next cache miss — no restart needed.
 */
@Service
public class SecretsManagerService {

    private final SecretsManagerClient secretsManagerClient;

    // Simple in-memory cache — key: secretName, value: secret string
    // In production: use aws-secretsmanager-caching-java with proper TTL
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public SecretsManagerService(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
    }

    /**
     * Fetch a secret by name or ARN.
     * Returns the secret string (plain text or JSON string).
     *
     * Example secret names:
     *   "lastmile/prod/db-password"
     *   "lastmile/prod/stripe-api-key"
     */
    public String getSecret(String secretName) {
        // Return cached value if present — avoids repeated API calls
        return cache.computeIfAbsent(secretName, this::fetchFromAws);
    }

    /**
     * Force-refresh a secret — call this after a rotation event
     * (e.g. via SNS notification from Secrets Manager rotation Lambda).
     */
    public String refreshSecret(String secretName) {
        cache.remove(secretName);
        return getSecret(secretName);
    }

    private String fetchFromAws(String secretName) {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);

        // SecretString: for text/JSON secrets
        // SecretBinary: for binary secrets (e.g. encrypted key files)
        return response.secretString();
    }
}
