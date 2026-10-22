import { Payment } from "@portone/react-native-sdk";
import type { PaymentRequest, PaymentResponse } from "@portone/browser-sdk/v2";
import { useMemo } from "react";
import { ActivityIndicator, Modal, Pressable, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import type { AppPaymentRequest, AppPaymentResult } from "@gole/core/lib";
import { themes } from "@/shared/theme";

const colors = themes.light;

/** 서비스 WebView와 분리한 PG 결제창. 서비스의 푸시·탭 스크립트를 주입하지 않는다. */
export function PaymentModal({
  request,
  onResult,
}: {
  readonly request: AppPaymentRequest;
  readonly onResult: (result: AppPaymentResult) => void;
}) {
  const payment = useMemo<PaymentRequest>(
    () => ({
      ...request.payment,
      appScheme: "gole://",
      locale: "KO_KR",
      windowType: { pc: "REDIRECTION", mobile: "REDIRECTION" },
    }),
    [request],
  );
  const close = () =>
    onResult({
      requestId: request.requestId,
      paymentId: request.payment.paymentId,
      code: "PAYMENT_CANCELLED",
      message: "결제창을 닫았습니다. 주문 상세에서 결제 상태를 확인해 주세요.",
    });
  const fail = () =>
    onResult({
      requestId: request.requestId,
      paymentId: request.payment.paymentId,
      code: "PAYMENT_WINDOW_FAILED",
      message: "결제창을 불러오지 못했습니다. 다시 결제하기 전에 주문 상태를 확인해 주세요.",
    });
  const complete = (response: PaymentResponse) => {
    // PG 오류에는 paymentId가 없을 수 있다. 성공일 때만 원래 주문번호와 반드시 일치해야 한다.
    const mismatch =
      response.code === undefined && response.paymentId !== request.payment.paymentId;
    onResult({
      requestId: request.requestId,
      paymentId: request.payment.paymentId,
      ...(mismatch
        ? {
            code: "PAYMENT_ID_MISMATCH",
            message: "결제 응답의 주문번호가 다릅니다. 주문 상태를 확인해 주세요.",
          }
        : response.code === undefined
          ? {}
          : {
              code: response.code.slice(0, 100),
              message: (
                response.message ??
                response.pgMessage ??
                "결제를 완료하지 못했습니다."
              ).slice(0, 500),
            }),
    });
  };
  return (
    <Modal visible presentationStyle="fullScreen" onRequestClose={close}>
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
        <View
          style={{
            flexDirection: "row",
            alignItems: "center",
            justifyContent: "space-between",
            paddingHorizontal: 20,
            borderBottomWidth: 1,
            borderColor: colors.border,
          }}
        >
          <Text
            accessibilityRole="header"
            style={{ color: colors.text, fontSize: 18, fontWeight: "600" }}
          >
            GoLe 결제
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="결제창 닫기"
            onPress={close}
            style={{ minHeight: 48, minWidth: 48, justifyContent: "center", alignItems: "center" }}
          >
            <Text style={{ color: colors.tint, fontSize: 16 }}>닫기</Text>
          </Pressable>
        </View>
        <Payment
          request={payment}
          onComplete={complete}
          onError={fail}
          style={{ flex: 1, backgroundColor: colors.background }}
          onHttpError={fail}
          onContentProcessDidTerminate={fail}
          onRenderProcessGone={fail}
          sharedCookiesEnabled={false}
          allowFileAccess={false}
          mixedContentMode="never"
          startInLoadingState
          renderLoading={() => (
            <ActivityIndicator
              accessibilityLabel="결제창 불러오는 중"
              color={colors.tint}
              style={{ position: "absolute", top: "50%", alignSelf: "center" }}
            />
          )}
        />
      </SafeAreaView>
    </Modal>
  );
}
