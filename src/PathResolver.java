import java.io.File;
import java.io.IOException;

public class PathResolver {

    public static File resolve(File rootDir, String requestPath) {
        if (rootDir == null || requestPath == null) {
            return null;
        }

        if (!requestPath.startsWith("/")) {
            return null;
        }

        try {
            File rootCanonical = rootDir.getCanonicalFile();
            String relative = requestPath.substring(1);
            File target = new File(rootCanonical, relative).getCanonicalFile();

            String rootPath = rootCanonical.getPath();
            String targetPath = target.getPath();

            if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
                return null;
            }

            return target;
        } catch (IOException e) {
            return null;
        }
    }
}
