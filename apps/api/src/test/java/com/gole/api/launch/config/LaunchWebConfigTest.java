package com.gole.api.launch.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.launch.adapter.in.web.LaunchGateInterceptor;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchStage;
import com.gole.api.review.adapter.in.web.ReviewController;
import com.gole.api.review.application.port.in.GetSellerReviewsUseCase;
import com.gole.api.review.application.port.in.ReplyToReviewUseCase;
import com.gole.api.review.application.port.in.WriteReviewUseCase;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class LaunchWebConfigTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void closedReviewsBlockSellerReplyBeforeControllerMappingRuns() throws Exception {
        ReplyToReviewUseCase replies = context.getBean(ReplyToReviewUseCase.class);

        mvc.perform(post("/api/v1/reviews/review-1/reply")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"거래 고맙습니다\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAUNCH_REVIEWS_CLOSED"));

        verifyNoInteractions(replies);
    }

    @Test
    void matrixParameterCannotBypassClosedReviewReply() throws Exception {
        ReplyToReviewUseCase replies = context.getBean(ReplyToReviewUseCase.class);

        mvc.perform(post("/api/v1/reviews/review-1/reply;x=1")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"거래 고맙습니다\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAUNCH_REVIEWS_CLOSED"));

        verifyNoInteractions(replies);
    }

    @Test
    void matrixParameterCannotBypassClosedOrderCreation() throws Exception {
        GetLaunchConfigUseCase launchConfig = context.getBean(GetLaunchConfigUseCase.class);
        Runnable orderMutation = context.getBean("orderMutation", Runnable.class);
        when(launchConfig.current()).thenReturn(new LaunchConfig(LaunchStage.BROWSE_ONLY, Map.of(), null, null));

        mvc.perform(post("/api/v1/orders;x=1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAUNCH_DIRECT_TRADE_ONLY"));

        verifyNoInteractions(orderMutation);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({
        ReviewController.class,
        OrderMutationProbeController.class,
        LaunchWebConfig.class,
        LaunchGateInterceptor.class,
        GlobalExceptionHandler.class
    })
    static class TestWebConfig {

        @Bean
        GetLaunchConfigUseCase launchConfig() {
            GetLaunchConfigUseCase launchConfig = mock(GetLaunchConfigUseCase.class);
            when(launchConfig.current())
                    .thenReturn(
                            new LaunchConfig(LaunchStage.TRADING, Map.of(LaunchFeature.REVIEWS, false), null, null));
            return launchConfig;
        }

        @Bean
        WriteReviewUseCase writeReviewUseCase() {
            return mock(WriteReviewUseCase.class);
        }

        @Bean
        GetSellerReviewsUseCase getSellerReviewsUseCase() {
            return mock(GetSellerReviewsUseCase.class);
        }

        @Bean
        ReplyToReviewUseCase replyToReviewUseCase() {
            return mock(ReplyToReviewUseCase.class);
        }

        @Bean
        OperationalEventPublisher operationalEventPublisher() {
            return mock(OperationalEventPublisher.class);
        }

        @Bean
        Runnable orderMutation() {
            return mock(Runnable.class);
        }
    }

    @RestController
    static class OrderMutationProbeController {

        private final Runnable mutation;

        OrderMutationProbeController(Runnable mutation) {
            this.mutation = mutation;
        }

        @PostMapping("/api/v1/orders")
        void place() {
            mutation.run();
        }
    }
}
