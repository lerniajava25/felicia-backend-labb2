package se.backend.labb2.helpdesk.user;

/**
 * The early check found the username already in use. The controller shows it as a form
 * error. It is only the friendly first check: the unique constraint in the database is the
 * real guard, and its DataIntegrityViolationException is shown the same way.
 */
public class UsernameTakenException extends RuntimeException {
}
