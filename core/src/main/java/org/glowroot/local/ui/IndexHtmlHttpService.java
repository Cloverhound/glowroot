/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.local.ui;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nullable;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.Resources;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

class IndexHtmlHttpService implements HttpService {

    private static final String baseHref;

    private static final @Nullable String googleAnalyticsTrackingId =
            System.getProperty("glowroot.internal.googleAnalyticsTrackingId");

    static {
        String uiBase = System.getProperty("glowroot.ui.base");
        if (Strings.isNullOrEmpty(uiBase)) {
            baseHref = "/";
        } else {
            baseHref = uiBase;
        }
    }

    private final HttpSessionManager httpSessionManager;
    private final LayoutJsonService layoutJsonService;

    IndexHtmlHttpService(HttpSessionManager httpSessionManager,
            LayoutJsonService layoutJsonService) {
        this.httpSessionManager = httpSessionManager;
        this.layoutJsonService = layoutJsonService;
    }

    @Override
    public HttpResponse handleRequest(HttpRequest request, Channel channel) throws IOException {
        URL url = Resources.getResource("org/glowroot/local/ui/app-dist/index.html");
        String indexHtml = Resources.toString(url, Charsets.UTF_8);
        String layout;
        if (httpSessionManager.needsAuthentication(request)) {
            layout = layoutJsonService.getUnauthenticatedLayout();
        } else {
            layout = layoutJsonService.getLayout();
        }
        String layoutScript = "var layout=" + layout + ";";
        indexHtml = indexHtml.replaceFirst("<base href=\"/\">",
                "<base href=\"" + baseHref + "\"><script>" + layoutScript + "</script>");
        // this is to work around an issue with IE10-11 (IE9 is OK)
        // (even without reverse proxy/non-root base href)
        // IE doesn't use the base href when loading the favicon
        indexHtml = indexHtml.replaceFirst(
                "<link rel=\"shortcut icon\" href=\"favicon\\.([0-9a-f]+)\\.ico\">",
                "<script>document.write('<link rel=\"shortcut icon\" href=\"'"
                        + " + document.getElementsByTagName(\"base\")[0].href"
                        + " + 'favicon.$1.ico\">');</script>");
        if (googleAnalyticsTrackingId != null) {
            indexHtml = indexHtml.replaceFirst("</body>", "  <script>"
                    + "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]"
                    + "||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();"
                    + "a=s.createElement(o),m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;"
                    + "m.parentNode.insertBefore(a,m)})(window,document,'script',"
                    + "'//www.google-analytics.com/analytics.js','ga');"
                    + "ga('create', '" + googleAnalyticsTrackingId + "', 'auto');"
                    + "</script>\n</body>");
        }
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpServices.preventCaching(response);
        response.headers().set(Names.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(Names.CONTENT_LENGTH, indexHtml.length());
        // X-UA-Compatible must be set via header (as opposed to via meta tag)
        // see https://github.com/h5bp/html5-boilerplate/blob/master/doc/html.md#x-ua-compatible
        response.headers().set("X-UA-Compatible", "IE=edge");
        response.setContent(ChannelBuffers.copiedBuffer(indexHtml, Charsets.UTF_8));
        return response;
    }
}
