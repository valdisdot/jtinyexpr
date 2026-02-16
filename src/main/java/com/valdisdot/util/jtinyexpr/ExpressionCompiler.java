package com.valdisdot.util.jtinyexpr;

import java.util.Collection;

public interface ExpressionCompiler {
    boolean validate(String constantExpression);
    boolean validate(String nonConstantExpression, Argument... args);
    boolean validate(String nonConstantExpression, Collection<Argument> args);
    double interpret(String constantExpression) throws ExpressionCompilerException;
    Expression compile(String constantExpression) throws ExpressionCompilerException;
    Expression compile(String nonConstantExpression, Argument... args) throws ExpressionCompilerException;
    Expression compile(String nonConstantExpression, Collection<Argument> args) throws ExpressionCompilerException;
}
