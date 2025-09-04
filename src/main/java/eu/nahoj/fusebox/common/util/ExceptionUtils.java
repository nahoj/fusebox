package eu.nahoj.fusebox.common.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ExceptionUtils {

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T t) throws IOException;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws IOException;
    }

    @FunctionalInterface
    public interface ThrowingUnaryOperator<T> extends ThrowingFunction<T, T> {
    }

    public static <T, R> Function<T, R> uncheckedIO(ThrowingFunction<T, R> function) {
        return t -> {
            try {
                return function.apply(t);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public static <T> Supplier<T> uncheckedIO(ThrowingSupplier<T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public static <T> UnaryOperator<T> uncheckedIO(ThrowingUnaryOperator<T> function) {
        return t -> {
            try {
                return function.apply(t);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
