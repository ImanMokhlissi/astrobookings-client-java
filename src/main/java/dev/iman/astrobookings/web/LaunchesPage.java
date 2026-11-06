package dev.iman.astrobookings.web;

import dev.iman.astrobookings.model.ApiFailure;
import dev.iman.astrobookings.model.Launch;
import dev.iman.astrobookings.service.LaunchDateFormatter;
import dev.iman.astrobookings.service.LaunchService;
import java.util.List;
import java.util.stream.Collectors;

/** The launches open for booking. */
final class LaunchesPage {

    private static final String HEADING = "Launches";

    private final LaunchService launchService;
    private final PageLayout layout;

    LaunchesPage(LaunchService launchService, PageLayout layout) {
        this.launchService = launchService;
        this.layout = layout;
    }

    WebResponse show() {
        return layout.page(HEADING, content());
    }

    private String content() {
        try {
            return launches(launchService.listOpenLaunches());
        } catch (ApiFailure failure) {
            return FailureView.html(failure);
        }
    }

    private static String launches(List<Launch> launches) {
        if (launches.isEmpty()) {
            return HtmlFields.message("No launches open for booking.");
        }
        return launches.stream().map(LaunchesPage::launch).collect(Collectors.joining());
    }

    private static String launch(Launch launch) {
        return HtmlFields.list(
                HtmlFields.field("Name", launch.name()),
                HtmlFields.field("Price", launch.price().toPlainString()),
                HtmlFields.field("Date", LaunchDateFormatter.format(launch.date())),
                HtmlFields.field("Duration", String.valueOf(launch.duration())),
                HtmlFields.field("Location", launch.location()),
                HtmlFields.field("Minimum participants", String.valueOf(launch.minParticipants())),
                HtmlFields.field("Maximum participants", String.valueOf(launch.maxParticipants())),
                HtmlFields.field("Status", launch.status()));
    }
}
