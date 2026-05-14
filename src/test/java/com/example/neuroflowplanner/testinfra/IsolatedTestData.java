package com.example.neuroflowplanner.testinfra;

import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables isolated test data runtime for DB/notes tests.
 * Applies temporary data dir overrides and singleton reset lifecycle.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestDataIsolationExtension.class)
public @interface IsolatedTestData {
    TestDataIsolationScope scope() default TestDataIsolationScope.PER_CLASS;

    /**
     * If true, runs Flyway migrations for the isolated DB before tests.
     */
    boolean migrateSchema() default true;
}
