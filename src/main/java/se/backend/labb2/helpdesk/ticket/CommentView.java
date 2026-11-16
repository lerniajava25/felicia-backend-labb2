package se.backend.labb2.helpdesk.ticket;

import se.backend.labb2.helpdesk.common.DateTimes;

/** One comment as the details page shows it. Built inside the service transaction. */
public record CommentView(String author, String body, String created) {

    public static CommentView from(Comment comment) {
        return new CommentView(
                comment.getAuthor().getDisplayName(),
                comment.getBody(),
                DateTimes.format(comment.getCreatedAt()));
    }
}
