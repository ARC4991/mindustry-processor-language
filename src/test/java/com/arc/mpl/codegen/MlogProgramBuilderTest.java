package com.arc.mpl.codegen;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void rendersPhysicalMemoryOperationsAsStructuredInstructions() {
        MlogProgramBuilder builder = new MlogProgramBuilder(MlogLabelStyle.RELEASE);

        builder.write("value", "__mpl_mem0", "7");
        builder.read("result", "__mpl_mem0", "7");

        assertEquals("write value __mpl_mem0 7\nread result __mpl_mem0 7\n", builder.render());
    }

    @Test
    void rendersProfileSpecificPrintCharInstruction() {
        MlogProgramBuilder builder = new MlogProgramBuilder(MlogLabelStyle.RELEASE);

        builder.printChar("__mpl_tmp0");

        assertEquals("printchar __mpl_tmp0\n", builder.render());
    }

    @Test
    void rendersBuildingControlInTheOfficialActionTargetOrder() {
        MlogProgramBuilder builder = new MlogProgramBuilder(MlogLabelStyle.RELEASE);

        builder.buildingControl("duo1", "shoot", List.of("10", "20", "1"));
        builder.buildingControl("switch1", "enabled", List.of("0"));

        assertEquals("""
            control shoot duo1 10 20 1 0
            control enabled switch1 0 0 0 0
            """, builder.render());
    }
}
