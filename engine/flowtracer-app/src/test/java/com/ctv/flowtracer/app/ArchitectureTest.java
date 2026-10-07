package com.ctv.flowtracer.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;

/** Luật ranh giới kiến trúc (CLAUDE.md §3.2). KHÔNG tắt hay nới luật để test chạy qua. */
@AnalyzeClasses(packages = "com.ctv.flowtracer", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String APP = "com.ctv.flowtracer.app";
    private static final List<String> FEATURES =
            List.of("workspace", "scan", "graph", "linking", "tracing", "explanation", "ai", "feature", "comparison");

    /** Lõi phân tích (ir, core, adapter-*) là Java thuần. */
    @ArchTest
    static final ArchRule core_is_pure_java = noClasses()
            .that().resideInAnyPackage("com.ctv.flowtracer.ir..", "com.ctv.flowtracer.core..", "com.ctv.flowtracer.adapter..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "java.sql..", "java.net.http..", "org.hibernate..")
            .as("Lõi phân tích không được phụ thuộc Spring/JPA/JDBC/HTTP client");

    /** Lõi không tự đọc file. */
    @ArchTest
    static final ArchRule core_does_not_read_files = noClasses()
            .that().resideInAnyPackage("com.ctv.flowtracer.ir..", "com.ctv.flowtracer.core..", "com.ctv.flowtracer.adapter..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
            .as("Lõi phân tích không được tự đọc file — nội dung được truyền vào qua SourceFile/SourceContentLoader");

    /** flowtracer-core không phụ thuộc adapter. */
    @ArchTest
    static final ArchRule core_does_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("com.ctv.flowtracer.core..")
            .should().dependOnClassesThat().resideInAPackage("com.ctv.flowtracer.adapter..");

    /** Chỉ package ai được dùng Spring AI. */
    @ArchTest
    static final ArchRule only_ai_uses_spring_ai = noClasses()
            .that().resideOutsideOfPackage(APP + ".ai..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.ai..");

    /** Chỉ package source (và cache dữ kiện của scan) được dùng java.nio.file.Files. */
    @ArchTest
    static final ArchRule only_source_reader_reads_files = noClasses()
            .that().resideInAPackage(APP + "..")
            .and().resideOutsideOfPackage(APP + ".source..")
            .and().doNotHaveSimpleName("FactsCache")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
            .as("Chỉ SourceReader được đọc mã nguồn người dùng");

    /** Không dùng Repository/Entity của package chức năng khác — chỉ gọi qua Service công khai. */
    @ArchTest
    static final ArchRule features_do_not_touch_other_features_persistence = CompositeArchRule.of(FEATURES.stream()
            .map(f -> (ArchRule) noClasses()
                    .that().resideInAPackage(APP + "..")
                    .and().resideOutsideOfPackage(APP + "." + f + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(APP + "." + f + ".entity..", APP + "." + f + ".repository..")
                    .as("Package khác không được dùng Repository/Entity của '" + f + "'"))
            .toList()).as("Ranh giới package chức năng");

    /** Controller chỉ gọi Service, không gọi thẳng Repository. */
    @ArchTest
    static final ArchRule controllers_do_not_use_repositories = noClasses()
            .that().resideInAPackage(APP + "..controller..")
            .should().dependOnClassesThat().resideInAPackage(APP + "..repository..")
            .as("Controller phải đi qua Service, không dùng thẳng Repository");

    /** Repository không phụ thuộc ngược lên Service hay Controller. */
    @ArchTest
    static final ArchRule repositories_do_not_depend_upward = noClasses()
            .that().resideInAPackage(APP + "..repository..")
            .should().dependOnClassesThat().resideInAnyPackage(APP + "..service..", APP + "..controller..")
            .as("Repository chỉ truy cập dữ liệu, không gọi Service/Controller");

    /** Entity không phụ thuộc Service/Controller/DTO. */
    @ArchTest
    static final ArchRule entities_are_plain = noClasses()
            .that().resideInAPackage(APP + "..entity..")
            .should().dependOnClassesThat().resideInAnyPackage(APP + "..service..", APP + "..controller..", APP + "..dto..")
            .as("Entity không phụ thuộc Service/Controller/DTO");
}
