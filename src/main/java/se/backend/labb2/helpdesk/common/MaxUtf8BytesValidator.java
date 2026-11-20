package se.backend.labb2.helpdesk.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/** Checks @MaxUtf8Bytes: counts the bytes the text takes in UTF-8. */
public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    // Set once per annotated field by the validation framework, then only read.
    private int maxBytes;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
        maxBytes = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
