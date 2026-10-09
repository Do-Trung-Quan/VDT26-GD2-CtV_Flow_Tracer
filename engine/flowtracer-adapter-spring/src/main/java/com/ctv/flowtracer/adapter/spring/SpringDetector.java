package com.ctv.flowtracer.adapter.spring;

import com.ctv.flowtracer.ir.spi.DetectContext;
import com.ctv.flowtracer.ir.spi.DetectResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nhận diện Spring Boot: {@code pom.xml} / {@code build.gradle} có {@code spring-boot}; đọc phiên bản. Dự án nhiều
 * module: file build gốc không nhắc tới Spring Boot nhưng một module con có thì vẫn nhận gốc (Maven {@code <modules>},
 * Gradle {@code include} trong {@code settings.gradle}).
 */
public final class SpringDetector {

    /** File đặc trưng ở gốc dự án Java. */
    public static final List<String> ROOT_MARKERS =
            List.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts");

    static final String STACK_NAME = "Spring Boot";
    static final double SCORE_DIRECT = 0.95;
    static final double SCORE_VIA_MODULE = 0.9;

    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern PARENT = Pattern.compile("<parent>(.*?)</parent>", Pattern.DOTALL);
    private static final Pattern DEPENDENCY = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL);
    private static final Pattern MODULE = Pattern.compile("<module>\\s*([^<\\s]+)\\s*</module>");
    private static final Pattern GRADLE_PLUGIN_VERSION = Pattern.compile(
            "id\\s*\\(?\\s*['\"]org\\.springframework\\.boot['\"]\\s*\\)?\\s*version\\s*\\(?\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern GRADLE_INCLUDE = Pattern.compile("include\\s*\\(?([^\\n)]*)");
    private static final Pattern QUOTED = Pattern.compile("['\"]:?([^'\"]+)['\"]");

    public DetectResult detect(DetectContext ctx) {
        List<String> files = ctx.relativePaths();
        if (files.contains("pom.xml")) {
            Optional<String> pom = ctx.loader().load("pom.xml").map(SpringDetector::stripXmlComments);
            if (pom.isPresent()) {
                DetectResult r = detectMaven(pom.get(), ctx);
                if (r.matched()) {
                    return r;
                }
            }
        }
        for (String build : List.of("build.gradle", "build.gradle.kts")) {
            if (files.contains(build)) {
                Optional<DetectResult> r = ctx.loader().load(build).flatMap(text -> gradleResult(text, build, SCORE_DIRECT));
                if (r.isPresent()) {
                    return r.get();
                }
            }
        }
        for (String settings : List.of("settings.gradle", "settings.gradle.kts")) {
            if (files.contains(settings)) {
                Optional<DetectResult> r = ctx.loader().load(settings).flatMap(text -> detectGradleModules(text, ctx));
                if (r.isPresent()) {
                    return r.get();
                }
            }
        }
        return DetectResult.none();
    }

    private static DetectResult detectMaven(String pom, DetectContext ctx) {
        if (mentionsSpringBoot(pom)) {
            String version = mavenBootVersion(pom);
            return new DetectResult(SCORE_DIRECT, STACK_NAME, version, "pom.xml có spring-boot" + suffix(version));
        }
        for (String module : modules(pom)) {
            String modulePom = module + "/pom.xml";
            Optional<String> child = ctx.loader().load(modulePom).map(SpringDetector::stripXmlComments);
            if (child.isPresent() && mentionsSpringBoot(child.get())) {
                String version = mavenBootVersion(child.get());
                return new DetectResult(
                        SCORE_VIA_MODULE, STACK_NAME, version, "dự án Maven nhiều module, " + modulePom + " có spring-boot");
            }
        }
        return DetectResult.none();
    }

    private static Optional<DetectResult> detectGradleModules(String settings, DetectContext ctx) {
        for (String module : gradleIncludes(settings)) {
            for (String build : List.of("build.gradle", "build.gradle.kts")) {
                String path = module + "/" + build;
                Optional<DetectResult> r = ctx.loader().load(path).flatMap(text -> gradleResult(text, path, SCORE_VIA_MODULE));
                if (r.isPresent()) {
                    return r;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<DetectResult> gradleResult(String text, String file, double score) {
        if (!text.contains("org.springframework.boot") && !text.contains("spring-boot")) {
            return Optional.empty();
        }
        Matcher m = GRADLE_PLUGIN_VERSION.matcher(text);
        String version = m.find() ? m.group(1) : null;
        return Optional.of(new DetectResult(score, STACK_NAME, version, file + " có spring-boot" + suffix(version)));
    }

    static boolean mentionsSpringBoot(String pom) {
        return pom.contains("<artifactId>spring-boot");
    }

    /** Phiên bản Spring Boot: parent spring-boot-starter-parent, hoặc BOM spring-boot-dependencies; giải ${property}. */
    static String mavenBootVersion(String pom) {
        Matcher parent = PARENT.matcher(pom);
        if (parent.find() && parent.group(1).contains("<artifactId>spring-boot-starter-parent</artifactId>")) {
            return resolveProperty(tag(parent.group(1), "version"), pom);
        }
        Matcher dep = DEPENDENCY.matcher(pom);
        while (dep.find()) {
            if (dep.group(1).contains("<artifactId>spring-boot-dependencies</artifactId>")) {
                return resolveProperty(tag(dep.group(1), "version"), pom);
            }
        }
        return resolveProperty(tag(pom, "spring-boot.version"), pom);
    }

    private static String resolveProperty(String value, String pom) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        String resolved = tag(pom, value.substring(2, value.length() - 1));
        return resolved == null || resolved.startsWith("${") ? null : resolved;
    }

    private static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + ">\\s*([^<]+?)\\s*</" + Pattern.quote(name) + ">")
                .matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    static List<String> modules(String pom) {
        var result = new ArrayList<String>();
        Matcher m = MODULE.matcher(pom);
        while (m.find()) {
            result.add(m.group(1).replace('\\', '/'));
        }
        return result.stream().filter(SpringDetector::isSafeChild).sorted().toList();
    }

    static List<String> gradleIncludes(String settings) {
        var result = new ArrayList<String>();
        Matcher line = GRADLE_INCLUDE.matcher(settings);
        while (line.find()) {
            Matcher q = QUOTED.matcher(line.group(1));
            while (q.find()) {
                result.add(q.group(1).replace(':', '/'));
            }
        }
        return result.stream().filter(SpringDetector::isSafeChild).sorted().toList();
    }

    /** Chỉ đọc module nằm bên trong thư mục ứng viên. */
    private static boolean isSafeChild(String path) {
        return !path.isBlank() && !path.startsWith("/") && !path.contains("..") && !path.contains(":");
    }

    private static String stripXmlComments(String xml) {
        return XML_COMMENT.matcher(xml).replaceAll("");
    }

    private static String suffix(String version) {
        return version == null ? "" : " " + version;
    }
}
