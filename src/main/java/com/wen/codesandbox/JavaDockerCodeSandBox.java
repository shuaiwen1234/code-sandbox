package com.wen.codesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.wen.codesandbox.model.*;
import com.wen.codesandbox.utils.ProcessUtil;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandBox implements CodeSandBox {

    private final String GLOBAL_CODE_PATH = "tempCode";

    private final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private final DockerClient dockerClient = DockerClientBuilder.getInstance().build();

    private final String image = "eclipse-temurin:8-jdk-alpine";

    //代码的最大运行时间
    private final Long MAX_CODE_RUN_TIME = 10L;


    @PostConstruct
    private void init() {
            //4.1 拉取镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("拉取镜像");
                    super.onNext(item);
                }
            };
        try {
            pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String userCode = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInput();
        String userParentCodePath = null;
        ExecuteCodeResponse  executeCodeResponse = new ExecuteCodeResponse();
        String containerId = null;
        final Long[] maxMemory = {0L};

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
            String compileCmd = String.format("javac --release 8 -encoding utf-8 -J-Dfile.encoding=UTF-8 %s", userCodePath);
            //3. 执行指令并获取对应的执行信息
            ExecuteMessage executeMessage = ProcessUtil.executeAndGetMessage(compileCmd,"代码编译");
            System.out.println(executeMessage);
            if(executeMessage.getExitValue()!=0){
                String message = "编译错误 "+executeMessage.getErrorMessage();
                executeCodeResponse.setMessage(message);
                executeCodeResponse.setStatus(ExecuteEnum.CODE_COMPILE_ERROR.getValue());
                return executeCodeResponse;
            }

            //4. 执行用户代码
            //4.0 创建容器 上传编译好的用户代码文件
            //4.2.1 将宿主机文件与容器内文件绑定(将宿主机的文件放到容器内的指定目录)
            HostConfig hostConfig = new HostConfig();
            hostConfig.setBinds(new Bind(userParentCodePath,new Volume("/tempCode")));
            //设置能使用的最大内存为256MB
            hostConfig.withMemory(256L*1024L*1024L);
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image)
                    .withName("java-container"+UUID.randomUUID())  //指定容器名字
                    .withAttachStdin(true)  //允许你的 Java 程序向 Docker 容器发送数据/指令（相当于在命令行向容器敲键盘输入）
                    .withAttachStdout(true)  //允许你的 Java 程序获取容器打印的正常日志/输出
                    .withAttachStderr(true)  //允许你的 Java 程序获取容器抛出的报错信息
                    .withCmd("tail", "-f", "/dev/null")  //通过监听一个永远不会有新内容的特殊文件来实现 让创建的容器一直存活(不加这个的话 Java 创建并启动容器后，容器会瞬间执行完毕并关闭)
                    .withHostConfig(hostConfig);
            //创建容器
            CreateContainerResponse createContainerResponse = containerCmd.exec();
            containerId = createContainerResponse.getId();

            //4.2.2 启动容器
            dockerClient.startContainerCmd(containerId).exec();
            List<ExecuteMessage> executeMessageList = new ArrayList<>();
            for (String inputStr : inputList) {
                //执行代码获取到的信息
                StopWatch stopWatch = new StopWatch();
                ExecuteMessage runExecuteMessage = new ExecuteMessage();
                StringBuilder successSB = new  StringBuilder();
                StringBuilder errSB = new  StringBuilder();
                String[] inputArray = StrUtil.isBlank(inputStr) ? new String[0] : inputStr.split(" ");
                //docker exec java-container26473327-5fc8-4e19-95bc-0c4a9a8070cf java -cp /tempCode Main 1 5
                String[] cmdArray = ArrayUtil.append(new String[]{"java","-cp","/tempCode","Main"},inputArray);
                //4.3 让容器执行命令 也就是运行java代码
                //4.3.1  创建要执行的命令
                ExecCreateCmdResponse createCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(cmdArray) // 让容器执行的命令
                        .withAttachStderr(true)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .exec();
                System.out.println("创建执行命令: "+createCmdResponse.toString().toString());

                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        if (frame.getStreamType().equals(StreamType.STDERR)) {
                            errSB.append(new String(frame.getPayload()));
                            System.out.println("输出错误信息: " + new String(frame.getPayload()));
                        } else {
                            successSB.append(new String(frame.getPayload()));
                            System.out.println("输出信息: " + new String(frame.getPayload()));
                        }
                        super.onNext(frame);
                    }
                };
                //4.3.2 获取(监控)程序占用的内存
                // withNoStream(true) 表示只向 Docker 索要一次当前的 Stats 数据，拿到后 Docker 会自动断开连接
                CountDownLatch countDownLatch = new CountDownLatch(1);
                ResultCallback<Statistics> statsResultCallBack = dockerClient.statsCmd(containerId).withNoStream(true).exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics object) {
                        if (object != null && object.getMemoryStats() != null) {
                            MemoryStatsConfig memoryStats = object.getMemoryStats();

                            // 判空处理：优先取 maxUsage，取不到则取 usage，防止 NPE
                            Long maxUsage = memoryStats.getMaxUsage();
                            Long usage = memoryStats.getUsage();

                            Long currentMem = 0L;
                            if (maxUsage != null) {
                                currentMem = maxUsage;
                            } else if (usage != null) {
                                currentMem = usage;
                            }

                            maxMemory[0] = Math.max(maxMemory[0], currentMem);
                            System.out.println("占用的内存为: " + currentMem + " bytes");
                        }
                    }

                    @Override
                    public void onStart(Closeable closeable) {

                    }

                    //一般来说 onError和onComplete只会调用一个
                    @Override
                    public void onError(Throwable throwable) {
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        countDownLatch.countDown();
                    }

                    @Override
                    public void close() throws IOException {

                    }
                });

                //4.3.3 让容器去执行命令
                stopWatch.start();
                boolean isFinished = dockerClient.execStartCmd(createCmdResponse.getId()).exec(execStartResultCallback).awaitCompletion(MAX_CODE_RUN_TIME, TimeUnit.SECONDS);
                stopWatch.stop();
                //等待监控内存 最多等待一秒
                countDownLatch.await(1, TimeUnit.SECONDS);
                //statsResultCallBack.close();
                if (!isFinished) {
                    runExecuteMessage.setErrorMessage("执行超时，最大执行时长为 10s");
                } else {
                    runExecuteMessage.setSuccessMessage(successSB.length() > 0 ? successSB.toString().trim() : "");
                    runExecuteMessage.setErrorMessage(errSB.length() > 0 ? errSB.toString().trim() : "");
                }
                runExecuteMessage.setExecuteTime(stopWatch.getLastTaskTimeMillis());
                runExecuteMessage.setMaxMemory(maxMemory[0]);
                executeMessageList.add(runExecuteMessage);
            }


            //5. 收集整理输出的结果
            boolean hasError = false;
            Long maxTime = 0L;
            List<String> outPutList = new ArrayList<>();
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
            judgeInfo.setMemory(maxMemory[0]);
            executeCodeResponse.setJudgeInfo(judgeInfo);
        } catch (Exception e) {
            return getErrorResponse(e);
        } finally {
            //6. 资源清理
            //6.1 文件清理
            if(userParentCodePath!=null && FileUtil.exist(userParentCodePath)){
                boolean del = FileUtil.del(userParentCodePath);
                System.out.println("删除"+(del ? "成功":"失败"));
            }

            //6.2 容器清理
            if(containerId!=null){
                try {
                    // withForce(true) 会强制停止并删除容器（相当于 docker rm -f），更适合沙箱短生命周期容器
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                    System.out.println("容器清理成功，containerId: " + containerId);
                } catch (Exception e) {
                    System.err.println("容器清理失败: " + e.getMessage());
                }
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

        JavaDockerCodeSandBox javaDockerCodeSandBox = new JavaDockerCodeSandBox();
        javaDockerCodeSandBox.init();
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }


}
