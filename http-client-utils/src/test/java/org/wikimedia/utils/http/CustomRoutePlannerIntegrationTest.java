package org.wikimedia.utils.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.tomakehurst.wiremock.WireMockServer;

import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;
import ru.lanwen.wiremock.ext.WiremockUriResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver.WiremockUri;

@ExtendWith({
    WiremockResolver.class,
    WiremockUriResolver.class
})
 class CustomRoutePlannerIntegrationTest {

    @Test
    void test(@Wiremock WireMockServer server, @WiremockUri String baseUri) throws IOException {
        server.stubFor(get(urlEqualTo("/my/resource"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody("<response>Some content</response>")));

        CloseableHttpClient client = HttpClientBuilder.create().build();
        try (CloseableHttpResponse resp = client.execute(new HttpGet(baseUri + "/my/resource"))) {
            assertThat(resp.getFirstHeader("content-type").getValue()).isEqualTo("text/xml");
            assertThat(EntityUtils.toString(resp.getEntity())).isEqualTo("<response>Some content</response>");
        }
    }
}
