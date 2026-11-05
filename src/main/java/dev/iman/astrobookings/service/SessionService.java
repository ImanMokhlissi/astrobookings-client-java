package dev.iman.astrobookings.service;

import dev.iman.astrobookings.api.ApiClient;
import dev.iman.astrobookings.model.InvalidCredentialsFailure;
import dev.iman.astrobookings.model.Session;
import dev.iman.astrobookings.model.SessionFileFailure;
import dev.iman.astrobookings.model.SessionRejectedFailure;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import dev.iman.astrobookings.session.SessionStore;
import java.util.Optional;
import java.util.function.Supplier;

/** The session lifecycle, and the meaning of a 401 in each use case. */
public final class SessionService {

    private final ApiClient apiClient;
    private final SessionStore sessionStore;
    private Session currentSession;

    public SessionService(ApiClient apiClient, SessionStore sessionStore) {
        this.apiClient = apiClient;
        this.sessionStore = sessionStore;
    }

    public Optional<Session> currentSession() {
        return Optional.ofNullable(currentSession);
    }

    /** Trusts the saved file without calling the API. A file failure starts no session. */
    public void loadSavedSession() {
        currentSession = sessionStore.read().orElse(null);
    }

    public Session register(String username, String email, String password) {
        return saveAndStart(apiClient.register(username, email, password));
    }

    public Session login(String email, String password) {
        try {
            return saveAndStart(apiClient.login(email, password));
        } catch (UnauthorizedFailure failure) {
            throw new InvalidCredentialsFailure(failure);
        }
    }

    /** Ends the session first, so that a failed delete still ends it. */
    public void logout() {
        currentSession = null;
        sessionStore.delete();
    }

    /** Runs a call that needs the session, and handles the API rejecting it. */
    public <T> T runAuthenticated(Supplier<T> authenticatedCall) {
        try {
            return authenticatedCall.get();
        } catch (UnauthorizedFailure failure) {
            throw rejectSession(failure);
        }
    }

    private Session saveAndStart(Session session) {
        sessionStore.write(session);
        currentSession = session;
        return session;
    }

    /** A failed delete is kept as suppressed, so that it does not hide the rejection. */
    private SessionRejectedFailure rejectSession(UnauthorizedFailure failure) {
        SessionRejectedFailure rejection = new SessionRejectedFailure(failure);
        currentSession = null;
        try {
            sessionStore.delete();
        } catch (SessionFileFailure deleteFailure) {
            rejection.addSuppressed(deleteFailure);
        }
        return rejection;
    }
}
