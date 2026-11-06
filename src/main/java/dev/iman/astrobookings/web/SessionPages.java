package dev.iman.astrobookings.web;

import static dev.iman.astrobookings.web.HtmlEscaper.escape;

import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.InvalidCredentialsFailure;
import dev.iman.astrobookings.model.SessionFileFailure;
import dev.iman.astrobookings.service.SessionService;

/** Registration, login and logout. A form shown again keeps the typed values, never the password. */
final class SessionPages {

    private static final String REGISTER_HEADING = "Register";
    private static final String LOG_IN_HEADING = "Log in";
    private static final String HOME_PATH = "/";
    private static final String NO_FAILURE = "";
    private static final String SESSION_NOT_SAVED = HtmlFields.error("The session could not be saved.");
    private static final String TERMS_NOT_ACCEPTED =
            HtmlFields.error("The terms were not accepted, so you were not registered.");
    private static final String LOGIN_REJECTED =
            HtmlFields.error("The API did not accept the email or password, and its login is broken today.")
                    + HtmlFields.message("<a href=\"/register\">Register instead</a>");
    private static final String LOGIN_BROKEN_STATEMENT =
            HtmlFields.message("The login of the API is currently broken and rejects every attempt.");

    private final SessionService sessionService;
    private final PageLayout layout;

    SessionPages(SessionService sessionService, PageLayout layout) {
        this.sessionService = sessionService;
        this.layout = layout;
    }

    WebResponse showRegistration() {
        return registrationPage(NO_FAILURE, new RegistrationEntry("", "", false));
    }

    WebResponse register(FormData form) {
        RegistrationEntry entry =
                new RegistrationEntry(form.value("username"), form.value("email"), !form.value("terms").isEmpty());
        if (!entry.termsAccepted()) {
            return registrationPage(TERMS_NOT_ACCEPTED, entry);
        }
        try {
            sessionService.register(entry.username().trim(), entry.email().trim(), form.value("password"));
            return redirectAfterStartingSession();
        } catch (ApiFailure failure) {
            return registrationPage(FailureView.html(failure), entry);
        } catch (SessionFileFailure failure) {
            return registrationPage(SESSION_NOT_SAVED, entry);
        }
    }

    WebResponse showLogin() {
        return loginPage(NO_FAILURE, "");
    }

    WebResponse login(FormData form) {
        String email = form.value("email");
        try {
            sessionService.login(email.trim(), form.value("password"));
            return redirectAfterStartingSession();
        } catch (InvalidCredentialsFailure failure) {
            return loginPage(LOGIN_REJECTED, email);
        } catch (ApiFailure failure) {
            return loginPage(FailureView.html(failure), email);
        } catch (SessionFileFailure failure) {
            return loginPage(SESSION_NOT_SAVED, email);
        }
    }

    WebResponse logout() {
        try {
            sessionService.logout();
            layout.forgetUnreadableSessionFile();
            return WebResponse.redirect(HOME_PATH);
        } catch (SessionFileFailure failure) {
            return layout.page("Log out", HtmlFields.error("The session file could not be deleted."));
        }
    }

    private WebResponse redirectAfterStartingSession() {
        layout.forgetUnreadableSessionFile();
        return WebResponse.redirect(HOME_PATH);
    }

    private WebResponse registrationPage(String failure, RegistrationEntry entry) {
        return layout.page(REGISTER_HEADING, failure + """
                <form method="post" action="/register">
                <label for="username">Username</label>
                <input type="text" id="username" name="username" value="%s">
                <label for="email">Email</label>
                <input type="text" id="email" name="email" value="%s">
                <label for="password">Password</label>
                <input type="password" id="password" name="password">
                <label><input type="checkbox" name="terms" value="accepted"%s> I accept the terms</label>
                <button type="submit">Register</button>
                </form>
                """.formatted(
                        escape(entry.username()), escape(entry.email()), entry.termsAccepted() ? " checked" : ""));
    }

    private WebResponse loginPage(String failure, String email) {
        return layout.page(LOG_IN_HEADING, LOGIN_BROKEN_STATEMENT + failure + """
                <form method="post" action="/login">
                <label for="email">Email</label>
                <input type="text" id="email" name="email" value="%s">
                <label for="password">Password</label>
                <input type="password" id="password" name="password">
                <button type="submit">Log in</button>
                </form>
                """.formatted(escape(email)));
    }

    /** What the user typed, as typed. It holds no password, so that no form can echo one. */
    private record RegistrationEntry(String username, String email, boolean termsAccepted) {
    }
}
