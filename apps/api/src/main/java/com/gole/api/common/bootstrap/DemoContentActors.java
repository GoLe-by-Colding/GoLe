package com.gole.api.common.bootstrap;

import java.util.List;

/** 데모 콘텐츠 시더들이 공유하는 비실사용 actor ID의 단일 목록. */
public final class DemoContentActors {

    public static final String SELLER_AURORA = "seller-aurora";
    public static final String SELLER_BRICKBANK = "seller-brickbank";
    public static final String SELLER_MINIFIG = "seller-minifig";

    public static final String USER_BUILDER = "user-builder";
    public static final String USER_COLLECTOR = "user-collector";
    public static final String USER_MOC = "user-moc";
    public static final String USER_NEWBIE = "user-newbie";

    public static final String BUYER_HYUN = "buyer-hyun";
    public static final String BUYER_JI = "buyer-ji";
    public static final String BUYER_JUN = "buyer-jun";
    public static final String BUYER_MINA = "buyer-mina";
    public static final String BUYER_SOO = "buyer-soo";
    public static final String BUYER_TAE = "buyer-tae";
    public static final String BUYER_WON = "buyer-won";

    public static final List<String> ALL_ACCOUNT_IDS = List.of(
            SELLER_AURORA,
            SELLER_BRICKBANK,
            SELLER_MINIFIG,
            USER_BUILDER,
            USER_COLLECTOR,
            USER_MOC,
            USER_NEWBIE,
            BUYER_HYUN,
            BUYER_JI,
            BUYER_JUN,
            BUYER_MINA,
            BUYER_SOO,
            BUYER_TAE,
            BUYER_WON);

    private DemoContentActors() {}
}
