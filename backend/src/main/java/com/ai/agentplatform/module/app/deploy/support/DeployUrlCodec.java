package com.ai.agentplatform.module.app.deploy.support;

import com.ai.agentplatform.module.app.deploy.enums.DeployMode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 数据库存储格式：{mode}:{完整URL}，多种部署方式以「; 」分隔
 * 示例：local:http://172.20.10.4:8080/sites/1/index.html; nginx:http://172.20.10.4:8888/apps/1/index.html
 */
public final class DeployUrlCodec {

    /** 分号+空格，在 Navicat / DBeaver 等工具的单元格里一眼可辨 */
    private static final String SEPARATOR = "; ";

    /** 兼容旧格式无分隔符粘连：local:...nginx:... */
    private static final Pattern LEGACY_MODE_BOUNDARY = Pattern.compile("(?=(?:local|nginx|docker):)");

    private DeployUrlCodec() {
    }

    public record DeployReference(DeployMode mode, String value) {
    }

    public static String encodeStored(DeployMode mode, String fullShareUrl) {
        return mode.getCode() + ":" + fullShareUrl;
    }

    /**
     * 合并新的部署结果：每种方式保留一条「mode:url」，同方式同 URL 不重复写入。
     */
    public static String merge(
            String existingStored,
            DeployMode mode,
            String fullShareUrl,
            Function<String, DeployReference> entryParser,
            Function<DeployReference, String> fullUrlResolver) {
        Map<DeployMode, String> byMode = buildByMode(existingStored, entryParser, fullUrlResolver);
        String encoded = encodeStored(mode, fullShareUrl);
        if (encoded.equals(byMode.get(mode))) {
            return normalizeStored(existingStored, entryParser, fullUrlResolver);
        }
        byMode.put(mode, encoded);
        return String.join(SEPARATOR, byMode.values());
    }

    public static List<DeployReference> parseAllEntries(String stored) {
        if (!StringUtils.hasText(stored)) {
            return List.of();
        }
        List<DeployReference> refs = new ArrayList<>();
        for (String part : splitStored(stored)) {
            refs.add(parseEntry(part));
        }
        return refs;
    }

    /** 解析单条存储：local:http://... / local:/sites/... / 纯 URL */
    public static DeployReference parseEntry(String entry) {
        if (!StringUtils.hasText(entry)) {
            return new DeployReference(DeployMode.LOCAL, "");
        }
        String trimmed = entry.trim();
        for (DeployMode mode : DeployMode.values()) {
            String prefix = mode.getCode() + ":";
            if (trimmed.startsWith(prefix)) {
                return new DeployReference(mode, trimmed.substring(prefix.length()));
            }
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return new DeployReference(inferModeFromUrl(trimmed), trimmed);
        }
        return new DeployReference(DeployMode.LOCAL, normalizeLegacyPath(trimmed));
    }

    /** @deprecated 使用 {@link #parseEntry(String)} */
    @Deprecated
    public static DeployReference parseLegacyReference(String stored) {
        return parseEntry(stored);
    }

    private static Map<DeployMode, String> buildByMode(
            String stored,
            Function<String, DeployReference> entryParser,
            Function<DeployReference, String> fullUrlResolver) {
        Map<DeployMode, String> byMode = new LinkedHashMap<>();
        for (String part : splitStored(stored)) {
            DeployReference ref = entryParser.apply(part);
            if (!StringUtils.hasText(ref.value())) {
                continue;
            }
            String fullUrl = fullUrlResolver.apply(ref);
            if (!StringUtils.hasText(fullUrl)) {
                continue;
            }
            byMode.put(ref.mode(), encodeStored(ref.mode(), fullUrl));
        }
        return byMode;
    }

    private static String normalizeStored(
            String stored,
            Function<String, DeployReference> entryParser,
            Function<DeployReference, String> fullUrlResolver) {
        Map<DeployMode, String> byMode = buildByMode(stored, entryParser, fullUrlResolver);
        if (byMode.isEmpty()) {
            return StringUtils.hasText(stored) ? stored.trim() : "";
        }
        return String.join(SEPARATOR, byMode.values());
    }

    private static DeployMode inferModeFromUrl(String url) {
        try {
            String path = java.net.URI.create(url).getPath();
            if (StringUtils.hasText(path)) {
                if (path.startsWith("/sites/") || path.contains("/sites/")) {
                    return DeployMode.LOCAL;
                }
                if (path.startsWith("/apps/") || path.contains("/apps/")) {
                    return DeployMode.NGINX;
                }
            }
            return DeployMode.DOCKER;
        } catch (IllegalArgumentException e) {
            return DeployMode.LOCAL;
        }
    }

    private static List<String> splitStored(String stored) {
        String trimmed = stored.trim();
        List<String> parts = new ArrayList<>();

        if (trimmed.contains(";")) {
            for (String part : trimmed.split(";\\s*")) {
                if (StringUtils.hasText(part)) {
                    parts.add(part.trim());
                }
            }
            return parts;
        }
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            String normalized = trimmed.replace("\r\n", "\n").replace('\r', '\n');
            for (String part : normalized.split("\n")) {
                if (StringUtils.hasText(part)) {
                    parts.add(part.trim());
                }
            }
            return parts;
        }
        if (trimmed.contains("|")) {
            for (String part : trimmed.split("\\|")) {
                if (StringUtils.hasText(part)) {
                    parts.add(part.trim());
                }
            }
            return parts;
        }
        if (countLegacyModePrefixes(trimmed) > 1) {
            for (String part : LEGACY_MODE_BOUNDARY.split(trimmed)) {
                if (StringUtils.hasText(part)) {
                    parts.add(part.trim());
                }
            }
            return parts;
        }
        parts.add(trimmed);
        return parts;
    }

    private static int countLegacyModePrefixes(String stored) {
        int count = 0;
        for (DeployMode mode : DeployMode.values()) {
            if (stored.contains(mode.getCode() + ":")) {
                count++;
            }
        }
        return count;
    }

    private static String normalizeLegacyPath(String stored) {
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            try {
                return java.net.URI.create(stored).getPath();
            } catch (IllegalArgumentException e) {
                return stored;
            }
        }
        return stored;
    }
}
