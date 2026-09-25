package com.wen.codesandbox;
import java.io.*;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.wen.codesandbox.model.*;
import com.wen.codesandbox.utils.ProcessUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandBox implements CodeSandBox {

    private final String GLOBAL_CODE_PATH = "tempCode";

    private final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String userCode = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInput();
        String userParentCodePath = null;
        ExecuteCodeResponse  executeCodeResponse = null;

        try {
            //1. 新建目录 每个用户的每个代码都存放在一个文件夹中 把用户的代码保存在文件夹里的文件中

            //1.1 获取当前用户在执行这段代码所在的文件夹名
            //这里是 D:\code\code-sandbox
            String userPath = System.getProperty("user.dir");
            //这个目录专门用于存放用户的代码
            String globalCodePath = userPath + File.separator + GLOBAL_CODE_PATH;

            //若当前文件夹不存在则进行创建
            if(!FileUtil.exist(globalCodePath)){
                FileUtil.mkdir(globalCodePath);
            }

            userParentCodePath = globalCodePath + File.separator + UUID.randomUUID();
            String userCodePath = userParentCodePath +File.separator + GLOBAL_JAVA_CLASS_NAME;

            //1.2 将用户代码写到文件里
            File file = FileUtil.writeString(userCode, userCodePath, StandardCharsets.UTF_8);

            //2. 编译用户写的代码(java文件) 得到.class字节码文件
            //2.1 获取编译的指令
//            String compileCmd = String.format("javac -encoding utf-8 %s",userCodePath);
            String compileCmd = String.format("javac -encoding utf-8 -J-Dfile.encoding=UTF-8 %s", userCodePath);
            //3. 执行指令并获取对应的执行信息
            ExecuteMessage executeMessage = ProcessUtil.executeAndGetMessage(compileCmd,"代码编译");
            System.out.println(executeMessage);
            if(executeMessage.getExitValue()!=0){
                executeCodeResponse = new ExecuteCodeResponse();
                String message = "编译错误 "+executeMessage.getErrorMessage();
                executeCodeResponse.setMessage(message);
                executeCodeResponse.setStatus(ExecuteEnum.CODE_COMPILE_ERROR.getValue());
                return executeCodeResponse;
            }

            List<ExecuteMessage> executeMessageList = new ArrayList<>();
            //4. 执行用户代码
            for (String args : inputList) {
                //给堆内存设置上限 最大256MB 防止单个用户占用过多资源
                String runCodeCmd = String.format("java -Xmx256M -Dfile.encoding=UTF-8 -cp %s Main %s", userParentCodePath,args);
                executeMessage = ProcessUtil.executeAndGetMessage(runCodeCmd,"代码运行");
                executeMessageList.add(executeMessage);
                System.out.println(executeMessage);
            }


            List<String> outPutList = new ArrayList<>();
            executeCodeResponse = new ExecuteCodeResponse();
            long maxTime = 0L;
            boolean hasError = false;
            //5. 收集整理输出的结果
            for(ExecuteMessage executeMessage1 : executeMessageList){
                if(StrUtil.isNotBlank(executeMessage1.getErrorMessage())){
                    executeCodeResponse.setMessage(executeMessage1.getErrorMessage());
                    //用户的代码在执行时出现错误
                    executeCodeResponse.setStatus(ExecuteEnum.CODE_EXECUTE_ERROR.getValue());
                    //执行出错了
                    hasError=true;
                }
                if(executeMessage1.getExecuteTime()!=null){
                    //取这几次运行耗时的最大值
                    maxTime = Math.max(maxTime,executeMessage1.getExecuteTime());
                }
                String successOut = executeMessage1.getSuccessMessage();
                outPutList.add(successOut != null ? successOut.trim() : "");
            }
            if(!hasError){
                //执行成功
                executeCodeResponse.setStatus(ExecuteEnum.SUCCESS.getValue());
            }
            //设置输出信息
            executeCodeResponse.setOutput(outPutList);
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setTime(maxTime);
            executeCodeResponse.setJudgeInfo(judgeInfo);
        } catch (Exception e) {
            return getErrorResponse(e);
        } finally {
            //6. 文件清理
            if(userParentCodePath!=null && FileUtil.exist(userParentCodePath)){
                boolean del = FileUtil.del(userParentCodePath);
                System.out.println("删除"+(del ? "成功":"失败"));
            }
        }

        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutput(new ArrayList<>());
        executeCodeResponse.setMessage("沙箱系统异常: " + e.getMessage());
        executeCodeResponse.setStatus(ExecuteEnum.CODE_SANDBOX_ERROR.getValue());
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    public static void main(String[] args) {
        ExecuteCodeRequest  executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInput(Arrays.asList("1 2","2 4"));
        executeCodeRequest.setLanguage("java");

        //从Resource目录下的TestCode1中获取代码
        String testCode = ResourceUtil.readStr("testCode/TestCode1.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(testCode);

        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }

}
