package com.careertuner.admin.securityops.waf;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

/** WAF 아웃바운드 요청이 공개 HTTPS 호스트만 향하도록 검증한다. */
@Component
public class WafExternalEndpointValidator {

    private final HostResolver hostResolver;

    public WafExternalEndpointValidator() {
        this(InetAddress::getAllByName);
    }

    WafExternalEndpointValidator(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    public URI validate(String endpoint) {
        if (endpoint == null || endpoint.isBlank()
                || containsControlCharacter(endpoint) || containsEncodedControlCharacter(endpoint)) {
            throw rejected("endpoint가 비어 있거나 올바르지 않습니다.");
        }

        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw rejected("endpoint URL 형식이 올바르지 않습니다.");
        }

        if (!uri.isAbsolute() || uri.isOpaque() || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw rejected("HTTPS endpoint만 허용됩니다.");
        }
        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw rejected("user-info와 fragment는 허용되지 않습니다.");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw rejected("표준 HTTPS 포트만 허용됩니다.");
        }

        String authority = uri.getRawAuthority();
        String host = uri.getHost();
        if (authority == null || authority.isBlank() || host == null || host.isBlank()
                || authority.indexOf('%') >= 0 || authority.indexOf('\\') >= 0 || authority.endsWith(":")) {
            throw rejected("endpoint 호스트가 올바르지 않습니다.");
        }

        host = normalizeHost(host);
        if (host.isBlank() || "localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw rejected("로컬 호스트는 허용되지 않습니다.");
        }

        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw rejected("endpoint DNS 조회에 실패했습니다.");
        }
        if (addresses == null || addresses.length == 0) {
            throw rejected("endpoint DNS 결과가 비어 있습니다.");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw rejected("공개 네트워크가 아닌 endpoint 주소는 허용되지 않습니다.");
            }
        }
        return uri;
    }

    private String normalizeHost(String host) {
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address inet6Address) {
            return inet6Address.getScopeId() == 0 && isPublicIpv6(address.getAddress());
        }
        return false;
    }

    private boolean isPublicIpv4(byte[] bytes) {
        int first = unsigned(bytes[0]);
        int second = unsigned(bytes[1]);
        int third = unsigned(bytes[2]);
        int fourth = unsigned(bytes[3]);

        if (first == 0 || first == 10 || first == 127 || first >= 224) {
            return false;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return false;
        }
        if (first == 169 && second == 254) {
            return false;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return false;
        }
        if (first == 192 && second == 168) {
            return false;
        }
        if (first == 192 && second == 0 && third == 0) {
            return false;
        }
        if (first == 192 && second == 0 && third == 2) {
            return false;
        }
        if (first == 192 && second == 88 && third == 99) {
            return false;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return false;
        }
        if (first == 198 && second == 51 && third == 100) {
            return false;
        }
        if (first == 203 && second == 0 && third == 113) {
            return false;
        }

        // Azure WireServer/metadata 가 공개 주소 범위처럼 보이는 예외를 명시적으로 차단한다.
        return first != 168 || second != 63 || third != 129 || fourth != 16;
    }

    private boolean isPublicIpv6(byte[] bytes) {
        // ULA(fc00::/7), 문서(2001:db8::/32), Teredo(2001::/32), benchmark(2001:2::/48),
        // 6to4(2002::/16), NAT64 well-known prefix(64:ff9b::/96)를 외부 endpoint로 사용하지 않는다.
        if ((unsigned(bytes[0]) & 0xfe) == 0xfc) {
            return false;
        }
        if (matchesPrefix(bytes, new int[] {0x20, 0x01, 0x0d, 0xb8}, 4)
                || matchesPrefix(bytes, new int[] {0x20, 0x01, 0x00, 0x00}, 4)
                || matchesPrefix(bytes, new int[] {0x20, 0x01, 0x00, 0x02}, 4)
                || matchesPrefix(bytes, new int[] {0x20, 0x02}, 2)
                || matchesPrefix(bytes, new int[] {0x00, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0}, 12)) {
            return false;
        }
        return true;
    }

    private boolean matchesPrefix(byte[] address, int[] prefix, int length) {
        for (int i = 0; i < length; i++) {
            if (unsigned(address[i]) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint <= 0x20 || codePoint == 0x7f);
    }

    private boolean containsEncodedControlCharacter(String value) {
        for (int i = 0; i + 2 < value.length(); i++) {
            if (value.charAt(i) != '%') {
                continue;
            }
            int high = Character.digit(value.charAt(i + 1), 16);
            int low = Character.digit(value.charAt(i + 2), 16);
            if (high >= 0 && low >= 0) {
                int decoded = (high << 4) + low;
                if (decoded < 0x20 || decoded == 0x7f) {
                    return true;
                }
            }
        }
        return false;
    }

    private IllegalArgumentException rejected(String reason) {
        return new IllegalArgumentException("WAF endpoint 거부: " + reason);
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
