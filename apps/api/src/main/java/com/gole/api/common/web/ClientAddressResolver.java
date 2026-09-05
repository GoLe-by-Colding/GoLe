package com.gole.api.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 현재 단일 호스트 Nginx 경계에서 공개 클라이언트 주소를 해석한다.
 *
 * <p>운영 Nginx가 {@code X-Real-IP}를 {@code $remote_addr}로 항상 덮어쓰고 백엔드 포트는
 * loopback에만 바인딩된다. 따라서 직접 연결한 peer가 loopback/사설 주소인 경우에만 이 헤더를
 * 신뢰한다. 사용자가 앞에 값을 덧붙일 수 있는 {@code X-Forwarded-For}는 의도적으로 사용하지 않는다.
 */
@Component
public class ClientAddressResolver {

    private static final String UNKNOWN = "unknown";

    public String resolve(HttpServletRequest request) {
        String remoteAddress = numericAddress(request.getRemoteAddr()).orElse(UNKNOWN);
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        return numericAddress(request.getHeader("X-Real-IP")).orElse(remoteAddress);
    }

    private static Optional<String> numericAddress(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String value = candidate.trim();
        if (value.isEmpty() || value.length() > 45 || value.indexOf(',') >= 0 || value.indexOf('%') >= 0) {
            return Optional.empty();
        }
        if (value.indexOf(':') < 0) {
            return normalizeIpv4(value);
        }
        if (!value.matches("[0-9a-fA-F:.]+")) {
            return Optional.empty();
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address || address instanceof Inet4Address
                    ? Optional.of(address.getHostAddress())
                    : Optional.empty();
        } catch (UnknownHostException invalidAddress) {
            return Optional.empty();
        }
    }

    private static Optional<String> normalizeIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return Optional.empty();
        }
        int[] parsed = new int[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty()
                    || octet.length() > 3
                    || !octet.chars().allMatch(character -> character >= '0' && character <= '9')) {
                return Optional.empty();
            }
            parsed[index] = Integer.parseInt(octet);
            if (parsed[index] > 255) {
                return Optional.empty();
            }
        }
        return Optional.of(parsed[0] + "." + parsed[1] + "." + parsed[2] + "." + parsed[3]);
    }

    private static boolean isTrustedProxy(String value) {
        if (UNKNOWN.equals(value)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        } catch (UnknownHostException invalidAddress) {
            return false;
        }
    }
}
