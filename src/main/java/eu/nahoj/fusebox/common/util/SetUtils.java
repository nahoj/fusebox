package eu.nahoj.fusebox.common.util;

import java.util.Collection;
import java.util.EnumSet;

public class SetUtils {

    // EnumSet.copyOf(Collection) cannot take an empty argument (type erasure)
    public static <E extends Enum<E>> EnumSet<E> enumSetCopy(Collection<E> collection, Class<E> enumClass) {
        return collection.isEmpty() ? EnumSet.noneOf(enumClass) : EnumSet.copyOf(collection);
    }
}
