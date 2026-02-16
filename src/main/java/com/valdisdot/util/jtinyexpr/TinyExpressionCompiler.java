package com.valdisdot.util.jtinyexpr;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class TinyExpressionCompiler implements ExpressionCompiler {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;

    private static final MethodHandle te_interp;
    private static final MethodHandle te_compile;
    private static final MethodHandle te_eval;
    private static final MethodHandle te_free;

    private static final StructLayout TE_VARIABLE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("name"),
            ValueLayout.ADDRESS.withName("address"),
            ValueLayout.JAVA_INT.withName("type"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("context")
    );

    static {
        try {
            Path libPath = loadLibraryFromResources();
            LOOKUP = SymbolLookup.libraryLookup(libPath, Arena.global());

            String os = System.getProperty("os.name").toLowerCase();
            int LC_ALL = os.contains("win") ? 0 : 6; // Windows=0, Linux/macOS=6

            //fix for cases where "foo(5,5)" gets interpreted as foo(double var)
            MethodHandle setlocale = LINKER.downcallHandle(
                    LINKER.defaultLookup().find("setlocale").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
            );

            try (Arena temp = Arena.ofConfined()) {
                MemorySegment result = (MemorySegment) setlocale.invoke(LC_ALL, temp.allocateFrom("C"));
                if (result.equals(MemorySegment.NULL)) {
                    System.err.println("Warning: Could not set C locale.");
                }
            } catch (Throwable e) {
                throw new RuntimeException("Cannot set locale, floating numbers might be threat oddly", e);
            }

            te_interp = LINKER.downcallHandle(LOOKUP.find("te_interp").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            te_compile = LINKER.downcallHandle(LOOKUP.find("te_compile").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            te_eval = LINKER.downcallHandle(LOOKUP.find("te_eval").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
            te_free = LINKER.downcallHandle(LOOKUP.find("te_free").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TinyExpr native library", e);
        }
    }

    private static Path loadLibraryFromResources() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String extension = os.contains("win") ? ".dll" : (os.contains("mac") ? ".dylib" : ".so");
        String resourceName = "/lib/tinyexpr-" + arch + extension;

        try (InputStream is = TinyExpressionCompiler.class.getResourceAsStream(resourceName)) {
            if (is == null) throw new IOException("Native library not found: " + resourceName);
            Path tempLib = Files.createTempFile("tinyexpr-", extension);
            Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
            tempLib.toFile().deleteOnExit();
            return tempLib;
        }
    }

    @Override
    public boolean validate(String nonConstantExpression, Collection<Argument> args) {
        try (NativeExpression expression = new NativeExpression(nonConstantExpression, args)) {
            //without assignment will be always false
            double res = expression.evaluate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override public boolean validate(String constantExpression) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errorPtr = arena.allocate(ValueLayout.JAVA_INT);
            //without assignment will be always false
            double result = (double) te_interp.invokeExact(arena.allocateFrom(constantExpression), errorPtr);
            return errorPtr.get(ValueLayout.JAVA_INT, 0) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public boolean validate(String nonConstantExpression, Argument... args) {
        return validate(nonConstantExpression, Arrays.asList(args));
    }

    @Override
    public double interpret(String constantExpression) throws ExpressionCompilerException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errorPtr = arena.allocate(ValueLayout.JAVA_INT);
            double result = (double) te_interp.invokeExact(arena.allocateFrom(constantExpression), errorPtr);
            int errorIndex = errorPtr.get(ValueLayout.JAVA_INT, 0);
            if (errorIndex > 0) throw ExpressionCompilerException.parsingError(constantExpression, errorIndex);
            return result;
        } catch (Throwable t) {
            throw new ExpressionCompilerException(t);
        }
    }

    @Override
    public Expression compile(String nonConstantExpression, Collection<Argument> args) throws ExpressionCompilerException {
        return new NativeExpression(nonConstantExpression, args);
    }

    @Override
    public Expression compile(String nonConstantExpression, Argument... args) throws ExpressionCompilerException {
        return compile(nonConstantExpression, Arrays.asList(args));
    }

    @Override
    public Expression compile(String constantExpression) throws ExpressionCompilerException {
        return compile(constantExpression, List.of());
    }

    private static class NativeExpression implements Expression {
        private final Arena arena = Arena.ofConfined();
        private final MemorySegment tePtr;
        private final LinkedList<Variable> variables = new LinkedList<>();

        public NativeExpression(String expression, Collection<Argument> arguments) throws ExpressionCompilerException {
            List<Argument> args = arguments.stream()
                    .sorted((a, b) -> Integer.compare(b.name().length(), a.name().length()))
                    .toList();
            try {
                MemorySegment varArray = args.isEmpty() ? MemorySegment.NULL : arena.allocate(TE_VARIABLE_LAYOUT, args.size());
                int i = 0;
                for (Argument arg : args) {
                    MemorySegment struct = varArray.asSlice(i * TE_VARIABLE_LAYOUT.byteSize(), TE_VARIABLE_LAYOUT);
                    bindArgument(struct, arg);
                    i++;
                }
                MemorySegment errorOffset = arena.allocate(ValueLayout.JAVA_INT);
                this.tePtr = (MemorySegment) te_compile.invokeExact( arena.allocateFrom(expression), varArray, args.size(), errorOffset);
                if (tePtr.equals(MemorySegment.NULL)) throw ExpressionCompilerException.parsingError(expression, errorOffset.get(ValueLayout.JAVA_INT, 0));
            } catch (ExpressionCompilerException e) {
                arena.close();
                throw e;
            } catch (Throwable t) {
                arena.close();
                throw new ExpressionCompilerException(t);
            }
        }

        private void bindArgument(MemorySegment struct, Argument argument) throws Throwable {
            struct.set(ValueLayout.ADDRESS, 0, arena.allocateFrom(argument.name()));
            ArgumentValue argumentValue = argument.value();
            if (argumentValue instanceof Variable v) {
                variables.add(v);
                MemorySegment valPtr = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, v.value());
                v.onUpdate(val -> valPtr.set(ValueLayout.JAVA_DOUBLE, 0, val));

                struct.set(ValueLayout.ADDRESS, 8, valPtr);
                struct.set(ValueLayout.JAVA_INT, 16, 0);
            } else if (argumentValue instanceof Function f) {
                bindFunction(struct, f);
            } else throw new ExpressionCompilerException(String.format("Unknown argument value type: %s", argumentValue.getClass()));
        }

        private void bindFunction(MemorySegment struct, Function function) throws Throwable {
            MemorySegment stub;
            FunctionDescriptor desc;
            int arity;
            var lookup = MethodHandles.lookup();

            if (function instanceof Function.NoArgs fn) {
                arity = 0;
                desc = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE);
                MethodHandle mh = lookup.findVirtual(Function.NoArgs.class, "apply", MethodType.methodType(double.class)).bindTo(fn);
                stub = LINKER.upcallStub(mh, desc, arena);
            } else if (function instanceof Function.OneArgs fn) {
                arity = 1;
                desc = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
                MethodHandle mh = lookup.findVirtual(Function.OneArgs.class, "apply", MethodType.methodType(double.class, double.class)).bindTo(fn);
                stub = LINKER.upcallStub(mh, desc, arena);
            } else if (function instanceof Function.TwoArgs fn) {
                arity = 2;
                desc = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
                MethodHandle mh = lookup.findVirtual(Function.TwoArgs.class, "apply", MethodType.methodType(double.class, double.class, double.class)).bindTo(fn);
                stub = LINKER.upcallStub(mh, desc, arena);
            } else if (function instanceof Function.ThreeArgs fn) {
                arity = 3;
                desc = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
                MethodHandle mh = lookup.findVirtual(Function.ThreeArgs.class, "apply", MethodType.methodType(double.class, double.class, double.class, double.class)).bindTo(fn);
                stub = LINKER.upcallStub(mh, desc, arena);
            } else if (function instanceof Function.FourArgs fn) {
                arity = 4;
                desc = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
                MethodHandle mh = lookup.findVirtual(Function.FourArgs.class, "apply", MethodType.methodType(double.class, double.class, double.class, double.class, double.class)).bindTo(fn);
                stub = LINKER.upcallStub(mh, desc, arena);
            } else if (function instanceof Function.FiveArgs fn) {
                arity = 5;
                desc = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
                MethodHandle mh = lookup.findVirtual(Function.FiveArgs.class, "apply", MethodType.methodType(double.class, double.class, double.class, double.class, double.class, double.class)).bindTo(fn);
                stub = LINKER.upcallStub(mh, desc, arena);
            } else if (function instanceof Function.SixArgs fn) {
                arity = 6;
                desc = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
                MethodHandle mh = lookup.findVirtual(Function.SixArgs.class, "apply", MethodType.methodType(double.class, double.class, double.class, double.class, double.class, double.class, double.class)).bindTo(fn);
                stub = LINKER.upcallStub(mh, desc, arena);
            } else if (function instanceof Function.SevenArgs fn) {
                arity = 7;
                desc = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
                MethodHandle mh = lookup.findVirtual(Function.SevenArgs.class, "apply", MethodType.methodType(double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class)).bindTo(fn);
                stub = LINKER.upcallStub(mh, desc, arena);
            } else {
                throw new ExpressionCompilerException(String.format("Unknown function type: %s", function.getClass()));
            }

            int type = (8 + arity) | (function.isPure() ? 32 : 0);
            struct.set(ValueLayout.ADDRESS, 8, stub);
            struct.set(ValueLayout.JAVA_INT, 16, type);
        }

        @Override
        public double evaluate() {
            try {
                return (double) te_eval.invokeExact(tePtr);
            } catch (Throwable t) {
                throw new RuntimeException("Evaluation failed", t);
            }
        }

        @Override
        public void close() {
            try {
                te_free.invokeExact(tePtr);
                while (!variables.isEmpty()) variables.pop().reset();
            } catch (Throwable ignored) {
            } finally {
                arena.close();
            }
        }
    }
}