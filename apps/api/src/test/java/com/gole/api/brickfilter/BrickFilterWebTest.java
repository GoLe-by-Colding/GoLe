package com.gole.api.brickfilter;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.brickfilter.adapter.in.web.BrickFilterController;
import com.gole.api.brickfilter.application.service.BrickFilterService;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BrickFilterWebTest {
    @Test
    void privateReadsAndUploadRequireServerResolvedIdentity() throws Exception {
        var service = mock(BrickFilterService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BrickFilterController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();
        for (String path : new String[] {"/quota", "/jobs", "/jobs/id", "/jobs/id/result"})
            mvc.perform(get("/api/v1/brick-filter" + path).header("X-User-Id", "victim"))
                    .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/brick-filter/jobs")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", new byte[] {1}))
                        .param("mode", "MINIFIGURE")
                        .header("Idempotency-Key", "id")
                        .header("X-User-Id", "victim"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedResultUsesOnlySessionOwnerAndIsNotCacheable() throws Exception {
        var service = mock(BrickFilterService.class);
        when(service.result("owner", "job")).thenReturn(new byte[] {1, 2});
        var mvc = MockMvcBuilders.standaloneSetup(new BrickFilterController(service))
                .build();
        mvc.perform(get("/api/v1/brick-filter/jobs/job/result")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "owner")
                        .param("userId", "victim"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("image/png"));
        verify(service).result("owner", "job");
    }
}
