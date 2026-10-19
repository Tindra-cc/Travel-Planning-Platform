package com.travel.modules.guidebook.generation;

/**
 * 生成流水线步骤。实现类用 @Order 声明顺序；非关键步骤抛异常时记录降级原因后继续。
 */
public interface GenerationStep {

    String name();

    /** 步骤完成后的总进度（0–100）。 */
    int progress();

    default boolean critical() {
        return false;
    }

    void execute(GenerationContext ctx);
}
