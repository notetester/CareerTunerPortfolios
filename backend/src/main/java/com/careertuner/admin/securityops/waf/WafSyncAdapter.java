package com.careertuner.admin.securityops.waf;

import com.careertuner.admin.securityops.waf.WafSyncModels.WafProvider;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncResult;
import com.careertuner.admin.securityops.waf.WafSyncModels.WafSyncTarget;

/** WAF/CDN 동기화 어댑터. 프로바이더 종류에 따라 라우팅된다(@Order 우선순위). */
public interface WafSyncAdapter {
    boolean supports(WafProvider provider, WafSyncTarget target);

    WafSyncResult sync(WafProvider provider, WafSyncTarget target);
}
