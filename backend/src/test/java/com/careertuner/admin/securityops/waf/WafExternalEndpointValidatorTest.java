package com.careertuner.admin.securityops.waf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

class WafExternalEndpointValidatorTest {

    @Test
    void acceptsStandardHttpsOnlyWhenEveryDnsResultIsPublic() throws Exception {
        WafExternalEndpointValidator validator = validator("93.184.216.34", "2606:2800:220:1:248:1893:25c8:1946");

        assertThat(validator.validate("https://waf.example.com/v1/rules?scope=global"))
                .hasScheme("https")
                .hasHost("waf.example.com")
                .hasPort(-1);
    }

    @Test
    void rejectsNonHttpsUserInfoFragmentsAndNonStandardPorts() throws Exception {
        WafExternalEndpointValidator validator = validator("93.184.216.34");

        for (String endpoint : new String[] {
                "http://waf.example.com/rules",
                "https://user:password@waf.example.com/rules",
                "https://waf.example.com:8443/rules",
                "https://waf.example.com/rules#internal",
                "https://waf.example.com:/rules",
                "https://waf.example.com/%0a"
        }) {
            assertThatThrownBy(() -> validator.validate(endpoint))
                    .as(endpoint)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("WAF endpoint 거부");
        }
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalSharedAndMetadataAddresses() throws Exception {
        for (String address : new String[] {
                "127.0.0.1",
                "10.0.0.10",
                "172.16.0.10",
                "192.168.0.10",
                "169.254.169.254",
                "100.100.100.200",
                "168.63.129.16",
                "::1",
                "fe80::1",
                "fd00:ec2::254"
        }) {
            WafExternalEndpointValidator validator = validator(address);

            assertThatThrownBy(() -> validator.validate("https://waf.example.com/rules"))
                    .as(address)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("공개 네트워크가 아닌");
        }
    }

    @Test
    void rejectsDirectIpLiteralSsrFEndpoints() {
        WafExternalEndpointValidator validator = new WafExternalEndpointValidator();

        for (String endpoint : new String[] {
                "https://127.0.0.1/rules",
                "https://10.0.0.1/rules",
                "https://169.254.169.254/latest/meta-data",
                "https://100.100.100.200/latest/meta-data",
                "https://[::1]/rules",
                "https://[fd00:ec2::254]/latest/meta-data"
        }) {
            assertThatThrownBy(() -> validator.validate(endpoint))
                    .as(endpoint)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("공개 네트워크가 아닌");
        }
    }

    @Test
    void rejectsMixedPublicAndPrivateDnsAnswers() throws Exception {
        WafExternalEndpointValidator validator = validator("93.184.216.34", "127.0.0.1");

        assertThatThrownBy(() -> validator.validate("https://waf.example.com/rules"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공개 네트워크가 아닌");
    }

    @Test
    void rejectsLocalHostnamesAndDnsFailures() throws Exception {
        WafExternalEndpointValidator localValidator = validator("127.0.0.1");
        WafExternalEndpointValidator unknownValidator = new WafExternalEndpointValidator(host -> {
            throw new UnknownHostException(host);
        });

        assertThatThrownBy(() -> localValidator.validate("https://localhost/rules"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("로컬 호스트");
        assertThatThrownBy(() -> unknownValidator.validate("https://waf.invalid/rules"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNS 조회에 실패");
    }

    private WafExternalEndpointValidator validator(String... addresses) throws Exception {
        InetAddress[] resolved = new InetAddress[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            resolved[i] = InetAddress.getByName(addresses[i]);
        }
        return new WafExternalEndpointValidator(host -> resolved);
    }
}
