package com.stockmanagement.market;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FR-004: 종목명·코드/티커 검색, 최대 20건, 상장폐지 제외. */
class SecuritySearchIT extends IntegrationTestBase {

    @BeforeEach
    void seed() {
        Fixtures f = new Fixtures(jdbc);
        f.samsung();
        f.security("KOSPI", "005935", "삼성전자우", "Samsung Electronics Pref");
        f.apple();
        f.security("NASDAQ", "OLDCO", "올드코", "Old Co", "DELISTED");
    }

    @Test
    void findsByKoreanNamePartially() throws Exception {
        mockMvc.perform(get("/securities/search").param("q", "삼성").with(owner()).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(xpath("count(//li[@role='option'])").number(2.0))
                .andExpect(content().string(containsString("005930")));
    }

    @Test
    void findsByTickerPrefix() throws Exception {
        mockMvc.perform(get("/securities/search").param("q", "AAP").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AAPL")))
                .andExpect(content().string(containsString("NASDAQ")));
    }

    @Test
    void filtersByMarket() throws Exception {
        mockMvc.perform(get("/securities/search")
                        .param("q", "삼")
                        .param("market", "US")
                        .with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("005930"))));
    }

    @Test
    void excludesDelisted() throws Exception {
        mockMvc.perform(get("/securities/search").param("q", "OLD").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("OLDCO"))));
    }

    @Test
    void limitsToTwentyResults() throws Exception {
        Fixtures f = new Fixtures(jdbc);
        for (int i = 0; i < 25; i++) {
            f.security("KOSDAQ", String.format("1%05d", i), "테스트종목" + i, "Test " + i);
        }
        mockMvc.perform(get("/securities/search").param("q", "테스트종목").with(owner()))
                .andExpect(status().isOk())
                .andExpect(xpath("count(//li[@role='option'])").number(20.0));
    }

    @Test
    void rejectsEmptyQuery() throws Exception {
        mockMvc.perform(get("/securities/search").param("q", "").with(owner()).header("HX-Request", "true"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void rejectsQueryLongerThanForty() throws Exception {
        mockMvc.perform(get("/securities/search").param("q", "a".repeat(41)).with(owner()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void requiresAuthentication() throws Exception {
        createOwner();
        mockMvc.perform(get("/securities/search").param("q", "삼성").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("HX-Redirect", "/login"));
    }
}
