package edu.stanford.courses.ingestion;

import edu.stanford.courses.ingestion.model.ScrapedCourse;
import org.jsoup.Jsoup;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class BulletinScraper {

    private static final String BULLETIN_URL = "https://bulletin.stanford.edu/programs/CS-PMN";
    private static final Pattern COURSE_ID_PATTERN = Pattern.compile("\\b(CS\\w+)\\b");

    /** Live scrape from bulletin URL. */
    public List<ScrapedCourse> scrape() throws IOException {
        return parseHtml(Jsoup.connect(BULLETIN_URL).get().html());
    }

    /** Parse HTML string — testable without network. */
    public List<ScrapedCourse> parseHtml(String html) {
        return Jsoup.parse(html).select(".courseleaf-block").stream()
            .map(el -> {
                var courseId    = el.attr("data-courseid");
                var titleEl     = el.selectFirst(".course-title");
                var title       = titleEl != null ? titleEl.text() : "";
                var descEl      = el.selectFirst(".description");
                var description = descEl != null ? descEl.text() : "";
                var unitsEl     = el.selectFirst(".units");
                var units       = unitsEl != null
                                    ? unitsEl.text().replaceAll("\\s*units?\\s*", "").trim()
                                    : "";
                var instEl      = el.selectFirst(".instructors");
                var instructors = instEl != null
                                    ? Arrays.stream(instEl.text().split(",\\s*"))
                                        .map(String::trim).filter(s -> !s.isEmpty()).toList()
                                    : List.<String>of();
                var quarterEl   = el.selectFirst(".quarters");
                var quarter     = quarterEl != null ? quarterEl.text() : "";
                var prereqEl    = el.selectFirst(".prerequisites");
                var prereqNote  = prereqEl != null ? prereqEl.text() : "";
                var prerequisites = COURSE_ID_PATTERN.matcher(prereqNote).results()
                                    .map(m -> m.group(1)).toList();
                var url = BULLETIN_URL + "#" + courseId.toLowerCase();
                return new ScrapedCourse(courseId, title, description, units,
                    instructors, quarter, url, prerequisites, prereqNote);
            }).toList();
    }
}
