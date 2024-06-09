package coursemaker.coursemaker.aop;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE})// 필드와 매개변수에 적을 수 있음
@Retention(RetentionPolicy.RUNTIME)// 어플리케이션 실행 시점에 정보를 가져옴
@Documented// javaDoc에 내용이 표시됨
public @interface LoginedUser {
    String loginedUser() default "user";
}
