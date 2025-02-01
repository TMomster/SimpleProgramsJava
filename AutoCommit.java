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
                    mOut("正在拉取远程更新");
                    executeGitCommand("pull");
                    outputRemoteChanges(); // 输出远程更新的文件变化
                    mOut("本地更新完毕");
                } else {
                    mOut("本地已是最新");
                }
                return; // 本地无更改，直接退出
            }

            // 本地有更改，输出文件更改信息
            mOut("本地有更改，继续提交推送");
            outputLocalChanges();

            // 检查是否为最新版本
            if (!isLocalUpToDate()) {
                mOut("远程有更新，正在保存本地更改");
                executeGitCommand("stash", "push", "--include-untracked");

                mOut("本地更改保存完毕，开始更新");
                executeGitCommand("pull");
                outputRemoteChanges(); // 输出远程更新的文件变化

                mOut("更新完毕，正在恢复本地更改");
                executeGitCommand("stash", "pop");
            }

            // 处理冲突
            List<String> conflictedFiles = getConflictedFiles();
            if (!conflictedFiles.isEmpty()) {
                mOut("检查到更改冲突");
                for (String file : conflictedFiles) {
                    renameConflictedFile(file, conflictedFiles);
                }
                // 添加重命名后的文件
                executeGitCommand("add", "-A");
            }

            // 添加所有修改到暂存区
            mOut("添加所有修改到暂存区");
            executeGitCommand("add", "-A");

            // 提交并推送
            mOut("所有更改已经添加，正在提交推送");
            executeGitCommand("commit", "-m", COMMIT_MESSAGE);
            executeGitCommand("push");

            // 输出提交信息
            mOut("所有更改已经推送完毕");
            outputCommitInfo();

        } catch (Exception e) {
            System.err.println(" * 执行 Git 操作途中发生错误 *" + e.getMessage());
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
     * 输出本地更改的文件信息，包括新增和删除的文件数量，以及每个文件的更新行数。
     *
     * @throws IOException            如果执行 git 命令时发生 I/O 错误
     * @throws InterruptedException    如果线程被中断
     */
    private static void outputLocalChanges() throws IOException, InterruptedException {
        String diffOutput = executeGitCommand("diff", "--stat");
        String[] lines = diffOutput.split("\n");

        int addedFiles = 0;
        int deletedFiles = 0;
        List<String> changedFiles = new ArrayList<>();

        for (String line : lines) {
            if (line.contains("|")) {
                String[] parts = line.split("\\|");
                String file = parts[0].trim();
                String changes = parts[1].trim();

                if (changes.contains("+") && changes.contains("-")) {
                    changedFiles.add(file);
                } else if (changes.contains("+")) {
                    addedFiles++;
                } else if (changes.contains("-")) {
                    deletedFiles++;
                }
            }
        }

        // 输出新增和删除的文件数量
        mOut("(+) " + addedFiles + " 文件，(-) " + deletedFiles + " 文件");

        // 输出每个文件的更新行数，最多显示前三个文件
        int maxFilesToShow = 3;
        for (int i = 0; i < changedFiles.size() && i < maxFilesToShow; i++) {
            String file = changedFiles.get(i);
            String fileDiff = executeGitCommand("diff", "--stat", file);
            System.out.println(fileDiff.trim());
        }

        // 如果更新的文件数量大于3，输出省略信息
        if (changedFiles.size() > maxFilesToShow) {
            System.out.println("...（以及其余 " + (changedFiles.size() - maxFilesToShow) + " 个文件）");
        }
    }

    /**
     * 输出远程更新的文件变化信息。
     *
     * @throws IOException            如果执行 git 命令时发生 I/O 错误
     * @throws InterruptedException    如果线程被中断
     */
    private static void outputRemoteChanges() throws IOException, InterruptedException {
        String diffOutput = executeGitCommand("diff", "--stat", "@{u}");
        String[] lines = diffOutput.split("\n");

        int changedFilesCount = 0;
        List<String> changedFiles = new ArrayList<>();

        for (String line : lines) {
            if (line.contains("|")) {
                changedFilesCount++;
                changedFiles.add(line.trim());
            }
        }

        // 输出远程更新的文件数量
        mOut("远程更新带来了 " + changedFilesCount + " 个文件的变化");

        // 输出每个文件的更新行数，最多显示前三个文件
        int maxFilesToShow = 3;
        for (int i = 0; i < changedFiles.size() && i < maxFilesToShow; i++) {
            System.out.println(changedFiles.get(i));
        }

        // 如果更新的文件数量大于3，输出省略信息
        if (changedFiles.size() > maxFilesToShow) {
            System.out.println("...（以及其余 " + (changedFiles.size() - maxFilesToShow) + " 个文件）");
        }
    }

    /**
     * 输出提交信息，包括远程仓库名、提交用户名和提交信息。
     *
     * @throws IOException            如果执行 git 命令时发生 I/O 错误
     * @throws InterruptedException    如果线程被中断
     */
    private static void outputCommitInfo() throws IOException, InterruptedException {
        String remoteUrl = executeGitCommand("config", "--get", "remote.origin.url");
        String userName = executeGitCommand("config", "--get", "user.name");

        System.out.println("远程仓库名: " + remoteUrl.trim());
        System.out.println("提交用户名: " + userName.trim());
        System.out.println("提交信息: " + COMMIT_MESSAGE);
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
    private static void renameConflictedFile(String originalPath, List<String> conflictedFiles) {
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
            System.out.println("处理的冲突文件: " + originalPath + " -> " + newName);
        } else {
            System.err.println("无法重命名文件: " + originalPath);
        }

        mOut("已处理 " + conflictedFiles.size() + " 个冲突");
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
            throw new RuntimeException(" * 执行Git命令失败 * " + String.join(" ", fullCommand) +
                    "\n退出码: " + exitCode +
                    "\n输出: " + output);
        }
        return output.toString().trim();
    }
}