import java.io.File;
import java.util.*;

public class Sequencer {

    /**
     * 主方法，程序入口。
     * 1. 获取当前目录下所有符合命名格式的文件。
     * 2. 提取文件中的数字序号并存储在Map中。
     * 3. 调整文件序号，确保从1开始。
     * 4. 处理重复的文件序号，让用户选择顺序。
     * 5. 确保文件序号连续。
     * 6. 重命名文件。
     */
    public static void main(String[] args) {
        File currentDir = new File("."); // 当前工作目录
        File[] files = getFilesWithNumberedNames(currentDir); // 获取符合命名格式的文件

        if (files.length == 0) {
            System.out.println("没有找到符合命名格式的文件。");
            return;
        }

        Map<Integer, List<File>> fileMap = extractFileNumbers(files); // 提取文件中的数字序号
        adjustFileNumbers(fileMap); // 调整文件序号，确保从1开始
        handleDuplicateNumbers(fileMap); // 处理重复的文件序号
        ensureContinuousKeys(fileMap); // 确保文件序号连续
        renameFiles(fileMap); // 重命名文件
    }

    /**
     * 获取指定目录下所有以数字开头的文件。
     * @param dir 指定的目录
     * @return 符合命名格式的文件数组
     */
    private static File[] getFilesWithNumberedNames(File dir) {
        return dir.listFiles((d, name) -> name.matches("\\d+\\..+")); // 匹配以数字开头的文件名
    }

    /**
     * 提取文件名中的数字序号，并将相同序号的文件归类到一起。
     * @param files 文件数组
     * @return 以数字序号为键，文件列表为值的Map
     */
    private static Map<Integer, List<File>> extractFileNumbers(File[] files) {
        Map<Integer, List<File>> fileMap = new HashMap<>();
        for (File file : files) {
            String fileName = file.getName();
            int dotIndex = fileName.indexOf('.'); // 获取文件扩展名的位置
            int number = Integer.parseInt(fileName.substring(0, dotIndex)); // 提取文件名中的数字部分
            fileMap.computeIfAbsent(number, k -> new ArrayList<>()).add(file); // 将文件添加到对应的数字键下
        }
        return fileMap;
    }

    /**
     * 调整文件序号，确保最小序号为1。
     * @param fileMap 文件序号映射表
     */
    private static void adjustFileNumbers(Map<Integer, List<File>> fileMap) {
        int minNumber = Collections.min(fileMap.keySet()); // 获取最小的序号
        if (minNumber != 1) {
            System.out.println("序号不是从1开始的，最小的序号是 " + minNumber + "，将其调整为1。");
            Map<Integer, List<File>> adjustedMap = new HashMap<>();
            for (Map.Entry<Integer, List<File>> entry : fileMap.entrySet()) {
                int newNumber = entry.getKey() - minNumber + 1; // 计算新的序号
                adjustedMap.put(newNumber, entry.getValue()); // 更新文件序号
            }
            fileMap.clear();
            fileMap.putAll(adjustedMap); // 替换原始映射表
        }
    }

    /**
     * 处理重复的文件序号，让用户选择文件的顺序。
     * @param fileMap 文件序号映射表
     */
    private static void handleDuplicateNumbers(Map<Integer, List<File>> fileMap) {
        List<Integer> duplicateNumbers = new ArrayList<>();
        for (Map.Entry<Integer, List<File>> entry : fileMap.entrySet()) {
            if (entry.getValue().size() > 1) { // 如果有重复的序号
                duplicateNumbers.add(entry.getKey());
            }
        }

        if (!duplicateNumbers.isEmpty()) {
            Scanner scanner = new Scanner(System.in);
            for (int number : duplicateNumbers) {
                List<File> duplicateFiles = fileMap.get(number); // 获取重复序号的文件列表
                System.out.println("发现重复序号 " + number + " 的文件:");
                for (int i = 0; i < duplicateFiles.size(); i++) {
                    System.out.println((i + 1) + ": " + duplicateFiles.get(i).getName()); // 显示文件列表
                }
                System.out.print("请输入这些文件的顺序（用空格分隔）: ");
                String[] order = scanner.nextLine().split(" "); // 用户输入文件顺序
                List<File> orderedFiles = new ArrayList<>();
                for (String index : order) {
                    int idx = Integer.parseInt(index) - 1;
                    orderedFiles.add(duplicateFiles.get(idx)); // 根据用户输入重新排序
                }
                fileMap.put(number, orderedFiles); // 更新文件列表
            }
        }
    }

    /**
     * 确保文件序号连续，从1开始递增。
     * @param fileMap 文件序号映射表
     */
    private static void ensureContinuousKeys(Map<Integer, List<File>> fileMap) {
        List<Integer> sortedKeys = new ArrayList<>(fileMap.keySet());
        Collections.sort(sortedKeys); // 对文件序号进行排序

        Map<Integer, List<File>> newMap = new LinkedHashMap<>();
        int newKey = 1;
        for (int oldKey : sortedKeys) {
            newMap.put(newKey, fileMap.get(oldKey)); // 重新分配连续的序号
            newKey++;
        }
        fileMap.clear();
        fileMap.putAll(newMap); // 替换原始映射表
    }

    /**
     * 重命名文件，按照新的序号顺序。
     * @param fileMap 文件序号映射表
     */
    private static void renameFiles(Map<Integer, List<File>> fileMap) {
        List<File> sortedFiles = new ArrayList<>();
        for (int i = 1; i <= fileMap.size(); i++) {
            sortedFiles.addAll(fileMap.get(i)); // 获取按序号排序后的文件列表
        }

        for (int i = 0; i < sortedFiles.size(); i++) {
            File file = sortedFiles.get(i);
            String newName = (i + 1) + file.getName().substring(file.getName().indexOf('.')); // 构造新文件名
            File newFile = new File(file.getParent(), newName); // 创建新的文件对象
            if (file.renameTo(newFile)) {
                System.out.println("重命名成功: " + file.getName() + " -> " + newName);
            } else {
                System.out.println("重命名失败: " + file.getName());
            }
        }
    }
}
