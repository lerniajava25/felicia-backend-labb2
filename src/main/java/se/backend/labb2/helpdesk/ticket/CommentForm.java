package se.backend.labb2.helpdesk.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the comment form may send: only the text. The ticket comes from the URL and
 * the author from the login. Max length matches the column in Comment.
 */
public class CommentForm {

    @NotBlank(message = "Comment cannot be empty")
    @Size(max = 2000, message = "Comment can be at most 2000 characters")
    private String body;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
