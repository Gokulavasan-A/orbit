package com.enterprise.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsValidInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "abc-123_X.y");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, capture(seen));

        assertThat(seen.get()).isEqualTo("abc-123_X.y");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("abc-123_X.y");
    }

    @Test
    void generatesIdWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), response, capture(seen));

        assertThat(seen.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(seen.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "line\nbreak", "semi;colon", "<script>"})
    void replacesInvalidInboundId(String inbound) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, inbound);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, capture(seen));

        assertThat(seen.get()).isNotEqualTo(inbound).matches("^[A-Za-z0-9._-]{1,64}$");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(seen.get());
    }

    @Test
    void rejectsOverlongInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String tooLong = "a".repeat(65);
        request.addHeader(CorrelationId.HEADER, tooLong);
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capture(seen));

        assertThat(seen.get()).isNotEqualTo(tooLong);
    }

    @Test
    void removesIdFromMdcAfterwards() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> { });

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    private static FilterChain capture(AtomicReference<String> seen) {
        return (req, res) -> seen.set(CorrelationId.current());
    }
}
