package se.backend.labb2.helpdesk.common;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The error pages as a browser really gets them. MockMvc doesn't forward to /error (a 403
 * there is only a status with an empty body), so the 403 tests run against a real server:
 * real login form, real session cookie, real forward to the error page. The server runs in
 * its own threads, so this class is NOT @Transactional: it saves its own users and deletes
 * them afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ErrorPagesTest {

    private static final String TEST_PASSWORD = "made-up-test-password";
    private static final String NO_ACCESS = "You don't have access to this page";
    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final Pattern VERSION_FIELD = Pattern.compile("name=\"version\" value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User agent;
    private User boss;
    private User target;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        agent = userRepository.save(new User("err-agent", hash, "Error Agent", Role.AGENT));
        boss = userRepository.save(new User("err-boss", hash, "Error Boss", Role.ADMIN));
        target = userRepository.save(new User("err-target", hash, "Error Target", Role.USER));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll(userRepository.findAllById(List.of(agent.getId(), boss.getId(), target.getId())));
    }

    @Test
    void agentOnAdminPageGetsTheForbiddenPageWithNav() throws Exception {
        Browser browser = Browser.loggedInAs(port, "err-agent");

        HttpResponse<String> response = browser.get("/admin/users");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains(NO_ACCESS, "<small>Error 403</small>", "href=\"/\"", "Error Agent", "(agent)");
        assertNoErrorDetails(response.body());
        assertThat(response.body()).doesNotContain("/admin/users");
    }

    @Test
    void demotedAdminPostingTheEditFormGetsTheForbiddenPageAndNothingChanges() throws Exception {
        Browser browser = Browser.loggedInAs(port, "err-boss");
        // Opened while still an admin: the form's CSRF token and version, as a browser has them.
        String editPage = browser.get("/admin/users/" + target.getId() + "/edit").body();
        String csrf = find(CSRF_FIELD, editPage);
        String version = find(VERSION_FIELD, editPage);

        // Another admin demotes boss meanwhile.
        User demoted = userRepository.findById(boss.getId()).orElseThrow();
        demoted.changeRole(Role.AGENT);
        userRepository.save(demoted);

        HttpResponse<String> response = browser.post("/admin/users/" + target.getId(), Map.of(
                "role", "ADMIN",
                "_enabled", "on",
                "enabled", "true",
                "version", version,
                "_csrf", csrf));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains(NO_ACCESS, "<small>Error 403</small>", "(agent)");
        assertNoErrorDetails(response.body());

        User stored = userRepository.findById(target.getId()).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.USER);
        assertThat(stored.getEnabled()).isTrue();
        assertThat(stored.getVersion()).isEqualTo(target.getVersion());
    }

    @Test
    void genericErrorPageShowsNoDetails() throws Exception {
        // Like the 404 test: call Boot's error controller the way the server's forward would.
        String body = mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret-detail"))
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/tickets/secret-path")
                        .with(user(CurrentUser.from(agent))))
                .andReturn().getResponse().getContentAsString();

        // The status code is shown on purpose, small under the heading; nothing else is.
        assertThat(body).contains("Something went wrong", "<small>Error 500</small>", "href=\"/\"", "Error Agent");
        assertThat(body).doesNotContain("secret-detail", "secret-path");
        assertNoErrorDetails(body);
    }

    private static void assertNoErrorDetails(String body) {
        assertThat(body).doesNotContain("Whitelabel", "Exception", "Forbidden", "trace", "at se.backend");
    }

    private static String find(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).as("page has %s", pattern).isTrue();
        return matcher.group(1);
    }

    /** A tiny browser: keeps cookies, sends forms, never follows redirects on its own. */
    private record Browser(HttpClient client, String base) {

        static Browser loggedInAs(int port, String username) throws IOException, InterruptedException {
            Browser browser = new Browser(
                    HttpClient.newBuilder()
                            .cookieHandler(new CookieManager())
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build(),
                    "http://localhost:" + port);
            String csrf = find(CSRF_FIELD, browser.get("/login").body());
            HttpResponse<String> login = browser.post("/login", Map.of(
                    "username", username, "password", TEST_PASSWORD, "_csrf", csrf));
            // Tomcat sends the full URL (http://localhost:port/), so compare the path only.
            assertThat(login.headers().firstValue("Location").map(l -> URI.create(l).getPath()))
                    .as("logged in")
                    .hasValue("/");
            return browser;
        }

        HttpResponse<String> get(String path) throws IOException, InterruptedException {
            return client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Accept", "text/html")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, Map<String, String> fields) throws IOException, InterruptedException {
            String form = new LinkedHashMap<>(fields).entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
            return client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Accept", "text/html")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
