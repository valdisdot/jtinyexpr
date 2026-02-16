package com.valdisdot.util.jtinyexpr;

/*
the purpose of the library is to test and validate tinyexpr specific expressions outside the C code
the library is as tiny as tinyexpr, without redundant overhead
not all capabilities are utilized though: no usage of c-closure functions since Java method already closure based

compiler can:
        - validate constant (ones without variables) and non-constant expressions
        - interpret constant expressions like te_interp does
        - compile constant and non-constant expressions into Expression (encapsulation of te_compile + te_eval(te_expr)) with further call of evaluate()
        - support custom functions, starting with no-arg and up to 7-args, as tinyexpr does
        - make functions marked as pure to be compatible with TE_FLAG_PURE
 */
public class Example {
    public static void main(String[] args) {
        /*
        core of the library is an ExpressionCompiler
        the only implementation is the TinyExpressionCompiler which load tinyexpr library from the resourses/lib
         */
        ExpressionCompiler expressionCompiler = new TinyExpressionCompiler();

        //validate constant expression
        String expressionString = "1+e()-1^4";
        boolean bResult = expressionCompiler.validate(expressionString);
        System.out.printf("expression %s %s valid%n", expressionString, bResult ? "is" : "isn't");

        expressionString = "1+e()-1^";
        bResult = expressionCompiler.validate(expressionString);
        System.out.printf("expression %s %s valid%n", expressionString, bResult ? "is" : "isn't");

        //define a variable
        Variable variable = new Variable(1);
        //define a variable argument
        Argument variableArgument = Argument.of("x", variable);

        //define a custom function that accepts 3 arguments
        Function function = (Function.ThreeArgs) (value1, value2, value3) -> value1 * value2 * value3;
        //define a function argument
        Argument functionArgument = Argument.of("trimultipl", function);

        //validate non-constant expression
        expressionString = "trimultipl(10, 5.55, x) - 2 * x";
        bResult = expressionCompiler.validate(expressionString, variableArgument, functionArgument);
        System.out.printf("expression %s %s valid%n", expressionString, bResult ? "is" : "isn't");

        //interpret constant expressions with error
        expressionString = "1 + 2 + e() + ";
        try {
            expressionCompiler.interpret(expressionString);
        } catch (ExpressionCompilerException e) {
            System.out.println(e.getMessage());
        }

        //interpret constant expressions
        expressionString = "1 + 2 + e() + 1";
        double dResult;
        try {
            dResult = expressionCompiler.interpret(expressionString);
            System.out.println(expressionString + " = " + dResult);
        } catch (ExpressionCompilerException ignored) {}

        //compile constant expression into Expression
        try (Expression expression = expressionCompiler.compile(expressionString)) {
            dResult = expression.evaluate();
            System.out.println(expressionString + " = " + dResult);
        } catch (ExpressionCompilerException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //compile non-constant expression into Expression
        expressionString = "trimultipl(10, 5.55, x) - 2 * x";
        try (Expression expression = expressionCompiler.compile(expressionString, variableArgument, functionArgument)) {
            dResult = expression.evaluate();
            System.out.println(expressionString.replace(variableArgument.name(), variable.value() + "") + " = " + dResult);

            //increment variable
            variable.increment();
            dResult = expression.evaluate();
            System.out.println(expressionString.replace(variableArgument.name(), variable.value() + "") + " = " + dResult);
        } catch (ExpressionCompilerException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
