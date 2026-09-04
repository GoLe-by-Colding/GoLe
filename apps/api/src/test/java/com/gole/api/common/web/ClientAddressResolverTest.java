package com.gole.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {

    private final ClientAddressResolver resolver = new ClientAddressResolver();

    @Test
    void trustsNginxOverwrittenRealIpOnlyFromPrivateDirectPeer() {
        MockHttpServletRequest request = requestFrom("172.18.0.4");
        request.addHeader("X-Real-IP", "198.51.100.24");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 198.51.100.24");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.24");
    }

    @Test
    void ignoresSpoofedProxyHeadersFromPublicDirectPeer() {
        MockHttpServletRequest request = requestFrom("203.0.113.9");
        request.addHeader("X-Real-IP", "198.51.100.24");
        request.addHeader("X-Forwarded-For", "192.0.2.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void neverUsesForwardedForAndFallsBackWhenRealIpIsMalformed() {
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.24, 192.0.2.1");
        request.addHeader("X-Forwarded-For", "198.51.100.24");

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void normalizesNumericAddressesWithoutResolvingHostNames() {
        MockHttpServletRequest ipv4 = requestFrom("010.000.000.001");
        MockHttpServletRequest hostName = requestFrom("attacker.example");
        MockHttpServletRequest nonAsciiDigits = requestFrom("١٢٧.٠.٠.١");

        assertThat(resolver.resolve(ipv4)).isEqualTo("10.0.0.1");
        assertThat(resolver.resolve(hostName)).isEqualTo("unknown");
        assertThat(resolver.resolve(nonAsciiDigits)).isEqualTo("unknown");
    }

    private static MockHttpServletRequest requestFrom(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }
}
