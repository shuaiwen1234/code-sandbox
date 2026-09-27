package com.wen.codesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DockerClientBuilder;

import java.util.List;

public class DockerDemo {
    public static void main(String[] args){
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
//        PingCmd pingCmd = dockerClient.pingCmd();
//        pingCmd.exec();

        ListImagesCmd listImagesCmd = dockerClient.listImagesCmd();
        List<Image> exec = listImagesCmd.exec();
        System.out.println(exec);



    }
}
