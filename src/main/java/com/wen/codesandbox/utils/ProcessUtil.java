package com.wen.codesandbox.utils;
import com.wen.codesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * @author a1472
 * 进程工具类
 */
public class ProcessUtil {

    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(32,62,3L, TimeUnit.MINUTES,new LinkedBlockingQueue<>(100), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());

    /**
     *
     * @param command 要执行的指令
     * @param operationName 操作的名称(如编译 运行)
     * @return 执行信息
     */
    public static ExecuteMessage executeAndGetMessage(String command,String operationName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        Process process;
        CountDownLatch countDownLatch = new CountDownLatch(2);
        int maxCodeExecuteTime = 10;
        //用于计算程序执行时间
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            //创建一个进程去执行指令
            process = Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            stopWatch.stop();
            throw new RuntimeException(e);
        }

        // 关键点1:在 waitFor() 之前,先启动两个线程分别读取标准输出和错误输出
        // 这样即使子进程输出内容很多、缓冲区被写满,读取线程也能及时把它消费掉
        // 不会出现"子进程等你读、你等子进程退出"的死锁
        StringBuilder successOutput = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try {
            POOL.execute(() -> {
                try {
                    successOutput.append(readStream(process.getInputStream()));
                } catch (Exception e) {
                    System.err.println("线程池执行任务时出现错误: " + e.getMessage());
                }finally {
                    countDownLatch.countDown();
                }
            });

            POOL.execute(() -> {
                try {
                    errorOutput.append(readStream(process.getErrorStream()));
                } catch (Exception e) {
                    System.err.println("线程池执行任务时出现错误: " + e.getMessage());
                }finally {
                    countDownLatch.countDown();
                }
            });
        } catch (RejectedExecutionException e) {
            //之前通过Runtime.getRuntime().exec(command) 创建了一个子进程去执行command
            //如果线程池无法创建新线程且队列也满了就无法进行后续操作 但是创建的子进程还存活
            // 强行销毁进程
            process.destroyForcibly();
            stopWatch.stop();
            throw new RuntimeException("系统繁忙，读取线程池已满", e);
        }


        int exitValue;
        try {
            // 这时候两个流已经在被并行读取消费,waitFor() 不会因为缓冲区写满而卡死
            // 1. 带超时的等待进程结束（防止代码死循环）
            boolean isFinished = process.waitFor(maxCodeExecuteTime, TimeUnit.SECONDS);

            if (!isFinished) {
                // 超时强制杀掉子进程
                process.destroyForcibly();
                executeMessage.setExitValue(-1);
                executeMessage.setErrorMessage("执行超时 (超过 " + maxCodeExecuteTime + " 秒)");
                stopWatch.stop();
                executeMessage.setExecuteTime(stopWatch.getLastTaskTimeMillis());
                return executeMessage;
            }
            // 必须等读取线程真正读完(读到流末尾/EOF),否则可能读到不完整的输出
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态,不要吞掉中断信号
            stopWatch.stop();
            throw new RuntimeException(e);
        }
        //程序正常执行时返回码一般为0
        exitValue = process.exitValue();

        executeMessage.setExitValue(exitValue);
        if (exitValue == 0) {
            System.out.println(operationName+"成功");
            executeMessage.setSuccessMessage(successOutput.toString());
        } else {
            System.out.println(operationName+"失败 错误码为: " + exitValue);
            // 异常退出时,通常两个流都想保留,方便定位问题
            executeMessage.setSuccessMessage(successOutput.toString());
            executeMessage.setErrorMessage(errorOutput.toString());
        }
        stopWatch.stop();
        executeMessage.setExecuteTime(stopWatch.getLastTaskTimeMillis());
        return executeMessage;
    }

    /**
     * 这个方法用来读取输入流里的数据
     * 关键点2:用 try-with-resources 读取流,读完(或异常)时自动关闭,不会有资源泄漏
     */
    private static String readStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        // 统一强制使用 UTF-8 读取流
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, length);
            }
            return sb.toString();
        }
    }

}