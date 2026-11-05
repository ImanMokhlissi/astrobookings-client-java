package dev.iman.astrobookings.model;

/** The API responded with a 401. What that means depends on the use case. */
public final class UnauthorizedFailure extends ApiFailure {

    private final transient ApiError apiError;

    public UnauthorizedFailure(ApiError apiError) {
        super(apiError.message());
        this.apiError = apiError;
    }

    public ApiError apiError() {
        return apiError;
    }
}
