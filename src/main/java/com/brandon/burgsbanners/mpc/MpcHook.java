package com.brandon.burgsbanners.mpc;

import java.util.List;

public interface MpcHook {
    boolean currencyExists(String currencyCode);
    List<String> suggestCurrencyCodes(String prefix);
}
