package com.ctv.flowtracer.adapter.spring.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.Guard;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.ThrowStmt;
import org.junit.jupiter.api.Test;

/** Điều kiện bao quanh {@code throw} / lời gọi, kể cả trong thân lambda của các hàm Optional / forEach. */
class GuardResolverTest {

    private static Guard guardOfThrowIn(String statement) {
        var unit = StaticJavaParser.parse("class A { void m() { " + statement + " } }");
        return GuardResolver.guardOf(unit.findFirst(ThrowStmt.class).orElseThrow());
    }

    @Test
    void throwInsideIfPresentLambdaIsAConditionalBranchNotAnUnconditionalEnd() {
        Guard guard = guardOfThrowIn("repo.find(id).filter(e -> e.ok()).ifPresent(e -> { throw new IllegalStateException(); });");

        assertThat(guard).isNotNull();
        assertThat(guard.type()).isEqualTo(Guard.Type.IF);
        assertThat(guard.text()).endsWith(".isPresent()").contains("repo.find(id)");
        assertThat(guard.branch()).isEqualTo("then");
    }

    @Test
    void ifPresentOrElseDistinguishesBothLambdas() {
        var unit = StaticJavaParser.parse(
                "class A { void m() { opt.ifPresentOrElse(e -> { throw new X1(); }, () -> { throw new X2(); }); } }");
        var throwsInOrder = unit.findAll(ThrowStmt.class);

        assertThat(GuardResolver.guardOf(throwsInOrder.get(0)).type()).isEqualTo(Guard.Type.IF);
        assertThat(GuardResolver.guardOf(throwsInOrder.get(1)).type()).isEqualTo(Guard.Type.ELSE);
    }

    @Test
    void orElseGetRunsOnlyWhenEmptyAndForEachLoops() {
        assertThat(guardOfThrowIn("opt.orElseGet(() -> { throw new X(); });").type()).isEqualTo(Guard.Type.ELSE);
        assertThat(guardOfThrowIn("items.forEach(i -> { throw new X(); });").type()).isEqualTo(Guard.Type.LOOP);
    }

    @Test
    void lambdaOfAnUnknownMethodAndPlainThrowKeepTheirOldBehaviour() {
        assertThat(guardOfThrowIn("run(() -> { throw new X(); });")).isNull();
        assertThat(guardOfThrowIn("throw new X();")).isNull();
        assertThat(guardOfThrowIn("if (a) { throw new X(); }").type()).isEqualTo(Guard.Type.IF);
    }
}
