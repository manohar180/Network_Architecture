public class HeaderTable {
    private static final String[] INDEXED_NAMES = {
        "content-type",
        "content-length",
        "connection",
        "server",
        "status",
        "cache-control",
        "date",
        "content-encoding",
        "accept",
        "host"
    };

    public static int getIndex(String name) {
        if (name == null) {
            return -1;
        }
        String lower = name.trim().toLowerCase();
        for (int i = 0; i < INDEXED_NAMES.length; i++) {
            if (INDEXED_NAMES[i].equals(lower)) {
                return i + 1;
            }
        }
        return -1;
    }

    public static String getName(int index) {
        if (index >= 1 && index <= INDEXED_NAMES.length) {
            return INDEXED_NAMES[index - 1];
        }
        return null;
    }
}
