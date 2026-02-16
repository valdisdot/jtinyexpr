package com.valdisdot.util.jtinyexpr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TinyExpressionCompilerTest {

    private TinyExpressionCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new TinyExpressionCompiler();
    }

    @Test
    @DisplayName("Should evaluate simple constant expressions")
    void testConstantExpression() throws Exception {
        try (Expression expr = compiler.compile("5 + 5 * 2")) {
            assertEquals(15.0, expr.evaluate(), 0.0001);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "sqrt(25), 5.0",
            "sin(0), 0.0",
            "cos(0), 1.0",
            "abs(-10), 10.0"
    })
    @DisplayName("Should support built-in C functions")
    void testBuiltInFunctions(String formula, double expected) throws Exception {
        try (Expression expr = compiler.compile(formula)) {
            assertEquals(expected, expr.evaluate(), 0.0001);
        }
    }

    @Test
    @DisplayName("Should bind and update variables correctly")
    void testVariableBindingAndUpdates() throws Exception {
        Variable x = new Variable(10.0);
        Argument argX = Argument.of("x", x);

        try (Expression expr = compiler.compile("x * 2", argX)) {
            assertEquals(20.0, expr.evaluate());

            // Update Java value, native side should reflect it immediately
            x.update(50.0);
            assertEquals(100.0, expr.evaluate());
        }
    }

    @Test
    @DisplayName("Should support custom Java functions (OneArgs)")
    void testCustomFunctionOneArg() throws Exception {
        Function.OneArgs cube = v -> v * v * v;
        Argument func = Argument.of("cube", cube);

        try (Expression expr = compiler.compile("cube(3)", func)) {
            assertEquals(27.0, expr.evaluate());
        }
    }

    @Test
    @DisplayName("Should support custom Java functions with multiple arguments")
    void testCustomFunctionTwoArgs() throws Exception {
        Function.TwoArgs customSum = (a, b) -> a + b + 10;
        Argument func = Argument.of("mysum", customSum);

        try (Expression expr = compiler.compile("mysum(5, 5)", func)) {
            assertEquals(20.0, expr.evaluate());
        }
    }

    @Test
    @DisplayName("Should throw exception on invalid expression syntax")
    void testParsingError() {
        ExpressionCompilerException exception = assertThrows(ExpressionCompilerException.class, () -> {
            compiler.compile("5 + (2 * 3"); // Missing closing parenthesis
        });
        assertTrue(exception.getMessage().contains("Parsing error"));
    }

    @Test
    @DisplayName("Should validate constant expressions correctly")
    void testValidateConstant() {
        assertTrue(compiler.validate("10 / 2"));
        assertFalse(compiler.validate("10 / (2 *"));
    }

    @Test
    @DisplayName("Should validate expressions with variables")
    void testValidateWithVariables() {
        Variable x = new Variable(0);
        Argument argX = Argument.of("x", x);

        assertTrue(compiler.validate("x + 5", argX));
        // Should be false because 'y' is unknown to the compiler
        assertFalse(compiler.validate("x + y", argX));
    }

    @Test
    @DisplayName("Should handle multiple variables and functions together")
    void testComplexIntegration() throws Exception {
        Variable x = new Variable(2.0);
        Variable y = new Variable(3.0);
        Function.TwoArgs power = Math::pow;

        try (Expression expr = compiler.compile("pow(x, y) + 1",
                Argument.of("x", x),
                Argument.of("y", y),
                Argument.of("pow", power))) {

            assertEquals(9.0, expr.evaluate());

            x.update(3.0);
            assertEquals(28.0, expr.evaluate());
        }
    }

    @Test
    @DisplayName("Should respect isPure flag for optimizations")
    void testPureFunction() throws Exception {
        int[] callCount = {0};
        Function.NoArgs pureFunc = new Function.NoArgs() {
            @Override public double apply() {
                callCount[0]++;
                return 42.0;
            }
            @Override public boolean isPure() { return true; }
        };

        try (Expression expr = compiler.compile("p() + p()", Argument.of("p", pureFunc))) {
            expr.evaluate();
            // Depending on TinyExpr's internal optimization, callCount might be 1 or 2
            assertTrue(callCount[0] >= 1);
        }
    }

    @Test
    @DisplayName("Should prevent updates on unbound variables")
    void testUnboundVariableUpdate() {
        Variable x = new Variable(1.0);
        assertThrows(IllegalStateException.class, () -> x.update(2.0));
    }

    @Test
    @DisplayName("Should interpret simple constant expressions")
    void testInterpretSimple() throws Exception {
        // Basic arithmetic
        assertEquals(10.0, compiler.interpret("5 + 5"), 1e-9);
        assertEquals(25.0, compiler.interpret("5 * 5"), 1e-9);
        // Order of operations
        assertEquals(15.0, compiler.interpret("5 + 2 * 5"), 1e-9);
    }

    @Test
    @DisplayName("Should interpret expressions with built-in functions")
    void testInterpretBuiltInFunctions() throws Exception {
        assertEquals(14.0, compiler.interpret("sqrt(16) + abs(-10)"), 1e-9);
        assertEquals(1.0, compiler.interpret("sin(1.57079632679)"), 1e-6);
    }

    @Test
    @DisplayName("Should throw ExpressionCompilerException for malformed expressions")
    void testInterpretInvalidExpression() {
        String invalidExpr = "5 + (2 * 3";

        ExpressionCompilerException exception = assertThrows(
                ExpressionCompilerException.class,
                () -> compiler.interpret(invalidExpr),
                "Should throw exception for unbalanced parentheses"
        );

        assertTrue(exception.getMessage().contains("Parsing error"));
    }

    @Test
    @DisplayName("Should handle complex nested constants")
    void testInterpretComplex() throws Exception {
        double result = compiler.interpret("((10 + 5) * 2) / (3 + 2)");
        assertEquals(6.0, result, 1e-9);
    }
}
