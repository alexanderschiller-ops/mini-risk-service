package de.demo.tdl.lineage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Repeatable(TdlDataObject.List.class)
public @interface TdlDataObject {

    String name();

    Direction direction();

    String platform() default "";

    String env() default "";

    String alias() default "";

    enum Direction {
        INPUT,
        OUTPUT
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.SOURCE)
    @interface List {
        TdlDataObject[] value();
    }
}