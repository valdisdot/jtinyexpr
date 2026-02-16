package com.valdisdot.util.jtinyexpr;

import java.util.Objects;

//{"x": &x}
public class Argument {
    private final String name;
    private final ArgumentValue value;

    public Argument(String name, ArgumentValue value) {
        this.name = Objects.requireNonNull(name, "Argument name is null").trim();
        if (this.name.isEmpty()) throw new IllegalArgumentException("Argument name is empty");
        this.value = Objects.requireNonNull(value, "Argument value is null");
    }

    public String name() {
        return name;
    }

    public ArgumentValue value() {
        return value;
    }

    public static Argument of(String name, ArgumentValue value) {
        return new Argument(name, value);
    }
}
