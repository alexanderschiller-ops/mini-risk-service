package de.demo.tdl.lineage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Repeatable(TdlManual.List.class)
public @interface TdlManual {

    String source();

    String target();

    TdlType type();

    String commentary() default "";

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.SOURCE)
    @interface List {
        TdlManual[] value();
    }
}
