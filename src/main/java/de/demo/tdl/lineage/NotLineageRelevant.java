package de.demo.tdl.lineage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Explicitly excludes a service from lineage; a nonblank reason is required. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface NotLineageRelevant {
    String reason();
}
