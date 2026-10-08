public class Header {
    private final String name;
    private final String value;

    public Header(String name, String value) {
        this.name = name != null ? name : "";
        this.value = value != null ? value : "";
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return name + ": " + value;
    }
}
