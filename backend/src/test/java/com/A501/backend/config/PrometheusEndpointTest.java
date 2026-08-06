package com.A501.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
// 테스트에서는 Boot가 메트릭 export를 기본 비활성화한다(MetricsContextCustomizer).
// 이 어노테이션이 없으면 /actuator/prometheus 엔드포인트 자체가 뜨지 않는다.
@AutoConfigureMetrics
class PrometheusEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheusEndpointServesScrapeTextWithoutAuthentication() throws Exception {
        // Prometheus 스크레이퍼는 인증 헤더 없이 요청한다. 외부 차단은 nginx가
        // /actuator/prometheus를 라우팅하지 않는 것으로 보장한다.
        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("jvm_memory_used_bytes");
        assertThat(body).contains("hikaricp_connections");
    }

    @Test
    void httpRequestMetricsPublishHistogramBuckets() throws Exception {
        // Grafana의 p95 패널은 히스토그램 버킷으로 분위수를 계산한다 — summary만
        // 남는 회귀를 막는다.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("http_server_requests_seconds_bucket");
    }
}
