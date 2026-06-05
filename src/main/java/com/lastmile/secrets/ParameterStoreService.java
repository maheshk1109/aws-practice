package com.lastmile.secrets;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 2.21 — AWS Systems Manager Parameter Store
 *
 * PARAMETER STORE vs SECRETS MANAGER — WHEN TO USE WHICH?
 *
 *   Parameter Store (SSM):
 *     ✓ Free for standard parameters (up to 4KB)
 *     ✓ Great for non-secret config: feature flags, queue URLs, env names
 *     ✓ Hierarchical paths: /lastmile/prod/queue-url
 *     ✓ Supports SecureString (encrypted with KMS) for light secrets
 *     ✗ No automatic rotation
 *     ✗ No cross-account sharing
 *
 *   Secrets Manager:
 *     ✓ Automatic rotation via Lambda
 *     ✓ Cross-account secret sharing
 *     ✓ Purpose-built for secrets (DB creds, API keys)
 *     ✗ $0.40/secret/month + $0.05 per 10K API calls
 *
 * RULE OF THUMB:
 *   Passwords / API keys / tokens → Secrets Manager
 *   Config values / URLs / flags  → Parameter Store (free tier)
 *
 * HIERARCHY:
 *   Use path-based naming: /app/environment/key
 *   e.g. /lastmile/prod/sqs-queue-url
 *        /lastmile/prod/feature-flags/new-routing-enabled
 *   GetParametersByPath lets you fetch ALL params under a path at once.
 *
 * SECURE STRING:
 *   Parameter type SecureString encrypts the value with a KMS key.
 *   withDecryption(true) decrypts it on fetch — IAM must allow kms:Decrypt.
 *
 * IAM PERMISSION NEEDED:
 *   ssm:GetParameter, ssm:GetParametersByPath on the path
 *   kms:Decrypt if using SecureString
 */
@Service
public class ParameterStoreService {

    private final SsmClient ssmClient;

    public ParameterStoreService(SsmClient ssmClient) {
        this.ssmClient = ssmClient;
    }

    /**
     * Fetch a single parameter by full path.
     * withDecryption(true) is required for SecureString params.
     *
     * Example: getParameter("/lastmile/prod/sqs-queue-url")
     */
    public String getParameter(String paramPath) {
        GetParameterRequest request = GetParameterRequest.builder()
                .name(paramPath)
                .withDecryption(true)   // no-op for String/StringList, required for SecureString
                .build();

        return ssmClient.getParameter(request).parameter().value();
    }

    /**
     * Fetch all parameters under a path hierarchy.
     * Returns a map of parameter name → value.
     *
     * Example: getAllByPath("/lastmile/prod/")
     *   returns: {"/lastmile/prod/queue-url" -> "https://...", ...}
     *
     * recursive(true): also fetches sub-paths like /lastmile/prod/flags/...
     */
    public Map<String, String> getAllByPath(String path) {
        GetParametersByPathRequest request = GetParametersByPathRequest.builder()
                .path(path)
                .recursive(true)
                .withDecryption(true)
                .build();

        GetParametersByPathResponse response = ssmClient.getParametersByPath(request);

        return response.parameters().stream()
                .collect(Collectors.toMap(Parameter::name, Parameter::value));
    }
}
