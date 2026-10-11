package org.example.backend.clerk;

import java.util.List;

/**
 * Asks Clerk about a user. A seam like SmsGateway: OwnerLinkService decides
 * who may be linked, this only fetches the facts, so tests can swap in a fake.
 */
public interface ClerkClient {

    /**
     * The user's email addresses that Clerk has verified, or an empty list if
     * there are none or Clerk can't be reached (a failed lookup must never
     * link anyone).
     */
    List<String> verifiedEmails(String clerkUserId);
}
