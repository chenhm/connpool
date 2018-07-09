package com.chenhm.connpool;

import java.io.FileWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Value("${loop}")
    int loop;

    @Value("${concurrency}")
    int concurrency;

    @Value("${dump.interval}")
    long dumpInterval = 0;

    @Bean
    public ApplicationRunner test(final TestRepository repository) {
        return args -> {
            long start = System.currentTimeMillis();
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            for (int i = 0; i < loop; i++) {
                executor.submit(() -> {
                    long s = System.currentTimeMillis();
                    Test test = repository.findOne("1234");
                    log.info(test.getTest() + "," + (System.currentTimeMillis() - s) + "," + formatDate(s));
                });
            }
            executor.shutdown();

            if (dumpInterval > 0) {
                Thread thread = new Thread(() -> {
                    ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd-HHmmss.SSS");
                    for (int i = 1; ; i++) {
                        try (FileWriter fw = new FileWriter("thread" + sdf.format(new Date()) + ".txt")) {
                            for (ThreadInfo ti : threadMxBean.dumpAllThreads(true, true)) {
                                fw.write(dump(ti));
                            }
                        } catch (Exception e) {
                            log.debug(e.getMessage(), e);
                        }
                        try {
                            Thread.sleep(dumpInterval);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
                thread.setDaemon(true);
                thread.start();
            }

            executor.awaitTermination(300, TimeUnit.SECONDS);
            System.out.println("finished in " + (System.currentTimeMillis() - start));
        };
    }

    static String formatDate(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(new Date(time));
    }

    static public String dump(ThreadInfo info) {

        StringBuilder sb = new StringBuilder("\"" + info.getThreadName() + "\""
                + " Id=" + info.getThreadId() + " " + info.getThreadState());
        if (info.getLockName() != null) {
            sb.append(" on " + info.getLockName());
        }
        if (info.getLockOwnerName() != null) {
            sb.append(" owned by \"" + info.getLockOwnerName() + "\" Id="
                    + info.getLockOwnerId());
        }
        if (info.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (info.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        int i = 0;
        for (; i < info.getStackTrace().length; i++) {
            StackTraceElement ste = info.getStackTrace()[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && info.getLockInfo() != null) {
                Thread.State ts = info.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + info.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + info.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + info.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : info.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
        }
        if (i < info.getStackTrace().length) {
            sb.append("\t...");
            sb.append('\n');
        }

        LockInfo[] locks = info.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = " + locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- " + li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    @Bean
    @ConfigurationProperties("c3p0.datasource")
    @ConditionalOnProperty("c3p0.datasource.jdbcUrl")
    public DataSource dataSource() {
        return new ComboPooledDataSource();
    }
}
