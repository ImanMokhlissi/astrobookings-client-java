package dev.iman.astrobookings.model;

/** The API responded with an error other than a 401, or with a body the client cannot read. */
public final class ApiErrorFailure extends ApiFailure {

    private final transient ApiError apiError;

    public ApiErrorFailure(ApiError apiError) {
        super(apiError.message());
        this.apiError = apiError;
    }

    public ApiError apiError() {
        return apiError;
    }
}
