package org.wikimedia.utils.http;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.protocol.HttpContext;

@ParametersAreNonnullByDefault
public class CustomRoutePlanner implements HttpRoutePlanner {

    private final Map<String, HttpHost> customRouteMap;
    private final HttpRoutePlanner defaultRoutePlanner;

    public CustomRoutePlanner(Map<String, HttpHost> customRouteMap, HttpRoutePlanner defaultRoutePlanner) {
        this.customRouteMap = customRouteMap;
        this.defaultRoutePlanner = defaultRoutePlanner;
    }

    /**
     * @param mapProxyProperty has a format of url=url
     * separated by commas with no space between them
     * for example: www.wikidata.org=http://proxy.local:9999,www.metawiki.org=https://proxy.local:8080
     */
    @Nonnull
    public static Map<String, HttpHost> createMapFromString(String mapProxyProperty) {
        String[] pairs = mapProxyProperty.split(",");
        return stream(pairs).map(p -> p.split("=")).
                collect(toMap(pair -> pair[0], pair -> HttpHost.create(pair[1])));
    }

    public void addRoute(String sourceURL, String targetURL) {
        customRouteMap.put(sourceURL, HttpHost.create(targetURL));
    }

    @Override @Nonnull
    public HttpRoute determineRoute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) throws HttpException {
        HttpHost destHost = customRouteMap.get(httpHost.getHostName());
        if (destHost != null) {
            return new HttpRoute(
                    new HttpHost(
                            destHost.getHostName(),
                            // If the dest port was not set, then assume we want use the same one as the request url.
                            destHost.getPort() != -1 ? destHost.getPort() : httpHost.getPort(),
                            destHost.getSchemeName()
                    )
            );
        } else {
            return defaultRoutePlanner.determineRoute(httpHost, httpRequest, httpContext);
        }
    }
}
