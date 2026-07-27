package com.arc.mpl.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MilCodeGeneratorTest {
    private final MilCodeGenerator generator = new MilCodeGenerator();

    @Test
    void wrapsEachGeneratedInstructionInATargetMacro() {
        String mil = generator.generate("""
            _0:
            op add temporary 1 2
            set value temporary
            print \"a message with spaces\"
            printflush message1
            ubind @dagger
            sensor health @unit @health
            ucontrol move 1 2 0 0 0
            jump _0 always 0 0
            stop
            """);

        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // @logic.* 由所选 target profile 展开为游戏 mlog 指令。
            @logic.label(_0);
            @logic.op(add, temporary, 1, 2);
            @logic.set(value, temporary);
            @logic.print(\"a message with spaces\");
            @logic.printFlush(message1);
            @logic.unitBind(@dagger);
            @logic.sensor(health, @unit, @health);
            @logic.unitControl(move, 1, 2, 0, 0, 0);
            @logic.jump(_0, always, 0, 0);
            @logic.stop();
            """, mil);
    }

    @Test
    void rejectsAnOpcodeThatHasNoMilMacro() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> generator.generate("read output cell 0\n"));

        assertEquals("MIL 尚不能序列化 mlog 指令：read", exception.getMessage());
    }
}
