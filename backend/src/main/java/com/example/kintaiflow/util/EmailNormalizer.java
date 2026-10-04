package com.example.kintaiflow.util;

import java.util.Locale;

/** メールアドレスの正規化（ONL-001 / ONL-014）。前後の空白を除去し小文字にする。 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
