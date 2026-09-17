package com.gole.api.listing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.domain.model.InterestTagCatalog;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class InterestTagParityTest {

    @Test
    void listingTagsMatchAccountCatalogByKeyAndLabel() {
        Set<TagValue> listingTags = Arrays.stream(InterestTag.values())
                .map(tag -> new TagValue(tag.key(), tag.label()))
                .collect(Collectors.toSet());
        Set<TagValue> accountTags = InterestTagCatalog.tags().stream()
                .map(tag -> new TagValue(tag.key(), tag.label()))
                .collect(Collectors.toSet());

        assertThat(listingTags).isEqualTo(accountTags);
    }

    private record TagValue(String key, String label) {}
}
