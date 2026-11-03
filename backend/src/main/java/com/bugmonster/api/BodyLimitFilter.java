package com.bugmonster.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BodyLimitFilter extends OncePerRequestFilter {
    public static final int MAX_BYTES = 256 * 1024;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")
                || !("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()))) {
            chain.doFilter(request, response);
            return;
        }
        byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            response.setStatus(413);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"BODY_TOO_LARGE\",\"message\":\"请求体不能超过 256 KiB\"}");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return in.read(); }
                    @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                    @Override public boolean isFinished() { return in.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Synchronous JSON requests only");
                    }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }
}
