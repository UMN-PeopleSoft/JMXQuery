package edu.umn.pssa.jmxquery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import java.util.ArrayList;
import java.util.Arrays;
import javax.management.MalformedObjectNameException;
import com.facebook.nailgun.NGContext;

/**
 *
 * JMXQuery is used for remote request of JMX attributes
 *
 * @author David Gildeh (www.outlyer.com)
 * @author Nate Werner (umn.edu)
 *
 */
public class JMXQuery {

    private JMXConnector connector;
    private final ArrayList<JMXMetric> metrics = new ArrayList<JMXMetric>();
    private NGContext ngContext;

    // Command Line Parameters
    String url = null;
    String username = null;
    String password = null;

    // implemented the NailGun Main instead of regular main.
    // Special Nailgun server entry point
    public static void nailMain(NGContext context) throws Exception  {
        JMXQuery query = new JMXQuery();
        query.ngContext = context;
        String[] args = context.getArgs();
        try {
           query.parse(args);
        } catch (ParseError pe) {
           context.out.println("{ \"error\": \"parm-parse-error\", \"message\":\"" + pe.getMessage() + "\"}");
           context.exit(2);
           return;
        }
        // Stripped out the JSON option, and only return JSON
        // Removed Help and JDK VM options
        
        // Initialise JMX Connection
        try {
            query.connector = new JMXConnector(query.url, query.username, query.password);
        } catch (IOException ioe) {
            context.out.println("{ \"error\": \"connection-error\", \"message\":\"" + ioe.getMessage() + "\"}");
            context.exit(2);
            return;
        }

        // Process Query
        try {
            ArrayList<JMXMetric> outputMetrics = query.connector.getMetrics(query.metrics);
            context.out.println("[");
            int count = 0;
            for (JMXMetric metric : outputMetrics) {
                metric.replaceTokens();
                if (count > 0) {
                    context.out.print(", \n" + metric.toJSON());
                } else {
                    count++;
                    context.out.print(metric.toJSON());
                }
            }
            System.out.println("]");
        } catch (IOException ioe) {
            context.out.println("{ \"error\": \"query-connection-error\", \"message\":\"" + ioe.getMessage() + "\"}");
            context.exit(2);
        } catch (MalformedObjectNameException me) {
            context.out.println("{ \"error\": \"bad-query\", \"message\":\"" + me.getMessage() + "\"}");
            context.exit(2);
        } catch (Exception e) {
            context.out.println("{ \"error\": \"general-exception\", \"message\":\"" + e.getMessage() + "\"}");
            context.exit(2);
        } finally
        {
            query.connector.disconnect();
        }
        return;
    }

    /**
     * Parse runtime argument commands
     *
     * @param args Command line arguments
     * @throws ParseError
     */
    private void parse(String[] args) throws ParseError {

        try {
            for (int i = 0; i < args.length; i++) {
                String option = args[i];
                if (option.equals("-url")) {
                    url = args[++i];
                } else if (option.equals("-username") || option.equals("-u")) {
                    username = args[++i];
                } else if (option.equals("-password") || option.equals("-p")) {
                    password = args[++i];
                } else if (option.equals("-query") || option.equals("-q")) {

                    // Parse query string to break up string in format:
                    // {mbean}/{attribute}/{key};
                    String[] query = args[++i].split(";");
                    for (String metricQuery : query) {
                        metrics.add(new JMXMetric(metricQuery));
                    }
                }
            }

            // Check that required parameters are given
            if (url == null && (metrics.size() > 1)) {
                ngContext.out.println("Required options not specified.");
                ngContext.exit(0);
            }
        } catch (Exception e) {
            throw new ParseError(e);
        }
    }

}
