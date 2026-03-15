package edu.stanford.courses.ingestion;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class BulletinScraperTest {

    @Test
    void parsesAllCoursesFromHtml() throws Exception {
        var html = Files.readString(
            Path.of(getClass().getResource("/bulletin-sample.html").toURI()));
        var courses = new BulletinScraper().parseHtml(html);

        assertThat(courses).hasSize(2);
    }

    @Test
    void parsesCs229Fields() throws Exception {
        var html = Files.readString(
            Path.of(getClass().getResource("/bulletin-sample.html").toURI()));
        var cs229 = new BulletinScraper().parseHtml(html).stream()
            .filter(c -> c.courseId().equals("CS229")).findFirst().orElseThrow();

        assertThat(cs229.title()).isEqualTo("CS 229: Machine Learning");
        assertThat(cs229.units()).isEqualTo("3-4");
        assertThat(cs229.instructors()).containsExactly("Andrew Ng");
        assertThat(cs229.prerequisites()).containsExactlyInAnyOrder("CS106B", "CS109");
    }

    @Test
    void parsesMultipleInstructors() throws Exception {
        var html = Files.readString(
            Path.of(getClass().getResource("/bulletin-sample.html").toURI()));
        var cs231n = new BulletinScraper().parseHtml(html).stream()
            .filter(c -> c.courseId().equals("CS231N")).findFirst().orElseThrow();

        assertThat(cs231n.instructors()).containsExactlyInAnyOrder("Fei-Fei Li", "Andrej Karpathy");
    }

    @Test
    void preservesRawPrereqNoteText() throws Exception {
        var html = Files.readString(
            Path.of(getClass().getResource("/bulletin-sample.html").toURI()));
        var cs229 = new BulletinScraper().parseHtml(html).stream()
            .filter(c -> c.courseId().equals("CS229")).findFirst().orElseThrow();

        // prereqNote contains the raw text from the .prerequisites element
        assertThat(cs229.prereqNote()).isNotEmpty();
        // structured prerequisites list is derived from prereqNote
        assertThat(cs229.prerequisites()).containsExactlyInAnyOrder("CS106B", "CS109");
        // prereqNote and prerequisites are distinct fields — both populated
        assertThat(cs229.prereqNote()).contains("CS106B");
    }
}
