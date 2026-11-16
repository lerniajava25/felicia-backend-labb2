package se.backend.labb2.helpdesk.common;

/**
 * A business rule refuses the action, e.g. "a closed ticket cannot be assigned".
 * Controllers show the message to the user as a flash message, so write it for them:
 * plain words, no ids, no internal details.
 *
 * Not for other cases: missing or not-yours is NotFoundException (404), and an outdated
 * version is Spring's OptimisticLockingFailureException.
 */
public class RuleViolationException extends RuntimeException {

    public RuleViolationException(String message) {
        super(message);
    }
}
