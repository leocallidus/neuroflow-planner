package com.example.neuroflowplanner.util;

import javax.net.ssl.SSLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CloudSyncUrlSupport Tests")
class CloudSyncUrlSupportTest {

    @Test
    @DisplayName("normalizes legacy wildcard uvicorn URL to loopback HTTP")
    void normalizesLegacyWildcardUvicornUrlToLoopbackHttp() {
        assertEquals(
                "http://127.0.0.1:8000",
                CloudSyncUrlSupport.normalizeBaseUrl("https://0.0.0.0:8000"));
        assertEquals(
                "http://127.0.0.1:8000",
                CloudSyncUrlSupport.normalizeBaseUrl("0.0.0.0:8000"));
    }

    @Test
    @DisplayName("keeps remote HTTPS URL intact except trailing slash cleanup")
    void keepsRemoteHttpsUrlIntactExceptTrailingSlashCleanup() {
        assertEquals(
                "https://sync.example.com/api",
                CloudSyncUrlSupport.normalizeBaseUrl("https://sync.example.com/api/"));
    }

    @Test
    @DisplayName("describes local HTTPS to HTTP mismatch with actionable hint")
    void describesLocalHttpsToHttpMismatchWithActionableHint() {
        String message = CloudSyncUrlSupport.describeTransportFailure(
                new SSLException("Unsupported or unrecognized SSL message"),
                "https://localhost:8000");

        assertTrue(message.contains("HTTP"));
        assertTrue(message.contains("http://127.0.0.1:8000"));
    }
}
