package com.gole.api.design.application.port.out;

import com.gole.api.design.domain.model.MascotAsset;
import com.gole.api.design.domain.model.MascotSelection;
import java.util.List;
import java.util.Optional;

public interface MascotRepositoryPort {

    MascotSelection currentSelection();

    List<MascotSelection> history(long before);

    /** 같은 리비전이 이미 있으면 충돌로 거부한다(동시 적용 펜스). */
    void appendSelection(MascotSelection selection);

    Optional<MascotAsset> findAsset(String id);

    List<MascotAsset> assets();

    long countAssets();

    void saveAsset(MascotAsset asset);

    /** @return 지웠으면 true */
    boolean deleteAsset(String id);
}
