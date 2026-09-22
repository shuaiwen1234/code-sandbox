package com.wen.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 判题配置
 */
public class JudgeInfo {
    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 程序执行耗时(ms)
     */
    private Long time;

    /**
     * 程序占用内存大小(KB)
     */
    private Long memory;

    /**
     * 程序占用栈空间大小(KB)
     */
    private Long stackSize;

    private static final long serialVersionUID = 1L;
}
