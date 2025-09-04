package eu.nahoj.fusebox.common.util;

import org.springframework.lang.Nullable;

import java.util.function.Function;

public class NullUtils {

    @Nullable 
    public static <T, R> R mapOrNull(@Nullable T value, Function<T, R> mapper) {
        return value == null ? null : mapper.apply(value);
    }
}
