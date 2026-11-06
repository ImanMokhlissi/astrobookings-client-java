package dev.iman.astrobookings.web;

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
    private static final String NOTHING_TYPED = "";
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
        return registrationPage(NO_FAILURE, new RegistrationEntry(NOTHING_TYPED, NOTHING_TYPED, false));
    }

    WebResponse register(FormData form) {
        RegistrationEntry entry =
                new RegistrationEntry(form.value("username"), form.value("email"), !form.value("terms").isEmpty());
        if (!entry.termsAccepted()) {
            return registrationPage(TERMS_NOT_ACCEPTED, entry);
        }
        try {
            sessionService.register(entry.username().trim(), entry.email().trim(), form.value("password"));
            return redirectAfterSessionChange();
        } catch (ApiFailure failure) {
            return registrationPage(FailureView.html(failure), entry);
        } catch (SessionFileFailure failure) {
            return registrationPage(SESSION_NOT_SAVED, entry);
        }
    }

    WebResponse showLogin() {
        return loginPage(NO_FAILURE, NOTHING_TYPED);
    }

    WebResponse login(FormData form) {
        String email = form.value("email");
        try {
            sessionService.login(email.trim(), form.value("password"));
            return redirectAfterSessionChange();
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
            return redirectAfterSessionChange();
        } catch (SessionFileFailure failure) {
            return layout.page("Log out", HtmlFields.error("The session file could not be deleted."));
        }
    }

    private WebResponse redirectAfterSessionChange() {
        layout.forgetUnreadableSessionFile();
        return WebResponse.redirect(HOME_PATH);
    }

    private WebResponse registrationPage(String failure, RegistrationEntry entry) {
        return layout.page(REGISTER_HEADING, failure + HtmlFields.form(
                "/register",
                "Register",
                HtmlFields.textInput("username", "Username", entry.username()),
                HtmlFields.textInput("email", "Email", entry.email()),
                HtmlFields.passwordInput(),
                termsCheckbox(entry.termsAccepted())));
    }

    private static String termsCheckbox(boolean accepted) {
        String checked = accepted ? " checked" : "";
        return "<label><input type=\"checkbox\" name=\"terms\" value=\"accepted\"" + checked
                + "> I accept the terms</label>\n";
    }

    private WebResponse loginPage(String failure, String email) {
        return layout.page(LOG_IN_HEADING, LOGIN_BROKEN_STATEMENT + failure + HtmlFields.form(
                "/login",
                "Log in",
                HtmlFields.textInput("email", "Email", email),
                HtmlFields.passwordInput()));
    }

    /** What the user typed, as typed. It holds no password, so that no form can echo one. */
    private record RegistrationEntry(String username, String email, boolean termsAccepted) {
    }
}
