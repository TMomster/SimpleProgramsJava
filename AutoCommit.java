import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class AutoCommit {

    public static void main(String[] args) {
        try {
            System.out.println("+ --- --- AutoCommit --- --- +");

            // 检查分支是否为最新，如果不是，则先保存当前状态并拉取更新；
            // 如果当前分支有更改，在拉取更新后推送，否则结束程序。
            if (checkAndPullLatest()) pushLocalUpdates();

        } catch (IOException | InterruptedException e) {
            System.err.println("\n[ * 执行Git操作途中遇到了问题 * ]\n" + e.getMessage());
        }
    }

    private static void pushLocalUpdates() throws IOException, InterruptedException {
        executeCommand("git add .");
        System.out.println("\n[ 你的更改已经全部添加，正在提交 ]\n");

        executeCommand("git commit -m \"from AutoCommit @TMomster\"");
        System.out.println("\n[ 提交完毕，正在推送你的更改 ]\n");

        executeCommand("git push -u origin master");
        System.out.println("\n[ 所有更改已经成功提交。 ]\n");
    }

    private static boolean checkAndPullLatest() throws IOException, InterruptedException {
        // 检查是否有未提交的更改
        String statusOutput = executeCommandAndGetOutput("git status --porcelain");
        if (statusOutput.trim().isEmpty()) {
            System.out.println("\n[ 不存在更改，仅拉取更新 ]\n");
            // 拉取最新更新
            executeCommand("git pull origin master");
            System.out.println("\n[ 更新完毕 ]\n");
            return false;
        }

        // 保存当前更改
        executeCommand("git stash");
        System.out.println("\n[ 你的更改已经保存，正在检查更新 ]\n");

        // 拉取最新更新
        executeCommand("git pull origin master");
        System.out.println("\n[ 检查更新完毕，正在恢复你的更改 ]\n");

        // 恢复之前的更改
        try {
            executeCommand("git stash pop");
            System.out.println("\n[ 你的更改已经恢复 ]\n");
        } catch (Exception e) {
            // 检查是否存在合并冲突
            String conflictCheck = executeCommandAndGetOutput("git status --porcelain");
            if (conflictCheck.contains("UU")) {
                System.err.println("\n[ 存在合并冲突，处理冲突文件 ]\n");
                // 提取冲突文件
                String[] lines = conflictCheck.split("\n");
                for (String line : lines) {
                    if (line.startsWith("UU")) {
                        String fileName = line.substring(3);
                        handleConflictFile(fileName);
                    }
                }
                // 标记冲突已解决
                executeCommand("git add .");
                System.out.println("\n[ 冲突已解决，继续操作 ]\n");
            } else {
                System.err.println("\n[ 恢复更改失败，可能是没有 stash 条目 ]\n" + e.getMessage());
                System.exit(1);
            }
        }

        return true;
    }

    private static void handleConflictFile(String fileName) throws IOException {
        Path originalPath = Paths.get(fileName);
        String baseName = originalPath.getFileName().toString();
        String extension = "";
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex != -1) {
            extension = baseName.substring(dotIndex);
            baseName = baseName.substring(0, dotIndex);
        }

        int suffix = 1;
        Path newPath;
        do {
            String newFileName = baseName + "u" + suffix + extension;
            newPath = originalPath.resolveSibling(newFileName);
            suffix++;
        } while (Files.exists(newPath));

        Files.copy(originalPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("\n[ 冲突文件 " + fileName + " 已复制为 " + newPath + " ]\n");
    }

    private static void executeCommand(String command) throws IOException, InterruptedException {
        // 判断操作系统类型并选择合适的命令解释器
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder processBuilder;
        if (os.contains("win")) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
        }
        Process process = processBuilder.start();

        // 读取输出
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        // 等待进程完毕
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // 读取错误输出
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }
            throw new RuntimeException("\n执行失败，退出代码为：" + exitCode);
        }
    }

    private static String executeCommandAndGetOutput(String command) throws IOException, InterruptedException {
        // 判断操作系统类型并选择合适的命令解释器
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder processBuilder;
        if (os.contains("win")) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
        }
        Process process = processBuilder.start();

        // 读取输出
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        // 等待进程完毕
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // 读取错误输出
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }
            throw new RuntimeException("\n执行失败，退出代码为：" + exitCode);
        }

        return output.toString();
    }
}
