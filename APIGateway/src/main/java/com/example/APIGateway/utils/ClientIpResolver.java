package com.example.APIGateway.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// BCR-01: which address a request is counted by, for the per-IP rate limits.
//
// Behind nginx every connection comes FROM nginx, so the connection address alone made all visitors one caller.
// nginx writes the visitor's real address into X-Real-IP (proxy_set_header X-Real-IP $remote_addr), replacing any
// value the visitor sent. But anyone can WRITE that header, so it is believed only when the connection itself comes
// from a proxy listed in gateway.trusted-proxies. From anyone else it is ignored and the connection address is used.
//
// X-Forwarded-For is never read: nginx adds to it instead of replacing it, so a value the visitor made up stays in it.
// The list is plain addresses only. A name like "nginx" is refused: it needs a DNS lookup, and DNS answers can change.
// An empty list (the default) is exactly the old behaviour.
@Component
public class ClientIpResolver {

    private static final Logger logger = Logger.getLogger(ClientIpResolver.class.getName());

    public static final String REAL_IP_HEADER = "X-Real-IP";

    // The longest written IPv6 address is 45 characters. Anything longer is not one address.
    private static final int MAX_ADDRESS_LENGTH = 45;

    // No leading zeros: Java could read "010.0.0.1" as a host NAME and look it up in DNS.
    private static final Pattern IPV4 = Pattern.compile(
            "(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})");
    // Only these characters, and at least one ':', so Java parses it as an IPv6 address and never asks DNS.
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]+");

    private final Set<InetAddress> trustedProxies;

    @Autowired
    public ClientIpResolver(@Value("${gateway.trusted-proxies}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(ClientIpResolver::requireAddress)
                .collect(Collectors.toUnmodifiableSet());
        logger.info(this.trustedProxies.isEmpty()
                ? "No trusted proxy: the per-IP rate limits count the connection address (X-Real-IP is ignored)"
                : "X-Real-IP is believed only from: " + this.trustedProxies.stream()
                        .map(InetAddress::getHostAddress).sorted().toList());
    }

    // The visitor's address, written one way (so two spellings of one address are one visitor), or "unknown".
    public String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        InetAddress connection = remote.getAddress();
        if (trustedProxies.contains(connection)) {
            InetAddress visitor = parseAddress(request.getHeaders().getFirst(REAL_IP_HEADER));
            if (visitor != null) {
                return visitor.getHostAddress();
            }
            // No usable X-Real-IP from our own proxy (its config is missing the line): count the proxy itself.
            // Shared by everyone behind it, but still limited - never let through for free.
        }
        return connection.getHostAddress();
    }

    private static InetAddress requireAddress(String entry) {
        InetAddress address = parseAddress(entry);
        if (address == null) {
            throw new IllegalArgumentException("gateway.trusted-proxies: '" + entry + "' is not an IP address."
                    + " List plain addresses such as 10.0.0.5, separated by commas.");
        }
        return address;
    }

    // One plain IPv4 or IPv6 address, or null. Never does a DNS lookup.
    static InetAddress parseAddress(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty() || value.length() > MAX_ADDRESS_LENGTH) {
            return null;
        }
        Matcher ipv4 = IPV4.matcher(value);
        if (ipv4.matches()) {
            for (int part = 1; part <= 4; part++) {
                if (Integer.parseInt(ipv4.group(part)) > 255) {
                    return null;
                }
            }
        } else if (value.indexOf(':') < 0 || !IPV6.matcher(value).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException notAnAddress) {
            return null;
        }
    }
}
