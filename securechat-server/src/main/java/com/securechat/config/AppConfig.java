package com.securechat.config;

import io.github.cdimascio.dotenv.Dotenv;

public final class AppConfig {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private AppConfig() {}

    public static String  dbUrl()       { return must("SC_DB_URL"); }
    public static String  dbUser()      { return must("SC_DB_USER"); }
    public static String  dbPassword()  { return must("SC_DB_PASSWORD"); }
    public static int     port()        { return Integer.parseInt(env("SC_PORT", "8080")); }

    public static boolean mailEnabled() { return Boolean.parseBoolean(env("SC_MAIL_ENABLED", "false")); }
    public static String  mailHost()    { return env("SC_MAIL_HOST", "smtp.gmail.com"); }
    public static int     mailPort()    { return Integer.parseInt(env("SC_MAIL_PORT", "587")); }
    public static String  mailUser()    { return getVal("SC_MAIL_USER"); }         // may be null
    public static String  mailPassword(){ return getVal("SC_MAIL_PASSWORD"); }     // may be null
    public static String  mailFrom() {
        String f = getVal("SC_MAIL_FROM");
        if (f != null && !f.isBlank()) return f;
        String u = mailUser();
        return u == null ? "" : u;
    }

    private static String must(String k) {
        String v = getVal(k);
        if (v == null || v.isBlank()) throw new IllegalStateException("Missing env variable: " + k);
        return v;
    }
    private static String env(String k, String def) {
        String v = getVal(k);
        return (v == null || v.isBlank()) ? def : v;
    }
    private static String getVal(String k) {
        String v = dotenv.get(k);
        if (v == null || v.isBlank()) v = System.getenv(k);
        return v;
    }

    public static String identityKeyPath() { return env("SC_IDENTITY_KEY_PATH", "./server_identity.key"); }
}