package com.valdisdot.util.jtinyexpr;

public interface Function extends ArgumentValue {
    default boolean isPure() {
        return false;
    }

    interface NoArgs extends Function {
        double apply();
    }

    interface OneArgs extends Function {
        double apply(double value);
    }

    interface TwoArgs extends Function {
        double apply(double value1, double value2);
    }

    interface ThreeArgs extends Function {
        double apply(double value1, double value2, double value3);
    }

    interface FourArgs extends Function {
        double apply(double value1, double value2, double value3, double value4);
    }

    interface FiveArgs extends Function {
        double apply(double value1, double value2, double value3, double value4, double value5);
    }

    interface SixArgs extends Function {
        double apply(double value1, double value2, double value3, double value4, double value5, double value6);
    }

    interface SevenArgs extends Function {
        double apply(double value1, double value2, double value3, double value4, double value5, double value6, double value7);
    }
}
