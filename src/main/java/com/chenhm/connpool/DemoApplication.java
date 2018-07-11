package com.chenhm.connpool;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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


    @ConditionalOnProperty("http.url")
    @Bean
    public ApplicationRunner curl(@Value("${http.url}") String url) {
        return args -> {
            long start = System.currentTimeMillis();
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            Queue<Long[]> list = new ConcurrentLinkedQueue();
            for (int i = 0; i < loop; i++) {
                executor.submit(() -> {
                    long s = System.currentTimeMillis();
                    int status = sendGet(url);
                    list.add(new Long[]{s, System.currentTimeMillis() - s});
                    log.info(status + "," + (System.currentTimeMillis() - s) + "," + formatDate(s));
                });
            }
            executor.shutdown();
            executor.awaitTermination(300, TimeUnit.SECONDS);
            jfreechart(list, "REST concurrency:"+ concurrency + " loop:"+loop);
            System.out.println("finished in " + (System.currentTimeMillis() - start));
        };
    }

    static void jfreechart(Queue<Long[]> list, String legend) {
        TimeSeries s1 = new TimeSeries(legend);
        for (Long[] i : list) {
            Millisecond period = new Millisecond(new Date(i[0]));
            Number old = s1.getValue(period);
            if (old == null || old.doubleValue() < i[1].doubleValue()) {
                s1.addOrUpdate(period, i[1].doubleValue());
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(s1);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Latency",  // title
                "Time",             // x-axis label
                "Latency (ms)",   // y-axis label
                dataset,            // data
                true,               // create legend?
                true,               // generate tooltips?
                false               // generate URLs?
        );

        chart.setBackgroundPaint(Color.white);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);

        XYItemRenderer r = plot.getRenderer();
        if (r instanceof XYLineAndShapeRenderer) {
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
            renderer.setDefaultShapesVisible(true);
            renderer.setDefaultShapesFilled(true);
        }

        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("mm:ss.SSS"));
        BufferedImage img = chart.createBufferedImage(1200, 600, TYPE_INT_RGB, null);

        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd-HHmmss");
        try {
            ImageIO.write(img, "jpg", new FileOutputStream("latency-" + sdf.format(new Date()) + ".jpg"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static int sendGet(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");

            int responseCode = con.getResponseCode();
            return responseCode;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return -1;
        }
    }

    @ConditionalOnExpression("'${http.url:true}' == 'true'")
    @Bean
    public ApplicationRunner test(final TestRepository repository) {
        return args -> {
            long start = System.currentTimeMillis();
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            Queue<Long[]> list = new ConcurrentLinkedQueue();
            for (int i = 0; i < loop; i++) {
                executor.submit(() -> {
                    long s = System.currentTimeMillis();
                    Test test = repository.findOne("1234");
                    list.add(new Long[]{s, System.currentTimeMillis() - s});
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
            jfreechart(list, "DB concurrency:"+ concurrency + " loop:"+loop);
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
