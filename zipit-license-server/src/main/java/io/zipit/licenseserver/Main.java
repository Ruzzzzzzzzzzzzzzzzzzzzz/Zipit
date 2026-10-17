package io.zipit.licenseserver;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Starts the local Zipit license server and admin UI. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String host = "127.0.0.1";
        String data = "./license-data";
        String adminToken = System.getenv("ZIPIT_LICENSE_TOKEN") == null
                ? "" : System.getenv("ZIPIT_LICENSE_TOKEN");
        String keystore = "";
        String storepass = "";
        String publicUrl = System.getenv("ZIPIT_PUBLIC_URL") == null
                ? "" : System.getenv("ZIPIT_PUBLIC_URL").trim();
        int autoRenewDays = 0;
        int renewExtensionDays = 365;
        boolean open = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--port".equals(arg) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--host".equals(arg) && i + 1 < args.length) {
                host = args[++i];
            } else if ("--data".equals(arg) && i + 1 < args.length) {
                data = args[++i];
            } else if ("--admin-token".equals(arg) && i + 1 < args.length) {
                adminToken = args[++i];
            } else if ("--keystore".equals(arg) && i + 1 < args.length) {
                keystore = args[++i];
            } else if ("--storepass".equals(arg) && i + 1 < args.length) {
                storepass = args[++i];
            } else if ("--public-url".equals(arg) && i + 1 < args.length) {
                publicUrl = args[++i].trim();
            } else if ("--auto-renew-days".equals(arg) && i + 1 < args.length) {
                autoRenewDays = Integer.parseInt(args[++i]);
            } else if ("--renew-extension-days".equals(arg) && i + 1 < args.length) {
                renewExtensionDays = Integer.parseInt(args[++i]);
            } else if ("--open".equals(arg)) {
                open = true;
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                usage();
                return;
            } else {
                System.err.println("Unknown argument: " + arg);
                usage();
                return;
            }
        }

        Path dataDir = Paths.get(data).toAbsolutePath();
        LicenseStore store = new LicenseStore(dataDir);
        if (autoRenewDays > 0) {
            int renewed = store.autoRenew(autoRenewDays, renewExtensionDays);
            System.out.println("  Auto-renewed " + renewed + " license(s) expiring within "
                    + autoRenewDays + " days.");
        }
        LicenseServer server = new LicenseServer(host, port, store, adminToken,
                keystore, storepass, publicUrl);
        int bound = server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        String url = server.publicUrl() + "/";
        String clientUrl = server.publicUrl();
        System.out.println();
        System.out.println("  ZIPIT LICENSE SERVER");
        System.out.println("  --------------------");
        System.out.println("  Admin UI  : " + url);
        System.out.println("  Client URL: " + clientUrl);
        System.out.println("  Data dir  : " + dataDir);
        System.out.println("  Public key: " + dataDir.resolve("public.pem"));
        System.out.println("  Bind      : " + host + ":" + bound);
        System.out.println("  Admin auth: " + (adminToken.isEmpty() ? "disabled" : "X-Admin-Token required"));
        System.out.println("  Stop with Ctrl+C.");
        System.out.println();
        System.out.println("  Client configuration (give clients your address):");
        System.out.println("    license:");
        System.out.println("      enabled: true");
        System.out.println("      file: \"license.yml\"");
        System.out.println("      publicKey: \"" + dataDir.resolve("public.pem") + "\"");
        System.out.println("      server: \"" + clientUrl + "\"");
        System.out.println("      require: true");
        System.out.println();
        System.out.println("  Or: --license license.yml --license-key public.pem --license-server " + clientUrl);
        System.out.println("  Or: ZIPIT_LICENSE_SERVER=" + clientUrl);
        if (publicUrl.trim().isEmpty() && ("0.0.0.0".equals(host) || "::".equals(host))) {
            System.out.println();
            System.out.println("  [WARN] Bound to " + host + " but --public-url is not set.");
            System.out.println("         Clients cannot reach 127.0.0.1; restart with");
            System.out.println("         --public-url https://your-domain (or ZIPIT_PUBLIC_URL).");
        }
        System.out.println();

        if (open) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception e) {
                System.out.println("  (Unable to open a browser: " + e.getMessage() + ")");
            }
        }
        Thread.currentThread().join();
    }

    private static void usage() {
        System.out.println("Zipit License Server");
        System.out.println("  --port <n>     HTTP port (default 8080, 0 = ephemeral)");
        System.out.println("  --host <addr>  bind address (default 127.0.0.1)");
        System.out.println("  --data <dir>   data directory (default ./license-data)");
        System.out.println("  --admin-token <token>  require X-Admin-Token on admin endpoints");
        System.out.println("  --keystore <jks>       enable HTTPS with a JKS keystore");
        System.out.println("  --storepass <pass>     keystore password");
        System.out.println("  --public-url <url>     public base URL shown to clients (behind a proxy)");
        System.out.println("  --auto-renew-days <n>  renew active licenses expiring within n days");
        System.out.println("  --renew-extension-days <n>  extension applied by auto-renew (default 365)");
        System.out.println("  --open         open the admin page in a browser");
        System.out.println("  Env ZIPIT_LICENSE_TOKEN provides the admin token as well.");
        System.out.println("  Env ZIPIT_PUBLIC_URL provides the public base URL as well.");
    }
}
