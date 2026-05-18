package com.appholaworld.termuxapi;

public interface ITermuxService {
    void createTermuxTask(String executable, String[] arguments, String stdin, String workingDirectory);
}
