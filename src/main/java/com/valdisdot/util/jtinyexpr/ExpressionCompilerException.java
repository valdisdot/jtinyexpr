package com.valdisdot.util.jtinyexpr;

public class ExpressionCompilerException extends Exception{
    public ExpressionCompilerException(String message) {
        super(message);
    }

    public ExpressionCompilerException(Throwable cause) {
        super(cause);
    }

    protected static ExpressionCompilerException parsingError(String expression, int errorCharIndex){
        return new ExpressionCompilerException(String.format("Parsing error at character %d in expression '%s'", errorCharIndex, expression));
    }
}
