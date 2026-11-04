package kz.mbmmotors.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import kz.mbmmotors.config.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SiteFilesController.class)
@Import(WebSecurityConfiguration.class)
class SiteFilesControllerTest {
  @Autowired MockMvcTester mvc;

  @Test
  void robotsAndFaviconArePublicAndNothingIsIndexed() throws Exception {
    var robots = mvc.get().uri("/robots.txt").exchange();
    assertThat(robots).hasStatusOk();
    assertThat(robots.getResponse().getContentAsString()).isEqualTo("User-agent: *\nDisallow: /\n");
    var favicon = mvc.get().uri("/favicon.ico").exchange();
    assertThat(favicon).hasStatus(HttpStatus.MOVED_PERMANENTLY);
    assertThat(favicon.getResponse().getHeader("Location")).isEqualTo("/frontend/favicon.svg");
  }
}
