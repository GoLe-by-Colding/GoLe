package com.gole.api.common.operations;

import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 유스케이스 성공을 운영 이벤트로 기록한다. 인수는 기본적으로 수집하지 않는다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationalSignal {

    Category category();

    Level level() default Level.SUCCESS;

    String title();

    String description() default "";

    /** 공개해도 안전한 인수의 0-based index만 명시한다. */
    int[] includeArguments() default {};

    boolean includeResult() default false;
}
