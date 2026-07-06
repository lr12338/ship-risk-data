import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DateParseTest {
    public static void main(String[] args) throws Exception {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ExecutorService ex = Executors.newFixedThreadPool(16);
        for (int t = 0; t < 16; t++) {
            ex.submit(() -> {
                for (int i = 0; i < 10000; i++) {
                    try {
                        long ts = 1732929223000L + i * 997L;
                        sdf.parse("2025-11-30 12:00:00");
                        String s = sdf.format(new Date(ts));
                        if (s.contains("-31 ") || s.contains("-11-31") || s.contains("-02-30") || s.contains("-02-31")) {
                            System.out.println("BAD: " + s);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(120, TimeUnit.SECONDS);
        System.out.println("done");
    }
}
