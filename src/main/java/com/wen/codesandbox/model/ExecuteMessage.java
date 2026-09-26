package com.wen.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteMessage {

    /**
     * 程序的退出码
     */
    private int exitValue;

    /**
     * 正常执行的信息
     */
    private String successMessage;

    /**
     * 异常执行的信息
     */
    private String errorMessage;

    /**
     * 执行时间
     */
    private Long executeTime;

    /**
     * 占用的最大内存
     */
    private Long maxMemory;
}
