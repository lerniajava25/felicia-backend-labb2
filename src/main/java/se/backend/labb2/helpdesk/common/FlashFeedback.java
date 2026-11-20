package se.backend.labb2.helpdesk.common;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Runs a change from a form and turns the two expected refusals into a flash "error" for
 * the page after the redirect. Each controller keeps its own redirect target and messages.
 * A static helper, not a bean: it has no state and needs nothing but what it is given.
 */
public final class FlashFeedback {

    private FlashFeedback() {
    }

    /**
     * @param successMessage flashed as "message" when the change goes through, or null for none
     * @param conflictMessage flashed as "error" when someone else changed the data meanwhile
     * @return true if the change went through; false if it was refused (the flash says why)
     */
    public static boolean attempt(Runnable change, RedirectAttributes flash,
                                  String successMessage, String conflictMessage) {
        try {
            change.run();
            if (successMessage != null) {
                flash.addFlashAttribute("message", successMessage);
            }
            return true;
        } catch (OptimisticLockingFailureException e) {
            // Covers both the service's version check and Hibernate's check at commit.
            flash.addFlashAttribute("error", conflictMessage);
        } catch (RuleViolationException e) {
            // A business rule said no; its message is written for the user.
            flash.addFlashAttribute("error", e.getMessage());
        }
        return false;
    }
}
