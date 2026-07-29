package com.arc.mpl.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MlogProgramBuilderTest {
    @Test
    void resolvesCounterTargetsUsingExecutableInstructionAddressesOnly() {
        MlogProgramBuilder builder = new MlogProgramBuilder(MlogLabelStyle.DEBUG);
        MlogProgramBuilder.Label target = builder.newLabel("target");
        MlogProgramBuilder.Label marker = builder.newLabel("marker");

        builder.setCounter(target);
        builder.label(marker);
        builder.set("value", "1");
        builder.label(target);
        builder.stop();

        assertEquals("""
            set @counter 2
            mpl_marker_1:
            set value 1
            mpl_target_0:
            stop
            """, builder.render());
    }
}
