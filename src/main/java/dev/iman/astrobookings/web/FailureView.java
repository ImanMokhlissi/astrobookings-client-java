package dev.iman.astrobookings.web;

import dev.iman.astrobookings.model.ApiError;
import dev.iman.astrobookings.model.ApiErrorFailure;
import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.ApiNotReachableFailure;
import dev.iman.astrobookings.model.FieldError;
import dev.iman.astrobookings.model.InvalidCredentialsFailure;
import dev.iman.astrobookings.model.LaunchNotBookableFailure;
import dev.iman.astrobookings.model.NoSessionFailure;
import dev.iman.astrobookings.model.SessionRejectedFailure;
import dev.iman.astrobookings.model.UnauthorizedFailure;
import java.util.stream.Collectors;

/** Presents every failure as escaped HTML. API messages stay verbatim apart from the escaping. */
final class FailureView {

    private static final String REGISTER_LINK = "<a href=\"/register\">Register</a>";
    private static final String LOG_IN_LINK = "<a href=\"/login\">Log in</a>";

    private FailureView() {
    }

    static String html(ApiFailure failure) {
        return switch (failure) {
            case ApiNotReachableFailure notReachable ->
                    HtmlFields.error(HtmlEscaper.escape("API not reachable at " + notReachable.url()));
            case UnauthorizedFailure unauthorized -> apiError(unauthorized.apiError());
            case ApiErrorFailure apiErrorFailure -> apiError(apiErrorFailure.apiError());
            case InvalidCredentialsFailure invalidCredentials ->
                    HtmlFields.error("The email or password was not accepted.");
            case SessionRejectedFailure sessionRejected ->
                    HtmlFields.error("Your session is no longer valid. Please register again.")
                            + HtmlFields.message(REGISTER_LINK);
            case NoSessionFailure noSession ->
                    HtmlFields.error("Register or log in first.")
                            + HtmlFields.message(REGISTER_LINK + " " + LOG_IN_LINK);
            case LaunchNotBookableFailure notBookable ->
                    HtmlFields.error("This launch is not open for booking.");
        };
    }

    private static String apiError(ApiError apiError) {
        String fieldErrors = apiError.fieldErrors().stream()
                .map(FailureView::fieldError)
                .collect(Collectors.joining());
        return HtmlFields.error(HtmlEscaper.escape(apiError.message())) + fieldErrors;
    }

    private static String fieldError(FieldError fieldError) {
        return HtmlFields.error(HtmlEscaper.escape(fieldError.field() + ": " + fieldError.message()));
    }
}
