package com.valdisdot.util.jtinyexpr;

import java.util.function.Consumer;

public class Variable implements ArgumentValue {
    private final double initialValue;
    private double lastValue;
    private Consumer<Double> listener;

    public Variable(double value) {
        this.lastValue = value;
        this.initialValue = value;
    }

    public double value() {
        return lastValue;
    }

    public void update(double value) {
        if (listener == null) throw new IllegalStateException("Variable not bound to an expression");
        this.lastValue = value;
        this.listener.accept(this.lastValue);
    }

    public void increment() {
        if (listener == null) throw new IllegalStateException("Variable not bound to an expression");
        this.lastValue++;
        this.listener.accept(this.lastValue);
    }

    public void decrement() {
        if (listener == null) throw new IllegalStateException("Variable not bound to an expression");
        this.lastValue--;
        this.listener.accept(this.lastValue);
    }

    protected void onUpdate(Consumer<Double> listener) {
        if (this.listener == null) this.listener = listener;
        else throw new IllegalStateException("Variable has already bound to an expression");
    }

    protected void reset(double withValue) {
        this.lastValue = withValue;
        this.listener = null;
    }

    protected void reset() {
        this.reset(this.initialValue);
    }
}
