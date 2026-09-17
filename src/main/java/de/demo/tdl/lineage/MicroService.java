package de.demo.tdl.lineage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a service subject to the lineage architecture contract. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MicroService {
}
