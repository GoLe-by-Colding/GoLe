package com.gole.api.media.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.media.application.port.in.AcquireMediaUploadQuotaUseCase;
import com.gole.api.media.application.port.in.LoadImageUseCase;
import com.gole.api.media.application.port.in.UploadImageUseCase;
import com.gole.api.media.application.port.in.UploadImageUseCase.UploadImageCommand;
import com.gole.api.media.domain.model.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MediaControllerTest {

    private UploadImageUseCase uploads;
    private AcquireMediaUploadQuotaUseCase quota;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        uploads = mock(UploadImageUseCase.class);
        quota = mock(AcquireMediaUploadQuotaUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new MediaController(uploads, mock(LoadImageUseCase.class), quota))
                .build();
    }

    @Test
    void batchChargesAuthenticatedAccountByActualImageCount() throws Exception {
        when(uploads.upload(org.mockito.ArgumentMatchers.any(UploadImageCommand.class)))
                .thenAnswer(invocation -> {
                    UploadImageCommand command = invocation.getArgument(0);
                    return new StoredImage(
                            "images/example.png",
                            "/api/v1/media/images/example.png",
                            command.contentType(),
                            command.content().length);
                });
        MockMultipartFile first =
                new MockMultipartFile("files", "first.png", "image/png", new byte[] {(byte) 0x89, 0x50});
        MockMultipartFile second =
                new MockMultipartFile("files", "second.png", "image/png", new byte[] {(byte) 0x89, 0x50});

        mvc.perform(multipart("/api/v1/media/images/batch")
                        .file(first)
                        .file(second)
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "account-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].key").value("images/example.png"));

        verify(quota).acquire("account-1", 2);
    }
}
