package xyz.n7mn.dev.nanamiauthsystem.MojangAPI;

public class UUIDtoProfile {

    /*
{
  "id" : "c6f2f6d35a7d45cda16a52e4d4e2ceb2",
  "name" : "7mi_chan",
  "properties" : [ {
    "name" : "textures",
    "value" : "ewogICJ0aW1lc3RhbXAiIDogMTY3NjYwODU0ODU4OSwKICAicHJvZmlsZUlkIiA6ICJjNmYyZjZkMzVhN2Q0NWNkYTE2YTUyZTRkNGUyY2ViMiIsCiAgInByb2ZpbGVOYW1lIiA6ICI3bWlfY2hhbiIsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80M2Q1YmMxMDFhMGJlZGUwNTNiZWQ3Y2E4ZjkyZjY2ODMyZjkyZTk2MDMzMWQyMGZmNTI5Y2IxNmRjNjY2OWQ3IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="
  } ]
}
     */

    private String id;
    private String name;
    private Properties[] properties;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Properties[] getProperties() {
        return properties;
    }
}

class Properties {

    private String name;
    private String value;

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
