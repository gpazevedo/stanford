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
                var title       = el.selectFirst(".course-title").text();
                var description = el.selectFirst(".description").text();
                var units       = el.selectFirst(".units").text()
                                    .replaceAll("\\s*units?\\s*", "").trim();
                var instructors = Arrays.stream(
                                    el.selectFirst(".instructors").text().split(",\\s*"))
                                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
                var quarter     = el.selectFirst(".quarters").text();
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
