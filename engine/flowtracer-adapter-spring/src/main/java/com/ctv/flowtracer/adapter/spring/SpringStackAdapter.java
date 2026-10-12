package com.ctv.flowtracer.adapter.spring;

import com.ctv.flowtracer.adapter.spring.extract.JavaFileExtractor;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.adapter.spring.resolve.JavaResolver;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.NoiseFilter;
import com.ctv.flowtracer.ir.SourceRole;
import com.ctv.flowtracer.ir.spi.DetectContext;
import com.ctv.flowtracer.ir.spi.DetectResult;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Hiện thực StackAdapter cho Java / Spring Boot: id "spring", vai trò BACKEND. */
public final class SpringStackAdapter implements StackAdapter {

    public static final String ID = "spring";

    private static final Pattern CONFIG_FILE =
            Pattern.compile("^(?:.*/)?src/main/resources/application(?:-[^/]+)?\\.(?:yml|yaml|properties)$");

    private final SpringDetector detector = new SpringDetector();
    private final JavaFileExtractor extractor = new JavaFileExtractor();
    private final JavaResolver resolver = new JavaResolver();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<SourceRole> roles() {
        return Set.of(SourceRole.BACKEND);
    }

    @Override
    public Class<? extends FileFacts> factsType() {
        return JavaFileFacts.class;
    }

    @Override
    public boolean analyzes(String relativePath) {
        return relativePath.endsWith(".java");
    }

    /** Cấu hình Spring của từng module (mặc định và theo profile) — quyết định context-path và đường dẫn dùng thuộc tính. */
    @Override
    public boolean affectsAnalysis(String relativePath) {
        return CONFIG_FILE.matcher(relativePath).matches();
    }

    @Override
    public String factsVersion() {
        return JavaFileExtractor.VERSION;
    }

    @Override
    public List<String> rootMarkers() {
        return SpringDetector.ROOT_MARKERS;
    }

    @Override
    public DetectResult detect(DetectContext ctx) {
        return detector.detect(ctx);
    }

    @Override
    public FileFacts extract(ExtractContext ctx, SourceFile file) {
        return extractor.extract(ctx, file);
    }

    @Override
    public IrFragment resolve(ResolveContext ctx, List<FileFacts> facts) {
        return resolver.resolve(ctx, facts.stream().map(JavaFileFacts.class::cast).toList());
    }

    @Override
    public List<NoiseFilter> defaultNoiseFilters() {
        return SpringNoiseFilters.DEFAULTS;
    }
}
