package com.wen.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExecuteCodeRequest {

    /**
     * 测试用例
     */
    private List<String> input;

    /**
     * 使用的语言
     */
    private String language;

    /**
     * 编写的代码
     */
    private String code;
}
