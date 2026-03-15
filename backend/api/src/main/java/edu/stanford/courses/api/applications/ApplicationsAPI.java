package edu.stanford.courses.api.applications;

import edu.stanford.courses.api.applications.domain.ApplicationService;
import org.springframework.stereotype.Component;

@Component
public class ApplicationsAPI {
    private final ApplicationService applicationService;
    public ApplicationsAPI(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }
    // delegate application methods
}
