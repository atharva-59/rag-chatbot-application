package rag_chatbot_application.service.impl;

//import com.example.ragchatbot.service.WebPageFetcher;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import rag_chatbot_application.service.WebPageFetcher;

import java.io.IOException;

@Component
public class JsoupWebPageFetcher implements WebPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(JsoupWebPageFetcher.class);

    private final int timeoutMs;
    private final String userAgent;
    private final int maxBodyBytes;

    public JsoupWebPageFetcher(
            @Value("${rag.url.timeout-ms:15000}") int timeoutMs,
            @Value("${rag.url.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36}") String userAgent,
            @Value("${rag.url.max-body-bytes:8388608}") int maxBodyBytes) {
        this.timeoutMs = timeoutMs;
        this.userAgent = userAgent;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public FetchedPage fetch(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .maxBodySize(maxBodyBytes)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)   // we inspect the status ourselves
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Upgrade-Insecure-Requests", "1")
                    .referrer("https://www.google.com/")
                    .execute();

            int status = response.statusCode();
            if (status >= 400) {
                log.warn("Fetch blocked/failed for {} -> HTTP {}", url, status);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, friendlyError(status, url));
            }

            Document doc = response.parse();
            doc.select("script, style, nav, footer, header, noscript, form, aside, iframe, svg").remove();

            String title = doc.title();
            String text = extractMainText(doc);

            if (text == null || text.strip().length() < 200) {
                // Very little text usually means JS-rendered content (e.g. Medium, SPAs)
                log.warn("Too little readable text from {} ({} chars) - likely JS-rendered",
                        url, text == null ? 0 : text.length());
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "This page has little readable text and may require JavaScript to load its content. " +
                                "Try a different URL (article/blog/docs pages work best).");
            }
            return new FetchedPage(title, text);

        } catch (ResponseStatusException e) {
            throw e; // already a clean, user-facing error
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL: " + url);
        } catch (IOException e) {
            log.warn("Fetch IOException for {} -> {}: {}", url, e.getClass().getSimpleName(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not fetch this URL (" + e.getClass().getSimpleName() + "). It may be down, slow, or blocking automated access.");
        }
    }

    /** Prefer the main article region; fall back to whole body. */
    private String extractMainText(Document doc) {
        for (String selector : new String[]{"article", "main", "[role=main]", ".post-content", ".article-content"}) {
            var el = doc.selectFirst(selector);
            if (el != null && el.text().strip().length() > 200) {
                return el.text();
            }
        }
        return doc.body() != null ? doc.body().text() : "";
    }

    private String friendlyError(int status, String url) {
        return switch (status) {
            case 401, 403 -> "This site blocks automated access (HTTP " + status + "). Try a different URL.";
            case 404 -> "Page not found (HTTP 404): " + url;
            case 429 -> "The site is rate-limiting requests (HTTP 429). Try again later.";
            default -> "The page returned HTTP " + status + ".";
        };
    }
}