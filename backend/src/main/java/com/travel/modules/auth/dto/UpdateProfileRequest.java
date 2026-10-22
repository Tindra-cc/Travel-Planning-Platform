package com.travel.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称最多 20 个字符")
    private String nickname;

    /** 预置 emoji 或 http(s) 图片地址；留空表示恢复昵称首字头像。 */
    @Size(max = 512, message = "头像地址过长")
    private String avatar;
}
