package com.ai.agentplatform.module.app.deploy.support;

import com.ai.agentplatform.module.app.deploy.config.DeployProperties;
import com.ai.agentplatform.module.app.deploy.enums.DeployMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * 生成可分享的完整 URL：优先配置 public-base-url / share-host，否则自动检测局域网 IP。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareUrlResolver {

    private final DeployProperties deployProperties;

    @Value("${server.port:8080}")
    private int serverPort;

    public String buildShareUrl(String path) {
        String normalizedPath = normalizeToPath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            return "";
        }
        return buildShareUrlWithPort(serverPort, normalizedPath);
    }

    public String buildShareUrl(DeployUrlCodec.DeployReference reference) {
        if (reference == null || !StringUtils.hasText(reference.value())) {
            return "";
        }
        return switch (reference.mode()) {
            case LOCAL -> buildShareUrlWithPort(serverPort, reference.value());
            case NGINX -> buildShareUrlWithPort(deployProperties.getNginxPort(), reference.value());
            case DOCKER -> buildShareUrlWithPort(Integer.parseInt(reference.value()), "/");
        };
    }

    /** 将数据库条目解析为完整 URL（兼容 local:http://...、local:/sites/...、纯 URL） */
    public String resolveFullUrlFromReference(DeployUrlCodec.DeployReference ref) {
        if (ref == null || !StringUtils.hasText(ref.value())) {
            return "";
        }
        String value = ref.value().trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return buildShareUrl(ref);
    }

    /** @deprecated 使用 {@link #resolveFullUrlFromReference(DeployUrlCodec.DeployReference)} */
    @Deprecated
    public String normalizeStoredDeployEntry(String entry) {
        return resolveFullUrlFromReference(DeployUrlCodec.parseEntry(entry));
    }

    /** 识别一条部署记录对应的部署方式 */
    public DeployMode detectDeployMode(String entry) {
        if (!StringUtils.hasText(entry)) {
            return DeployMode.LOCAL;
        }
        String trimmed = entry.trim();
        for (DeployMode mode : DeployMode.values()) {
            String prefix = mode.getCode() + ":";
            if (trimmed.startsWith(prefix)) {
                return mode;
            }
        }
        return DeployUrlCodec.parseEntry(entry).mode();
    }

    public String buildShareUrlWithPort(int port, String path) {
        String host = resolveHost();
        String base = "http://" + host + ":" + port;
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return base + "/";
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    /** 本机 Headless 截图专用：固定走 127.0.0.1，避免 Chrome 访问局域网 IP 卡死 */
    public String buildLocalhostUrl(int port, String path) {
        String normalizedPath = normalizeToPath(path);
        String base = "http://127.0.0.1:" + port;
        if (!StringUtils.hasText(normalizedPath) || "/".equals(normalizedPath)) {
            return base + "/";
        }
        return base + normalizedPath;
    }

    public String resolveHost() {
        if (StringUtils.hasText(deployProperties.getShareHost())) {
            return deployProperties.getShareHost().trim();
        }
        String lanIp = findBestLanIpv4();
        if (lanIp != null) {
            return lanIp;
        }
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            if (StringUtils.hasText(host) && !"127.0.0.1".equals(host)) {
                return host;
            }
        } catch (Exception e) {
            log.warn("获取本机地址失败", e);
        }
        return "localhost";
    }

    /** 将数据库中的完整 URL 或相对路径统一转为 /sites/... 形式 */
    public String normalizeToPath(String urlOrPath) {
        if (!StringUtils.hasText(urlOrPath)) {
            return "";
        }
        String value = urlOrPath.trim();
        if (value.startsWith("/")) {
            return value;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(value);
                String path = uri.getPath();
                return StringUtils.hasText(path) ? path : "/";
            } catch (IllegalArgumentException e) {
                log.warn("无法解析分享路径: {}", urlOrPath);
            }
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    public String resolveBaseUrl() {
        if (StringUtils.hasText(deployProperties.getPublicBaseUrl())) {
            return trimTrailingSlash(deployProperties.getPublicBaseUrl().trim());
        }
        return buildShareUrlWithPort(serverPort, "").replaceAll("/$", "");
    }

    private String findBestLanIpv4() {
        try {
            List<String> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                String displayName = networkInterface.getDisplayName().toLowerCase();
                if (isVirtualAdapter(displayName)) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    if (ip == null || ip.startsWith("169.254.")) {
                        continue;
                    }
                    candidates.add(ip);
                }
            }
            return candidates.stream()
                    .max(Comparator.comparingInt(this::scoreIp))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("检测局域网 IP 失败，回退 localhost", e);
            return null;
        }
    }

    private boolean isVirtualAdapter(String name) {
        return name.contains("vmware")
                || name.contains("virtualbox")
                || name.contains("vethernet")
                || name.contains("hyper-v")
                || name.contains("wsl")
                || name.contains("docker")
                || name.contains("npcap")
                || name.contains("tap")
                || name.contains("bluetooth");
    }

    /** 分数越高越优先：真实校园网/办公网 IP 优先于虚拟网卡地址 */
    private int scoreIp(String ip) {
        if (ip.startsWith("10.") && !ip.startsWith("10.0.0.")) {
            return 100;
        }
        if (ip.startsWith("192.168.")) {
            if (ip.startsWith("192.168.38.") || ip.startsWith("192.168.56.") || ip.startsWith("192.168.122.")) {
                return 10;
            }
            return 60;
        }
        if (ip.startsWith("172.")) {
            int second = Integer.parseInt(ip.split("\\.")[1]);
            if (second >= 16 && second <= 31) {
                return 50;
            }
        }
        return 20;
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
