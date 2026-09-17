package de.demo.tdl.lineage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Requires analyzable mapper lineage or explicit manual lineage. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface LineageRelevant {
}
