package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.Listing;

public interface GetListingUseCase {

    Listing getById(String listingId);
}
