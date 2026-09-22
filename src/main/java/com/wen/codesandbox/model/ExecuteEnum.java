package com.wen.codesandbox.model;

import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 这个枚举类用来记录代码沙箱在执行时可能会出现的错误(包含正常执行)
 */
@SuppressWarnings("all")
public enum ExecuteEnum {

    CODE_COMPILE_ERROR("代码编译错误",1),
    CODE_SANDBOX_ERROR("代码沙箱出现异常",2),
    CODE_EXECUTE_ERROR("代码在执行时发生错误",3),
    SUCCESS("代码执行成功",4);



    public final String descMsg;
    public final int value;

    ExecuteEnum(String descMsg,int value) {
        this.descMsg = descMsg;
        this.value = value;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static ExecuteEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (ExecuteEnum anEnum : ExecuteEnum.values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }

    public String getDescMsg() {
        return descMsg;
    }
}
