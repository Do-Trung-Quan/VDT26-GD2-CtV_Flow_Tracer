package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.SpringLinkRules;
import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.adapter.spring.config.ApplicationConfigReader;
import com.ctv.flowtracer.adapter.spring.config.PropertyLookup;
import com.ctv.flowtracer.adapter.spring.config.QuerySinkRules;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PHA 2: ghép dữ kiện của cả Source → IrFragment (đã sắp xếp ổn định).
 *
 * <p>Endpoint + module (Giai đoạn 2); từ endpoint đi xuống controller → service → repository → truy vấn → bảng
 * (Giai đoạn 3, xem {@link FlowResolver}); kiểu dữ liệu vào / ra và thông báo lỗi (Giai đoạn 6, xem {@link DtoSchemaBuilder},
 * {@link MessageResolver}); chẩn đoán file lỗi cú pháp.
 */
public final class JavaResolver {

    private static final Comparator<Diagnostic> DIAGNOSTIC_ORDER = Comparator.comparing(
                    Diagnostic::filePath, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Diagnostic::line, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Diagnostic::message);

    private final ApplicationConfigReader configReader = new ApplicationConfigReader();
    private final EndpointBuilder endpointBuilder = new EndpointBuilder();
    private final MessageResolver messageResolver = new MessageResolver();

    public IrFragment resolve(ResolveContext ctx, List<JavaFileFacts> facts) {
        var diagnostics = new ArrayList<Diagnostic>();
        facts.stream()
                .filter(f -> !f.parseErrors().isEmpty())
                .forEach(f -> diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR, f.relativePath(), null, String.join("; ", f.parseErrors()))));
        List<JavaFileFacts> parsed = facts.stream().filter(f -> f.parseErrors().isEmpty()).toList();

        ApplicationConfigReader.Result config = configReader.read(ctx);
        diagnostics.addAll(config.diagnostics());
        EndpointBuilder.Result endpoints =
                endpointBuilder.build(parsed, config, new PropertyLookup(ctx.allPaths(), ctx.loader()));
        diagnostics.addAll(endpoints.diagnostics());

        // Kiểu dữ liệu vào / ra của từng endpoint (Giai đoạn 6): node DATA_TYPE + biểu thức kiểu ghi lên endpoint
        DtoSchemaBuilder.Result types = new DtoSchemaBuilder(
                        new ClassIndex(parsed), parsed, path -> config.forFile(path).map(ApplicationConfigReader.AppConfig::jsonNamingStrategy).orElse(null))
                .build(endpoints.endpoints());

        FlowResolver.Result flow = new FlowResolver(parsed, new QuerySinkRules(ctx.manualRules()), messageResolver.loadMessages(ctx))
                .resolve(types.endpoints());
        diagnostics.addAll(flow.diagnostics());

        var nodes = new ArrayList<>(types.endpoints());
        nodes.addAll(flow.nodes());
        nodes.addAll(types.dataTypes());
        diagnostics.sort(DIAGNOSTIC_ORDER);
        var linkRules = new SpringLinkRules().detect(endpoints.endpoints());
        var edges = new ArrayList<>(flow.edges());
        edges.addAll(types.edges());
        return new IrFragment(SpringStackAdapter.ID, nodes, edges, linkRules, diagnostics).sorted();
    }
}
