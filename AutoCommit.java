import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动提交工具类，用于自动拉取远程更新、处理冲突文件并提交本地更改。
 */
public class AutoCommit {

    // 自动提交的消息
    private static final String COMMIT_MESSAGE = "from auto-commit program by @TMomster";

    // 冲突文件的后缀标识
    private static final String CONFLICT_SUFFIX = "u";

    /**
     * 主方法，执行自动提交流程。
     */
    public static void main(String[] args) {
        try {
            mOut("* 自动提交程序 *");

            // 检查本地是否有更改
            boolean hasLocalChanges = hasLocalChanges();
            if (!hasLocalChanges) {
                mOut("本地无更改，仅检查远程");
                if (!isLocalUpToDate()) {
                    mOut("检测到远程更新，正在拉取");
                    executeGitCommand("pull");
                    mOut("远程更新已拉取");
                } else {
                    mOut("本地版本已是最新");
                }
                return; // 本地无更改，直接退出
            }

            // 本地有更改，继续执行现有逻辑
            mOut("检查到本地更改");

            // 检查是否为最新版本
            if (!isLocalUpToDate()) {
                mOut("检测到远程更新，正保存本地更改");
                executeGitCommand("stash", "push", "--include-untracked");

                mOut("本地更改保存完毕，拉取远程更新");
                executeGitCommand("pull");

                mOut("已更新，正恢复本地更改");
                executeGitCommand("stash", "pop");
            }

            // 处理冲突
            List<String> conflictedFiles = getConflictedFiles();
            if (!conflictedFiles.isEmpty()) {
                mOut("检查到更改冲突，正在处理");
                for (String file : conflictedFiles) {
                    renameConflictedFile(file);
                }
                // 添加重命名后的文件
                executeGitCommand("add", "-A");
            }

            // 添加所有修改到暂存区
            mOut("正添加所有更改");
            executeGitCommand("add", "-A");

            // 提交并推送
            mOut("所有更改已经添加，正提交推送");
            executeGitCommand("commit", "-m", COMMIT_MESSAGE);
            executeGitCommand("push");

            mOut("所有更改已经推送完毕");
        } catch (Exception e) {
            System.err.println("{ * 执行 Git 操作途中发生错误 * }" + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void mOut(String x) {
        System.out.println("\n[ " + x + "] \n");
    }

    /**
     * 检查本地是否有未提交的更改。
     *
     * @return 如果有未提交的更改返回 true，否则返回 false。
     * @throws IOException            如果执行 git 命令时发生 I/O 错误
     * @throws InterruptedException    如果线程被中断
     */
    private static boolean hasLocalChanges() throws IOException, InterruptedException {
        String statusOutput = executeGitCommand("status", "--porcelain");
        return !statusOutput.trim().isEmpty();
    }

    /**
     * 检查本地是否是最新的，通过比较本地和远程分支的哈希值。
     *
     * @return 如果本地是最新的返回 true，否则返回 false。
     * @throws IOException            如果执行 git 命令时发生 I/O 错误
     * @throws InterruptedException    如果线程被中断
     */
    private static boolean isLocalUpToDate() throws IOException, InterruptedException {
        executeGitCommand("fetch");
        String localHash = executeGitCommand("rev-parse", "@");
        String remoteHash = executeGitCommand("rev-parse", "@{u}");
        return localHash.equals(remoteHash);
    }

    /**
     * 获取所有冲突的文件列表。
     *
     * @return 包含冲突文件路径的列表
     * @throws IOException            如果执行 git 命令时发生 I/O 错误
     * @throws InterruptedException    如果线程被中断
     */
    private static List<String> getConflictedFiles() throws IOException, InterruptedException {
        List<String> conflictedFiles = new ArrayList<>();
        String statusOutput = executeGitCommand("diff", "--name-only", "--diff-filter=U");
        for (String file : statusOutput.split("\n")) {
            if (!file.trim().isEmpty()) {
                conflictedFiles.add(file.trim());
            }
        }
        return conflictedFiles;
    }

    /**
     * 重命名冲突文件，避免覆盖。
     *
     * @param originalPath 冲突文件的原始路径
     */
    private static void renameConflictedFile(String originalPath) {
        File originalFile = new File(originalPath);
        if (!originalFile.exists()) return;

        String baseName = originalFile.getName();
        String newName;
        String extension = "";
        int dotIndex = baseName.lastIndexOf('.');

        // 处理文件扩展名
        if (dotIndex > 0) {
            extension = baseName.substring(dotIndex);
            baseName = baseName.substring(0, dotIndex);
        }

        int version = 1;
        Pattern pattern = Pattern.compile(Pattern.quote(baseName) + "_" + CONFLICT_SUFFIX + "(\\d+)" + Pattern.quote(extension));
        File parentDir = originalFile.getParentFile();

        // 查找已存在的冲突文件版本号
        if (parentDir != null) {
            for (File f : parentDir.listFiles()) {
                Matcher matcher = pattern.matcher(f.getName());
                if (matcher.find()) {
                    int foundVersion = Integer.parseInt(matcher.group(1));
                    if (foundVersion >= version) {
                        version = foundVersion + 1;
                    }
                }
            }
        }

        // 生成新文件名并重命名
        newName = baseName + "_" + CONFLICT_SUFFIX + version + extension;
        File newFile = new File(originalFile.getParent(), newName);

        if (originalFile.renameTo(newFile)) {
            System.out.println("重命名冲突文件: " + originalPath + " -> " + newName);
        } else {
            System.err.println("无法重命名文件: " + originalPath);
        }
    }

    /**
     * 执行 git 命令，并返回命令输出。
     *
     * @param command git 命令及其参数
     * @return 命令输出结果
     * @throws IOException            如果执行 git 命令时发生 I/O 错误
     * @throws InterruptedException    如果线程被中断
     */
    private static String executeGitCommand(String... command) throws IOException, InterruptedException {
        String[] fullCommand = new String[command.length + 1];
        fullCommand[0] = "git";
        System.arraycopy(command, 0, fullCommand, 1, command.length);

        Process process = new ProcessBuilder(fullCommand).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Git命令执行失败: " + String.join(" ", fullCommand) +
                    "\n退出码: " + exitCode +
                    "\n输出: " + output);
        }
        return output.toString().trim();
    }
}