package io.zipit.core.test;

import io.zipit.core.runtime.ZipitVM;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct tests for the reduced instruction set implemented by {@link ZipitVM}.
 *
 * <p>Opcode numbers are JVM opcodes; operands follow the opcode in the code
 * array. These cases cover stack manipulation forms that javac rarely emits but
 * that must stay correct, plus floating-point NaN comparison semantics.</p>
 */
class VirtualMachineTest {

    @Test
    void dupX2KeepsOperandOrder() {
        // 3, 2, 1 -> 1, 3, 2, 1 -> 7
        int[] code = {6, 5, 4, 91, 96, 96, 96, 172};
        assertEquals(7, ((Number) run(code, new Object[0])).intValue());
    }

    @Test
    void dupX2HandlesWideSecondValue() {
        // 0L, 2 -> 2, 0L, 2 -> pop, pop2 -> 2
        int[] code = {9, 5, 91, 87, 88, 172};
        assertEquals(2, ((Number) run(code, new Object[0])).intValue());
    }

    @Test
    void dup2X1KeepsOperandOrder() {
        // 3, 2, 1 -> 2, 1, 3, 2, 1 -> 9
        int[] code = {6, 5, 4, 93, 96, 96, 96, 96, 172};
        assertEquals(9, ((Number) run(code, new Object[0])).intValue());
    }

    @Test
    void dup2X1HandlesWideTopValue() {
        // 2, 1L -> 1L, 2, 1L -> pop2 -> 2
        int[] code = {5, 10, 93, 88, 172};
        assertEquals(2, ((Number) run(code, new Object[0])).intValue());
    }

    @Test
    void dup2X2KeepsOperandOrder() {
        // 4, 3, 2, 1 -> 2, 1, 4, 3, 2, 1 -> 13
        int[] code = {7, 6, 5, 4, 94, 96, 96, 96, 96, 96, 172};
        assertEquals(13, ((Number) run(code, new Object[0])).intValue());
    }

    @Test
    void dup2X2HandlesTwoWideValues() {
        // 0L, 1L -> 1L, 0L, 1L -> 1L + 1L = 2L
        int[] code = {9, 10, 94, 97, 97, 173};
        assertEquals(2L, ((Number) run(code, new Object[0])).longValue());
    }

    @Test
    void floatingComparisonsMatchJvmNanSemantics() {
        Object[] constants = new Object[]{Double.valueOf(Double.NaN), Double.valueOf(1.0),
                Double.valueOf(-0.0d), Double.valueOf(0.0d)};
        // DCMPL(NaN, 1.0) = -1
        assertEquals(-1, ((Number) run(new int[]{18, 0, 18, 1, 151, 172}, constants)).intValue());
        // DCMPG(NaN, 1.0) = 1
        assertEquals(1, ((Number) run(new int[]{18, 0, 18, 1, 152, 172}, constants)).intValue());
        // DCMPL(-0.0, 0.0) = 0
        assertEquals(0, ((Number) run(new int[]{18, 2, 18, 3, 151, 172}, constants)).intValue());

        Object[] floats = new Object[]{Float.valueOf(Float.NaN), Float.valueOf(1.0f),
                Float.valueOf(-0.0f), Float.valueOf(0.0f)};
        // FCMPL(NaN, 1.0) = -1
        assertEquals(-1, ((Number) run(new int[]{18, 0, 18, 1, 149, 172}, floats)).intValue());
        // FCMPG(NaN, 1.0) = 1
        assertEquals(1, ((Number) run(new int[]{18, 0, 18, 1, 150, 172}, floats)).intValue());
        // FCMPL(-0.0, 0.0) = 0
        assertEquals(0, ((Number) run(new int[]{18, 2, 18, 3, 149, 172}, floats)).intValue());
    }

    private static Object run(int[] code, Object[] constants) {
        return ZipitVM.run(code, constants, new String[0], new Object[8]);
    }
}
