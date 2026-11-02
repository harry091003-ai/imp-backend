package com.example.APIGateway.utils;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// BCR-01: which address a request is counted by. The X-Real-IP note is believed ONLY when the connection comes from
// a proxy named in gateway.trusted-proxies. From anyone else it is ignored, because anyone can write that header.
class ClientIpResolverTest {

    private static final String NGINX = "10.0.0.5";

    private static ServerHttpRequest from(String connectionIp, String realIpHeader) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post("/auth/login")
                .remoteAddress(new InetSocketAddress(connectionIp, 50000));
        if (realIpHeader != null) {
            request.header(ClientIpResolver.REAL_IP_HEADER, realIpHeader);
        }
        return request.build();
    }

    // Nothing set = exactly today's behaviour: the connection address, and the header is ignored.
    @Test
    void withNoTrustedProxyTheHeaderIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertEquals("10.0.0.5", resolver.clientIp(from(NGINX, "198.51.100.1")));
    }

    @Test
    void fromTheTrustedProxyTheVisitorAddressIsUsed() {
        ClientIpResolver resolver = new ClientIpResolver(NGINX);
        assertEquals("198.51.100.1", resolver.clientIp(from(NGINX, "198.51.100.1")));
    }

    // The most important rule: a stranger who connects directly cannot pick their own address.
    @Test
    void aStrangersFakeHeaderIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(NGINX);
        assertEquals("203.0.113.7", resolver.clientIp(from("203.0.113.7", "198.51.100.99")));
    }

    // Only X-Real-IP counts. nginx overwrites it on every request; X-Forwarded-For only gets added to, so a value the
    // visitor typed in would still be in it.
    @Test
    void xForwardedForIsIgnoredEvenFromTheTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(NGINX);
        ServerHttpRequest request = MockServerHttpRequest.post("/auth/login")
                .remoteAddress(new InetSocketAddress(NGINX, 50000))
                .header("X-Forwarded-For", "198.51.100.99")
                .build();
        assertEquals(NGINX, resolver.clientIp(request));
    }

    // nginx set up without the X-Real-IP line: fall back to nginx's own address. Still limited, never let through free.
    @Test
    void theTrustedProxyWithNoHeaderFallsBackToItsOwnAddress() {
        ClientIpResolver resolver = new ClientIpResolver(NGINX);
        assertEquals(NGINX, resolver.clientIp(from(NGINX, null)));
    }

    // Anything that is not one plain address is not believed: a list, a name, junk, or a huge value.
    @Test
    void aHeaderThatIsNotOnePlainAddressIsNotBelieved() {
        ClientIpResolver resolver = new ClientIpResolver(NGINX);
        assertEquals(NGINX, resolver.clientIp(from(NGINX, "198.51.100.1, 198.51.100.2")));
        assertEquals(NGINX, resolver.clientIp(from(NGINX, "evil.example.com")));
        assertEquals(NGINX, resolver.clientIp(from(NGINX, "999.1.1.1")));
        assertEquals(NGINX, resolver.clientIp(from(NGINX, "")));
        assertEquals(NGINX, resolver.clientIp(from(NGINX, "1".repeat(500))));
    }

    // Two spellings of one IPv6 address are one visitor, so writing it differently is not a way around the limit.
    @Test
    void ipv6AddressesAreWrittenOneWay() {
        ClientIpResolver resolver = new ClientIpResolver("::1");
        assertEquals("2001:db8:0:0:0:0:0:1", resolver.clientIp(from("0:0:0:0:0:0:0:1", "2001:db8::1")));
        assertEquals("2001:db8:0:0:0:0:0:1", resolver.clientIp(from("0:0:0:0:0:0:0:1", "2001:DB8:0::1")));
    }

    @Test
    void severalTrustedProxiesCanBeListed() {
        ClientIpResolver resolver = new ClientIpResolver(" 10.0.0.5 , 10.0.0.6 ");
        assertEquals("198.51.100.1", resolver.clientIp(from("10.0.0.6", "198.51.100.1")));
    }

    // A mistake in the setting must stop the gateway at start-up, not quietly trust the wrong thing. A name like
    // "nginx" is refused too: it would need a DNS lookup, and DNS answers can change.
    @Test
    void aBadSettingStopsTheGatewayAtStartUp() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ClientIpResolver("10.0.0.5, nginx"));
        assertTrue(error.getMessage().contains("nginx"), error.getMessage());
    }

    @Test
    void anUnknownConnectionAddressIsStillCounted() {
        ClientIpResolver resolver = new ClientIpResolver(NGINX);
        assertEquals("unknown", resolver.clientIp(MockServerHttpRequest.post("/auth/login").build()));
    }
}
