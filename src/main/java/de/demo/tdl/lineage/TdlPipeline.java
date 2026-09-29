package de.demo.tdl.lineage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface TdlPipeline {

    String id();

    String name() default "";

    String orchestrator() default "data-services";

    String platform() default "java";

    String env() default "PROD";
}