package com.gole.api.chat.adapter.out.assistant;

import java.time.Duration;
import java.util.Set;

/** 기존 내부 rules-v1 경계보다 넓은 네트워크·대기 권한을 만들지 않는다. */
record DurableSupportAssistantSettings(String target, String caller, String token, Duration timeout) {
    private static final Set<String> LOCAL_ENVIRONMENTS = Set.of("local", "development", "dev", "test", "e2e");

    DurableSupportAssistantSettings {
        if (target == null || !target.matches("127\\.0\\.0\\.1:[0-9]{1,5}")) {
            throw new IllegalStateException("Durable support agent requires a loopback target");
        }
        int port = Integer.parseInt(target.substring(target.lastIndexOf(':') + 1));
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Durable support agent requires a valid port");
        }
        if (caller == null || !caller.matches("[A-Za-z0-9_.-]{1,128}") || token == null || token.length() < 32) {
            throw new IllegalStateException("Durable support agent requires internal caller credentials");
        }
        if (timeout == null || timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalStateException("Durable support agent timeout must be between 1ms and 10s");
        }
    }

    static void requireLocalEnvironment(String environment) {
        if (!LOCAL_ENVIRONMENTS.contains(environment)) {
            throw new IllegalStateException("Durable support agent production transport is not configured");
        }
    }

    @Override
    public String toString() {
        return "DurableSupportAssistantSettings[credentials=REDACTED]";
    }
}
