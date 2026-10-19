package com.travel.modules.guidebook.generation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.chat.dto.IntentDto;

/** Step 1：意图完整性校验。缺关键字段属于业务错误，直接终止。 */
@Order(1)
@Component
public class IntentValidationStep implements GenerationStep {

    @Override
    public String name() {
        return "intent_validation";
    }

    @Override
    public int progress() {
        return 5;
    }

    @Override
    public boolean critical() {
        return true;
    }

    @Override
    public void execute(GenerationContext ctx) {
        IntentDto intent = ctx.getIntent();
        if (intent == null || !StringUtils.hasText(intent.getDestination())) {
            throw new BizException(ResultCode.UNPROCESSABLE, "目的地缺失，无法生成攻略");
        }
        if (intent.getDateWindow() == null
                || !StringUtils.hasText(intent.getDateWindow().getStart())
                || !StringUtils.hasText(intent.getDateWindow().getEnd())) {
            throw new BizException(ResultCode.UNPROCESSABLE, "出行日期缺失，无法生成攻略");
        }
        if (intent.getParty() == null || intent.getParty().getAdults() == null
                || intent.getParty().getAdults() < 1) {
            throw new BizException(ResultCode.UNPROCESSABLE, "出行人数缺失，无法生成攻略");
        }
        if (intent.getBudget() == null || intent.getBudget().getAmount() == null) {
            throw new BizException(ResultCode.UNPROCESSABLE, "预算缺失，无法生成攻略");
        }
    }
}
