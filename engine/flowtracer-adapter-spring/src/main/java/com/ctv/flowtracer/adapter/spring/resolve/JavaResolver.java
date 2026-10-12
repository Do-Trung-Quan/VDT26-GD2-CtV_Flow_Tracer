package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.adapter.spring.config.ApplicationConfigReader;
import com.ctv.flowtracer.adapter.spring.config.PropertyLookup;
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
 * <p>Giai đoạn 2: endpoint + module, chẩn đoán file lỗi cú pháp. Lớp xử lý, tiêm phụ thuộc, truy vấn, bảng làm ở
 * Giai đoạn 3.
 */
public final class JavaResolver {

    private static final Comparator<Diagnostic> DIAGNOSTIC_ORDER = Comparator.comparing(
                    Diagnostic::filePath, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Diagnostic::line, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Diagnostic::message);

    private final ApplicationConfigReader configReader = new ApplicationConfigReader();
    private final EndpointBuilder endpointBuilder = new EndpointBuilder();

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

        diagnostics.sort(DIAGNOSTIC_ORDER);
        return new IrFragment(SpringStackAdapter.ID, endpoints.endpoints(), List.of(), List.of(), diagnostics).sorted();
    }
}
