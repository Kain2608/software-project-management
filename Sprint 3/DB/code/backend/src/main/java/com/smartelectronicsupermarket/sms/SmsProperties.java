package com.smartelectronicsupermarket.sms;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sasms.sms")
public record SmsProperties(Mode mode, URI endpoint, String apiKey, String brandname,
        Duration connectTimeout, Duration requestTimeout) {
    public enum Mode { DISABLED, MOCK, HTTP }

    public SmsProperties {
        mode = mode == null ? Mode.DISABLED : mode;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        if (mode == Mode.HTTP) {
            if (endpoint == null || !"https".equalsIgnoreCase(endpoint.getScheme())
                    || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getFragment() != null || endpoint.getQuery() != null) {
                throw new IllegalArgumentException("SMS endpoint must be an absolute HTTPS URI without credentials, query or fragment");
            }
            if (apiKey == null || !apiKey.matches("[\\x21-\\x7E]{1,512}")) {
                throw new IllegalArgumentException("SMS API key must contain 1 to 512 visible ASCII characters");
            }
            if (brandname == null || !brandname.matches("[A-Za-z0-9 _-]{1,32}")) {
                throw new IllegalArgumentException("SMS brandname is invalid");
            }
        }
        for (Duration timeout : new Duration[] {connectTimeout, requestTimeout}) {
            if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("SMS timeout must be greater than zero and at most 30 seconds");
            }
        }
    }

    @Override
    public String toString() {
        return "SmsProperties[mode=" + mode + ", endpoint=[REDACTED], apiKey=[REDACTED], brandname=[REDACTED]]";
    }
}
