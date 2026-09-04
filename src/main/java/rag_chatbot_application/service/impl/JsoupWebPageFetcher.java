package rag_chatbot_application.service.impl;

//import com.example.ragchatbot.exception.WebPageFetchException;
//import com.example.ragchatbot.service.WebPageFetcher;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import rag_chatbot_application.exception.WebPageFetchException;
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
                    .ignoreHttpErrors(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Upgrade-Insecure-Requests", "1")
                    .referrer("https://www.google.com/")
                    .execute();

            int status = response.statusCode();
            if (status >= 400) {
                log.warn("Fetch blocked/failed for {} -> HTTP {}", url, status);
                throw new WebPageFetchException(mapStatus(status), friendlyError(status, url));
            }

            Document doc = response.parse();
            doc.select("script, style, nav, footer, header, noscript, form, aside, iframe, svg").remove();

            String title = doc.title();
            String text = extractMainText(doc);

            if (text == null || text.strip().length() < 200) {
                log.warn("Too little readable text from {} ({} chars)", url, text == null ? 0 : text.length());
                throw new WebPageFetchException(422,
                        "This page has little readable text and may require JavaScript to load its content. " +
                                "Try a different URL (article/blog/docs pages work best).");
            }
            return new FetchedPage(title, text);

        } catch (WebPageFetchException e) {
            throw e; // already a clean domain exception
        } catch (IllegalArgumentException e) {
            throw new WebPageFetchException(400, "Invalid URL: " + url);
        } catch (IOException e) {
            log.warn("Fetch IOException for {} -> {}: {}", url, e.getClass().getSimpleName(), e.getMessage());
            throw new WebPageFetchException(422,
                    "Could not fetch this URL (" + e.getClass().getSimpleName() +
                            "). It may be down, slow, or blocking automated access.");
        }
    }

    private String extractMainText(Document doc) {
        for (String selector : new String[]{"article", "main", "[role=main]", ".post-content", ".article-content"}) {
            var el = doc.selectFirst(selector);
            if (el != null && el.text().strip().length() > 200) {
                return el.text();
            }
        }
        return doc.body() != null ? doc.body().text() : "";
    }

    private int mapStatus(int status) {
        // 401/403/404/429 pass through; everything else -> 422 (we couldn't process it)
        return switch (status) {
            case 401, 403, 404, 429 -> status;
            default -> 422;
        };
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
