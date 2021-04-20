package org.wikimedia.utils.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomRoutePlannerUnitTest {

    Map<String, HttpHost> customRouteMapTest = new HashMap<>();
    HttpRoutePlanner defaultRoutePlanner = new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE);

    @BeforeEach
    public void setUp() {
        customRouteMapTest.put("managed.test", new HttpHost("route.test", 9999, "someScheme"));
        customRouteMapTest.put("proxy.test", new HttpHost("route.test", 8080, "diffScheme"));
    }

    @Test
    void shouldCreateMapFromString() {
        String mapString = "www.wikidata.org=http://proxy.local:9999,www.metawiki.org=https://proxy.local:8080";
        Map<String, HttpHost> stringMap = new HashMap<>();
        stringMap.put("www.wikidata.org", new HttpHost("proxy.local", 9999, "http"));
        stringMap.put("www.metawiki.org", new HttpHost("proxy.local", 8080, "https"));
        assertThat(CustomRoutePlanner.createMapFromString(mapString)).isEqualTo(stringMap);
    }


    @Test
    void shouldNotGetSameHost() throws HttpException {
        Map<String, HttpHost> emptyMap = new HashMap<>();
        CustomRoutePlanner customRoutePlanner = new CustomRoutePlanner(emptyMap, defaultRoutePlanner);
        HttpHost testHost = new HttpHost("wikidata.org", 9999, "someScheme");
        HttpRoute actualRoute = customRoutePlanner.determineRoute(testHost, new HttpGet(), new BasicHttpContext());
        assertThat(actualRoute.getTargetHost()).isEqualTo(testHost);
    }

    @Test
    void shouldGetCustomRoute() throws HttpException {
        CustomRoutePlanner customRoutePlanner = new CustomRoutePlanner(customRouteMapTest, defaultRoutePlanner);
        HttpHost testHost = new HttpHost("managed.test", 9999, "someScheme");
        HttpHost expectedHost = new HttpHost("route.test", 9999, "someScheme");
        HttpRoute actualRoute = customRoutePlanner.determineRoute(testHost, new HttpGet(), new BasicHttpContext());
        assertThat(actualRoute.getTargetHost()).isEqualTo(expectedHost);
    }

    @Test
    void shouldUseProxyRoute() throws HttpException {
        CustomRoutePlanner customRoutePlanner = new CustomRoutePlanner(customRouteMapTest, defaultRoutePlanner);
        HttpHost testHost = new HttpHost("proxy.test", 9999, "someScheme");
        HttpHost expectedHost = new HttpHost("route.test", 8080, "diffScheme");
        HttpRoute actualRoute = customRoutePlanner.determineRoute(testHost, new HttpGet(), new BasicHttpContext());
        assertThat(actualRoute.getTargetHost()).isEqualTo(expectedHost);
    }
}
