package com.gole.api.design.domain.model;

/**
 * 사이트가 지금 그릴 마스코트. 공개 응답 모양이며 조치자·사유를 담지 않는다. (mascot-assets R4)
 *
 * <p>기본 제공 에셋은 그림을 웹이 가지므로 이미지 경로가 없다. 업로드 에셋은 같은 원점의 공개 경로와 치수를 준다.
 */
public record ActiveMascot(
        long revision,
        String id,
        Kind kind,
        String name,
        String imageUrl,
        String darkImageUrl,
        Integer width,
        Integer height) {

    public enum Kind {
        PRESET,
        UPLOAD
    }

    public static ActiveMascot preset(long revision, String key, String name) {
        return new ActiveMascot(revision, key, Kind.PRESET, name, null, null, null, null);
    }

    public static ActiveMascot upload(long revision, MascotAsset asset) {
        return new ActiveMascot(
                revision,
                asset.id(),
                Kind.UPLOAD,
                asset.name(),
                asset.imageUrl(),
                asset.darkImageUrl(),
                asset.width(),
                asset.height());
    }
}
