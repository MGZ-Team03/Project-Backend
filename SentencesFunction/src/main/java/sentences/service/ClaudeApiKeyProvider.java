package sentences.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * Loads Claude API key from AWS Secrets Manager.
 *
 * Expected secret name/id: e.g. "team3/claude_api_key"
 * Expected SecretString:
 *  - JSON: { "CLAUDE_API_KEY": "..." }
 *  - or plain string: "..." (fallback)
 */
public final class ClaudeApiKeyProvider {

    private static volatile String cachedApiKey;
    private static final Object lock = new Object();

    private final SecretsManagerClient secretsClient;
    private final ObjectMapper objectMapper;

    public ClaudeApiKeyProvider() {
        this.secretsClient = SecretsManagerClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public String getApiKey(String secretId) {
        if (secretId == null || secretId.trim().isEmpty()) {
            throw new IllegalArgumentException("CLAUDE_API_KEY_SECRET_ID environment variable is not set");
        }

        String key = cachedApiKey;
        if (key != null && !key.isEmpty()) {
            return key;
        }

        synchronized (lock) {
            if (cachedApiKey != null && !cachedApiKey.isEmpty()) {
                return cachedApiKey;
            }

            GetSecretValueResponse resp = secretsClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretId).build()
            );

            String secretString = resp.secretString();
            if (secretString == null || secretString.isEmpty()) {
                throw new IllegalStateException("SecretString is empty for secretId=" + secretId);
            }

            cachedApiKey = parseApiKey(secretString);
            if (cachedApiKey == null || cachedApiKey.isEmpty()) {
                throw new IllegalStateException("CLAUDE_API_KEY is missing/empty in secretId=" + secretId);
            }

            return cachedApiKey;
        }
    }

    private String parseApiKey(String secretString) {
        String s = secretString.trim();
        if (s.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(s);
                JsonNode node = root.get("CLAUDE_API_KEY");
                return node == null ? null : node.asText();
            } catch (Exception e) {
                // fall through to treat as plain string
            }
        }
        return s;
    }
}

