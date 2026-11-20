package se.backend.labb2.helpdesk.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The text may be at most this many bytes in UTF-8, which is not the same as characters:
 * å, ä and ö take 2 bytes, many other characters 3 or 4. @Size counts characters, so it
 * can't guard a byte limit such as BCrypt's 72. Null counts as valid (@NotBlank handles it).
 */
@Documented
@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Too long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
